package com.operion.onboarding.readiness;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import com.operion.onboarding.readiness.GoLiveReport.Group;
import org.springframework.stereotype.Service;

/**
 * Final check before an organisation goes live: for each module that is switched on, runs the
 * checks the module contributes (configuration, data, access) and rolls them up. Modules the
 * platform owner switched off are skipped and named, so the school is never asked to set up
 * something it cannot see. A module with no contributed checks is reported as unchecked, not
 * as passing, so a gap in coverage is visible rather than silent.
 */
@Service
public class GoLiveService {

	static final String ORGANISATION = "ORGANISATION";

	private final List<ReadinessCheck> checks;
	private final ModuleAccessService moduleAccessService;

	public GoLiveService(List<ReadinessCheck> checks, ModuleAccessService moduleAccessService) {
		this.checks = checks;
		this.moduleAccessService = moduleAccessService;
	}

	public GoLiveReport report() {
		Set<AppModule> disabled = moduleAccessService.disabledModules(TenantContext.getOrganisationId());
		List<Group> groups = new ArrayList<>();
		groups.add(group(ORGANISATION, "Organisation", null));
		for (AppModule module : AppModule.values()) {
			if (!disabled.contains(module)) {
				groups.add(group(module.name(), module.label(), module));
			}
		}
		boolean ready = groups.stream().noneMatch(group -> group.level() == ReadinessLevel.FAIL);
		return new GoLiveReport(ready, groups, disabled.stream().sorted(Comparator.naturalOrder()).map(AppModule::label).toList());
	}

	private Group group(String code, String label, AppModule module) {
		List<ReadinessFinding> findings = checks.stream().filter(check -> check.module() == module)
				.flatMap(check -> check.check().stream()).toList();
		ReadinessLevel worst = findings.stream().map(ReadinessFinding::level).max(Comparator.naturalOrder()).orElse(ReadinessLevel.PASS);
		return new Group(code, label, worst, !findings.isEmpty(), findings);
	}

	/** Enabled-or-not, which modules have at least one contributed check - used by the coverage test. */
	public Set<AppModule> modulesWithChecks() {
		Set<AppModule> covered = java.util.EnumSet.noneOf(AppModule.class);
		checks.stream().map(ReadinessCheck::module).filter(java.util.Objects::nonNull).forEach(covered::add);
		return covered;
	}
}
