package com.operion.student;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import org.springframework.stereotype.Component;

/**
 * The one rule behind "no academic writes for a student whose admission fee is unpaid". Called explicitly from
 * the service methods that record marks, report cards, answer sheets, homework reviews and rewards - not an
 * aspect, so each call site stays visible and can name its action in the error.
 *
 * <p>Blocks only when the organisation's mode is BLOCK, the FEES module is enabled, and some enrollment in the
 * call is PENDING. Lives in the student package and reads only student-package state (the stored status and
 * AdmissionSettings), so student, attendance, examination and learning never depend on finance. A batch is
 * all-or-nothing: one PENDING enrollment rejects the whole call.
 *
 * <p>The cheap check comes first: with no PENDING enrollment in the call (the overwhelming case) there is no
 * settings or module lookup at all. Otherwise each is read once per call, never per row.
 */
@Component
public class AdmissionFeeGate {

	private final AdmissionSettingsRepository admissionSettingsRepository;
	private final ModuleAccessService moduleAccessService;

	public AdmissionFeeGate(AdmissionSettingsRepository admissionSettingsRepository, ModuleAccessService moduleAccessService) {
		this.admissionSettingsRepository = admissionSettingsRepository;
		this.moduleAccessService = moduleAccessService;
	}

	public void requireAllowed(StudentEnrollment enrollment, String action) {
		requireAllowed(List.of(enrollment), action);
	}

	/** @param action what is being attempted, in the lower-case form used in the message, e.g. "enter marks". */
	public void requireAllowed(Collection<StudentEnrollment> enrollments, String action) {
		List<Long> pending = enrollments.stream()
				.filter(Objects::nonNull)
				.filter(enrollment -> enrollment.getFeeClearanceStatus() == FeeClearanceStatus.PENDING)
				.map(StudentEnrollment::getId)
				.toList();
		if (pending.isEmpty() || !blocking()) {
			return;
		}
		throw new AdmissionFeePendingException(action, pending);
	}

	private boolean blocking() {
		Long organisationId = TenantContext.getOrganisationId();
		AdmissionFeeClearanceMode mode = admissionSettingsRepository.findByOrganisationId(organisationId)
				.map(AdmissionSettings::getFeeClearanceMode)
				.orElse(AdmissionSettings.DEFAULT_FEE_CLEARANCE_MODE);
		return mode == AdmissionFeeClearanceMode.BLOCK && moduleAccessService.isEnabled(organisationId, AppModule.FEES);
	}
}
