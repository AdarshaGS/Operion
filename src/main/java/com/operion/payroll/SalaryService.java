package com.operion.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.operion.audit.AuditLogService;
import com.operion.hr.StaffProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalaryService {

	/** One requested line of a new structure. */
	public record LineInput(SalaryComponent component, BigDecimal value) {
	}

	private final SalaryComponentRepository componentRepository;
	private final SalaryStructureRepository structureRepository;
	private final SalaryStructureLineRepository lineRepository;
	private final AuditLogService auditLogService;

	public SalaryService(SalaryComponentRepository componentRepository, SalaryStructureRepository structureRepository,
			SalaryStructureLineRepository lineRepository, AuditLogService auditLogService) {
		this.componentRepository = componentRepository;
		this.structureRepository = structureRepository;
		this.lineRepository = lineRepository;
		this.auditLogService = auditLogService;
	}

	public List<SalaryComponent> listComponents() {
		return componentRepository.findAllByOrderByNameAsc();
	}

	@Transactional
	public SalaryComponent createComponent(String name, ComponentType type, CalculationBasis basis) {
		if (name != null && componentRepository.existsByNameIgnoreCase(name.trim())) {
			throw new IllegalStateException("A salary component named " + name.trim() + " already exists");
		}
		SalaryComponent component = componentRepository.save(new SalaryComponent(name, type, basis));
		auditLogService.record("SalaryComponent", component.getId(), "CREATE", null, component.getName());
		return component;
	}

	/** Deactivating stops a component being used in new structures; existing ones keep it. */
	@Transactional
	public SalaryComponent changeComponentStatus(SalaryComponent component, SalaryComponentStatus status) {
		SalaryComponentStatus previous = component.getStatus();
		component.changeStatus(status);
		SalaryComponent saved = componentRepository.save(component);
		auditLogService.record("SalaryComponent", saved.getId(), "STATUS_CHANGE", previous, status);
		return saved;
	}

	public List<SalaryStructure> historyFor(Long staffProfileId) {
		return structureRepository.findByStaffProfileIdOrderByEffectiveFromDesc(staffProfileId);
	}

	/** The structure in force on the given date, if any. */
	public Optional<SalaryStructure> structureInForce(Long staffProfileId, LocalDate date) {
		return structureRepository
				.findFirstByStaffProfileIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(staffProfileId, date);
	}

	public List<SalaryStructureLine> linesOf(SalaryStructure structure) {
		return lineRepository.findBySalaryStructureId(structure.getId());
	}

	/**
	 * Adds a new structure version. It must take effect after the staff member's latest one -
	 * backdating would rewrite what past payroll was computed against.
	 */
	@Transactional
	public SalaryStructure createStructure(StaffProfile staff, LocalDate effectiveFrom, BigDecimal basicAmount,
			List<LineInput> lines) {
		Optional<SalaryStructure> latest = structureRepository
				.findFirstByStaffProfileIdOrderByEffectiveFromDesc(staff.getId());
		if (latest.isPresent() && !effectiveFrom.isAfter(latest.get().getEffectiveFrom())) {
			throw new IllegalStateException("A new salary structure must take effect after the current one ("
					+ latest.get().getEffectiveFrom() + ")");
		}
		Set<Long> seen = new HashSet<>();
		for (LineInput line : lines) {
			if (line.component().getStatus() != SalaryComponentStatus.ACTIVE) {
				throw new IllegalStateException("Component " + line.component().getName() + " is inactive");
			}
			if (!seen.add(line.component().getId())) {
				throw new IllegalStateException("Component " + line.component().getName() + " is listed twice");
			}
		}
		SalaryStructure structure = structureRepository.save(new SalaryStructure(staff, effectiveFrom, basicAmount));
		for (LineInput line : lines) {
			lineRepository.save(new SalaryStructureLine(structure, line.component(), line.value()));
		}
		auditLogService.record("SalaryStructure", structure.getId(), "CREATE", null,
				"staff " + staff.getId() + " from " + effectiveFrom);
		return structure;
	}
}
