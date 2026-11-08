package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The gate's decision table in isolation: it blocks only for mode BLOCK + FEES module on + at least one PENDING
 * enrollment, a batch is all-or-nothing, and settings and the module flag are read at most once per call (and not
 * at all when nobody in the call is PENDING).
 */
class AdmissionFeeGateTest {

	private static final Long ORG = 7L;

	private AdmissionSettingsRepository settingsRepository;
	private ModuleAccessService moduleAccessService;
	private AdmissionFeeGate gate;

	@BeforeEach
	void setUp() {
		TenantContext.set(ORG, null);
		settingsRepository = mock(AdmissionSettingsRepository.class);
		moduleAccessService = mock(ModuleAccessService.class);
		gate = new AdmissionFeeGate(settingsRepository, moduleAccessService);
		when(moduleAccessService.isEnabled(ORG, AppModule.FEES)).thenReturn(true);
		mode(AdmissionFeeClearanceMode.BLOCK);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void mode(AdmissionFeeClearanceMode mode) {
		AdmissionSettings settings = new AdmissionSettings();
		settings.setFeeClearanceMode(mode);
		when(settingsRepository.findByOrganisationId(ORG)).thenReturn(Optional.of(settings));
	}

	private static StudentEnrollment enrollment(long id, FeeClearanceStatus status) {
		StudentEnrollment enrollment = mock(StudentEnrollment.class);
		when(enrollment.getId()).thenReturn(id);
		when(enrollment.getFeeClearanceStatus()).thenReturn(status);
		return enrollment;
	}

	@Test
	void blocksAPendingEnrollmentInBlockModeNamingTheActionAndTheEnrollment() {
		assertThatThrownBy(() -> gate.requireAllowed(enrollment(11, FeeClearanceStatus.PENDING), "enter marks"))
				.isInstanceOfSatisfying(AdmissionFeePendingException.class, e -> {
					assertThat(e.getEnrollmentIds()).containsExactly(11L);
					assertThat(e.getMessage()).contains("enter marks").contains("11");
				});
	}

	@Test
	void allowsEveryOtherStatusInBlockMode() {
		for (FeeClearanceStatus status : List.of(FeeClearanceStatus.NOT_REQUIRED, FeeClearanceStatus.CLEARED, FeeClearanceStatus.EXEMPTED)) {
			assertThatCode(() -> gate.requireAllowed(enrollment(1, status), "enter marks")).doesNotThrowAnyException();
		}
	}

	@Test
	void allowsAPendingEnrollmentInTrackAndOffModesAndWithNoSettingsRow() {
		for (AdmissionFeeClearanceMode mode : List.of(AdmissionFeeClearanceMode.TRACK, AdmissionFeeClearanceMode.OFF)) {
			mode(mode);
			assertThatCode(() -> gate.requireAllowed(enrollment(1, FeeClearanceStatus.PENDING), "enter marks")).doesNotThrowAnyException();
		}
		when(settingsRepository.findByOrganisationId(ORG)).thenReturn(Optional.empty());
		assertThatCode(() -> gate.requireAllowed(enrollment(1, FeeClearanceStatus.PENDING), "enter marks")).doesNotThrowAnyException();
	}

	@Test
	void allowsAPendingEnrollmentWhenTheFeesModuleIsOff() {
		when(moduleAccessService.isEnabled(ORG, AppModule.FEES)).thenReturn(false);
		assertThatCode(() -> gate.requireAllowed(enrollment(1, FeeClearanceStatus.PENDING), "enter marks")).doesNotThrowAnyException();
	}

	@Test
	void oneRejectedEnrollmentRejectsTheWholeBatchAndListsEveryPendingOne() {
		List<StudentEnrollment> batch = List.of(enrollment(1, FeeClearanceStatus.CLEARED), enrollment(2, FeeClearanceStatus.PENDING),
				enrollment(3, FeeClearanceStatus.EXEMPTED), enrollment(4, FeeClearanceStatus.PENDING));

		assertThatThrownBy(() -> gate.requireAllowed(batch, "enter marks"))
				.isInstanceOfSatisfying(AdmissionFeePendingException.class, e -> assertThat(e.getEnrollmentIds()).containsExactly(2L, 4L));
	}

	@Test
	void aBatchWithNoPendingEnrollmentIsAllowedAndNullsAreIgnored() {
		List<StudentEnrollment> batch = Arrays.asList(enrollment(1, FeeClearanceStatus.CLEARED), null, enrollment(3, FeeClearanceStatus.NOT_REQUIRED));
		assertThatCode(() -> gate.requireAllowed(batch, "enter marks")).doesNotThrowAnyException();
		assertThatCode(() -> gate.requireAllowed(List.of(), "enter marks")).doesNotThrowAnyException();
	}

	@Test
	void settingsAndModuleAreReadOnceForABatchAndNotAtAllWhenNobodyIsPending() {
		List<StudentEnrollment> pendingBatch = List.of(enrollment(1, FeeClearanceStatus.PENDING), enrollment(2, FeeClearanceStatus.PENDING),
				enrollment(3, FeeClearanceStatus.PENDING));
		assertThatThrownBy(() -> gate.requireAllowed(pendingBatch, "enter marks")).isInstanceOf(AdmissionFeePendingException.class);
		verify(settingsRepository, times(1)).findByOrganisationId(ORG);
		verify(moduleAccessService, times(1)).isEnabled(ORG, AppModule.FEES);

		AdmissionSettingsRepository untouchedSettings = mock(AdmissionSettingsRepository.class);
		ModuleAccessService untouchedModules = mock(ModuleAccessService.class);
		new AdmissionFeeGate(untouchedSettings, untouchedModules)
				.requireAllowed(List.of(enrollment(1, FeeClearanceStatus.CLEARED), enrollment(2, FeeClearanceStatus.EXEMPTED)), "enter marks");
		verify(untouchedSettings, never()).findByOrganisationId(any());
		verify(untouchedModules, never()).isEnabled(any(), any());
	}
}
