package com.operion.onboarding.readiness;

import java.util.List;

import com.operion.authorization.RoleRepository;
import com.operion.modules.AppModule;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.stereotype.Component;

/** Organisation-wide checks that matter whatever modules are on. */
@Component
class CoreReadinessCheck implements ReadinessCheck {

	private final AcademicYearRepository academicYearRepository;
	private final RoleRepository roleRepository;

	CoreReadinessCheck(AcademicYearRepository academicYearRepository, RoleRepository roleRepository) {
		this.academicYearRepository = academicYearRepository;
		this.roleRepository = roleRepository;
	}

	@Override
	public AppModule module() {
		return null;
	}

	@Override
	public List<ReadinessFinding> check() {
		return List.of(
				ReadinessFinding.of(academicYearRepository.existsByCurrentTrue(), ReadinessStage.CONFIGURATION, ReadinessLevel.FAIL,
						"An academic year is active", "No academic year is active yet", "/settings/academic-years"),
				// The Owner role always exists; staff need at least one more role to be given anything less than everything.
				ReadinessFinding.of(roleRepository.count() > 1, ReadinessStage.ACCESS, ReadinessLevel.WARN,
						"Roles beyond the Owner exist", "Only the Owner role exists - staff would get either full access or none", "/settings/roles"));
	}
}
