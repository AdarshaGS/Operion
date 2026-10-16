package com.operion.organisation.rollover;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.audit.AuditLogService;
import com.operion.common.TenantContext;
import com.operion.modules.ModuleAccessService;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.AcademicYearStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Copy-forward: prepares a new academic year from an earlier one by running each enabled
 * module's {@link RolloverContributor}. Two phases over the same code - {@link #preview} runs
 * everything and then rolls the transaction back, so its counts are exactly what {@link #apply}
 * would do, including rows created by earlier steps in the same run. Apply is all-or-nothing
 * and safe to repeat, because contributors skip what the target year already holds.
 */
@Service
public class AcademicYearRolloverService {

	private static final BigDecimal MAX_FEE_CHANGE_PERCENT = new BigDecimal("500");

	private final AcademicYearRepository academicYearRepository;
	private final List<RolloverContributor> contributors;
	private final ModuleAccessService moduleAccessService;
	private final AuditLogService auditLogService;

	public AcademicYearRolloverService(AcademicYearRepository academicYearRepository, List<RolloverContributor> contributors,
			ModuleAccessService moduleAccessService, AuditLogService auditLogService) {
		this.academicYearRepository = academicYearRepository;
		this.contributors = contributors.stream().sorted(Comparator.comparingInt(RolloverContributor::order)).toList();
		this.moduleAccessService = moduleAccessService;
		this.auditLogService = auditLogService;
	}

	/** The steps this organisation can run: contributors whose module is enabled. */
	public List<RolloverContributor> availableSteps() {
		var disabled = moduleAccessService.disabledModules(TenantContext.getOrganisationId());
		return contributors.stream().filter(contributor -> !disabled.contains(contributor.module())).toList();
	}

	@Transactional
	public RolloverReport preview(Long targetYearId, RolloverRequest request) {
		RolloverReport report = run(targetYearId, request, true);
		TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
		return report;
	}

	@Transactional
	public RolloverReport apply(Long targetYearId, RolloverRequest request) {
		RolloverReport report = run(targetYearId, request, false);
		Map<String, Integer> copied = new LinkedHashMap<>();
		report.steps().forEach(step -> copied.put(step.key(), step.copiedTotal()));
		auditLogService.record("AcademicYear", targetYearId, "ROLLOVER", null,
				Map.of("sourceYear", report.sourceYear(), "copied", copied));
		return report;
	}

	private RolloverReport run(Long targetYearId, RolloverRequest request, boolean dryRun) {
		AcademicYear target = year(targetYearId, "Target");
		AcademicYear source = year(request.sourceYearId(), "Source");
		if (source.getId().equals(target.getId())) {
			throw new IllegalArgumentException("Choose a different year to copy from");
		}
		if (target.getStatus() == AcademicYearStatus.CLOSED) {
			throw new IllegalStateException("A closed academic year cannot be prepared");
		}
		RolloverOptions options = new RolloverOptions(request.feeIncreasePercent());
		if (options.feeIncreasePercent().compareTo(MAX_FEE_CHANGE_PERCENT) > 0 || options.feeIncreasePercent().compareTo(new BigDecimal("-100")) < 0) {
			throw new IllegalArgumentException("Fee change must be between -100% and 500%");
		}

		List<RolloverContributor> available = availableSteps();
		Set<String> wanted = request.steps() == null ? Set.of() : request.steps();
		Set<String> known = contributors.stream().map(RolloverContributor::key).collect(Collectors.toSet());
		for (String key : wanted) {
			if (!known.contains(key)) {
				throw new IllegalArgumentException("Unknown rollover step: " + key);
			}
		}
		List<RolloverStep> steps = new ArrayList<>();
		for (RolloverContributor contributor : available) {
			if (wanted.isEmpty() || wanted.contains(contributor.key())) {
				steps.add(contributor.run(source, target, options));
			}
		}
		List<String> unavailable = contributors.stream().filter(contributor -> !available.contains(contributor)).map(RolloverContributor::label).toList();
		return new RolloverReport(dryRun, source.getName(), target.getName(), steps, unavailable);
	}

	private AcademicYear year(Long id, String role) {
		if (id == null) {
			throw new IllegalArgumentException(role + " academic year is required");
		}
		return academicYearRepository.findById(id).orElseThrow(() -> new IllegalArgumentException(role + " academic year " + id + " not found"));
	}
}
