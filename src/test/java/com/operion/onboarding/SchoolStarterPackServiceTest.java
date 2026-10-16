package com.operion.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import com.operion.modules.OrganisationModuleSettingRepository;
import com.operion.onboarding.SchoolStarterPackService.ModuleChoice;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real needs catalog and real module settings (no mocks for them); only the template apply -
 * covered by SchoolTemplateServiceTest - is mocked, so what is asserted here is which modules
 * get chosen, what is handed to the template, and that the choice can be made only once.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, ModuleAccessService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SchoolStarterPackServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;
	@Autowired
	private OrganisationModuleSettingRepository settingRepository;
	@Autowired
	private SchoolStarterNeedRepository needRepository;
	@Autowired
	private ModuleAccessService moduleAccessService;

	private SchoolTemplateService templateService;
	private SchoolStarterPackService service;
	private Organisation organisation;

	@BeforeEach
	void setUp() {
		TenantContext.clear();
		settingRepository.deleteAll();
		seedNeedsOnce();
		templateService = mock(SchoolTemplateService.class);
		service = new SchoolStarterPackService(needRepository, templateService, moduleAccessService);
		organisation = organisationRepository.save(new Organisation("Pack School", "Pack Legal", "pack-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	/** Stand-in for V132's seed rows (Flyway is off under the H2 test profile). */
	private void seedNeedsOnce() {
		if (!needRepository.findAll().isEmpty()) {
			return;
		}
		needRepository.save(new SchoolStarterNeed("BASELINE", "Core school operations", null, true, true, 0,
				"DASHBOARD,STUDENTS,ACADEMICS,ATTENDANCE,REPORTS"));
		needRepository.save(new SchoolStarterNeed("FEES", "Do you collect fees?", null, true, false, 10, "FEES"));
		needRepository.save(new SchoolStarterNeed("EXAMS", "Do you run exams?", null, true, false, 20, "EXAMINATIONS"));
		needRepository.save(new SchoolStarterNeed("TRANSPORT", "Do you run buses?", null, false, false, 50, "TRANSPORT"));
		needRepository.save(new SchoolStarterNeed("HR", "Do you manage staff?", null, true, false, 70, "HR"));
		needRepository.save(new SchoolStarterNeed("PAYROLL", "Do you run payroll?", null, false, false, 80, "PAYROLL"));
		needRepository.save(new SchoolStarterNeed("PROCUREMENT", "Do you buy or sell items?", null, false, false, 100, "PURCHASE,SALES"));
	}

	private ModuleChoice choice(SchoolStarterPackService.Recommendation recommendation, AppModule module) {
		return recommendation.modules().stream().filter(c -> c.module() == module).findFirst().orElseThrow();
	}

	@Test
	void questionsExcludeTheBaselineAndKeepTheirOrder() {
		assertThat(service.needs().questions()).extracting(SchoolStarterPackService.Question::key)
				.containsExactly("FEES", "EXAMS", "TRANSPORT", "HR", "PAYROLL", "PROCUREMENT");
		assertThat(service.needs().canSelectModules()).isTrue();
	}

	@Test
	void unansweredQuestionsUseTheirDefaults() {
		var recommendation = service.recommend(Map.of());

		assertThat(choice(recommendation, AppModule.STUDENTS)).matches(c -> c.enabled() && c.locked());
		assertThat(choice(recommendation, AppModule.FEES).enabled()).isTrue();
		assertThat(choice(recommendation, AppModule.TRANSPORT).enabled()).isFalse();
		assertThat(choice(recommendation, AppModule.TRANSPORT).reason()).contains("Do you run buses?");
		assertThat(recommendation.modules()).hasSize(AppModule.values().length);
	}

	@Test
	void aModuleThatNeedsAnotherSwitchesItOnAndSaysWhy() {
		var recommendation = service.recommend(Map.of("FEES", false, "TRANSPORT", true, "PAYROLL", true, "HR", false));

		ModuleChoice fees = choice(recommendation, AppModule.FEES);
		assertThat(fees.enabled()).isTrue();
		assertThat(fees.locked()).isTrue();
		assertThat(fees.reason()).isEqualTo("Required by Transport");
		assertThat(choice(recommendation, AppModule.HR)).matches(c -> c.enabled() && c.locked());
		assertThat(choice(recommendation, AppModule.PAYROLL).locked()).isFalse();
	}

	@Test
	void unknownQuestionKeysAreRejected() {
		assertThatThrownBy(() -> service.recommend(Map.of("HOSTEL", true))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void applyingWithAnswersRecordsEveryModuleAndHandsTheDisabledOnesToTheTemplate() {
		when(templateService.apply(anySet())).thenReturn(new SchoolTemplateResult());

		service.apply(Map.of("EXAMS", false, "PROCUREMENT", true));

		verify(templateService).apply(argThat((Set<AppModule> disabled) -> disabled.contains(AppModule.TRANSPORT)
				&& disabled.contains(AppModule.EXAMINATIONS) && !disabled.contains(AppModule.FEES)));
		assertThat(moduleAccessService.history(organisation.getId())).hasSize(AppModule.values().length)
				.allMatch(row -> "Set by School Starter Pack".equals(row.getNote()));
		Set<AppModule> disabled = moduleAccessService.disabledModules(organisation.getId());
		assertThat(disabled).contains(AppModule.TRANSPORT, AppModule.EXAMINATIONS, AppModule.COMPLIANCE).doesNotContain(AppModule.FEES);
		// Procurement needs Inventory, which has no question of its own: it must come along.
		assertThat(disabled).doesNotContain(AppModule.PURCHASE, AppModule.SALES, AppModule.INVENTORY);
		assertThat(service.needs().canSelectModules()).isFalse();
	}

	@Test
	void theChoiceCanOnlyBeMadeOnce() {
		when(templateService.apply(anySet())).thenReturn(new SchoolTemplateResult());
		service.apply(Map.of());

		assertThatThrownBy(() -> service.apply(Map.of("TRANSPORT", true))).isInstanceOf(IllegalStateException.class);
		assertThat(moduleAccessService.isEnabled(organisation.getId(), AppModule.TRANSPORT)).isFalse();
	}

	@Test
	void applyingWithoutAnswersTouchesNoModuleSetting() {
		when(templateService.apply(anySet())).thenReturn(new SchoolTemplateResult());
		moduleAccessService.setEnabled(organisation.getId(), AppModule.LIBRARY, false, "platform");

		service.apply(null);

		verify(templateService).apply(EnumSet.of(AppModule.LIBRARY, AppModule.TRANSPORT, AppModule.COMMUNICATION, AppModule.MESSAGING));
		assertThat(moduleAccessService.history(organisation.getId())).hasSize(1);
	}

	@Test
	void anOrganisationWithExistingSettingsCannotChooseModules() {
		moduleAccessService.setEnabled(organisation.getId(), AppModule.LIBRARY, false, "platform");

		assertThat(service.needs().canSelectModules()).isFalse();
		assertThatThrownBy(() -> service.apply(Map.of())).isInstanceOf(IllegalStateException.class);
		verify(templateService, never()).apply(anySet());
	}
}
