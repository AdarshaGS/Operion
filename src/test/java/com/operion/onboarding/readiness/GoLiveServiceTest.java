package com.operion.onboarding.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoLiveServiceTest {

	private final ModuleAccessService moduleAccessService = mock(ModuleAccessService.class);

	@BeforeEach
	void setUp() {
		TenantContext.set(7L, null);
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	private static ReadinessCheck check(AppModule module, ReadinessFinding... findings) {
		return new ReadinessCheck() {
			public AppModule module() {
				return module;
			}

			public List<ReadinessFinding> check() {
				return List.of(findings);
			}
		};
	}

	@Test
	void aFailingModuleBlocksGoLiveAndNamesWhatToFix() {
		when(moduleAccessService.disabledModules(7L)).thenReturn(EnumSet.noneOf(AppModule.class));
		GoLiveService service = new GoLiveService(List.of(
				check(AppModule.FEES, ReadinessFinding.fail(ReadinessStage.CONFIGURATION, "No fee structure", "/fees/setup"),
						ReadinessFinding.pass(ReadinessStage.CONFIGURATION, "Categories")),
				check(AppModule.STUDENTS, ReadinessFinding.warn(ReadinessStage.DATA, "No students", "/students"))), moduleAccessService);

		GoLiveReport report = service.report();

		assertThat(report.ready()).isFalse();
		assertThat(group(report, "FEES").level()).isEqualTo(ReadinessLevel.FAIL);
		assertThat(group(report, "FEES").findings()).hasSize(2);
		assertThat(group(report, "STUDENTS").level()).isEqualTo(ReadinessLevel.WARN);
	}

	@Test
	void warningsAloneDoNotBlockGoLive() {
		when(moduleAccessService.disabledModules(7L)).thenReturn(EnumSet.noneOf(AppModule.class));
		GoLiveService service = new GoLiveService(
				List.of(check(AppModule.STUDENTS, ReadinessFinding.warn(ReadinessStage.DATA, "No students", "/students"))), moduleAccessService);

		assertThat(service.report().ready()).isTrue();
	}

	@Test
	void aSwitchedOffModuleIsNeverCheckedButIsNamed() {
		when(moduleAccessService.disabledModules(7L)).thenReturn(EnumSet.of(AppModule.TRANSPORT));
		GoLiveService service = new GoLiveService(List.of(
				check(AppModule.TRANSPORT, ReadinessFinding.fail(ReadinessStage.DATA, "No vehicles", "/transport"))), moduleAccessService);

		GoLiveReport report = service.report();

		assertThat(report.ready()).isTrue();
		assertThat(report.groups()).extracting(GoLiveReport.Group::code).doesNotContain("TRANSPORT");
		assertThat(report.hiddenModules()).containsExactly("Transport");
	}

	@Test
	void aModuleWithNoChecksIsReportedAsUncheckedNotAsPassing() {
		when(moduleAccessService.disabledModules(7L)).thenReturn(EnumSet.noneOf(AppModule.class));
		GoLiveService service = new GoLiveService(List.of(), moduleAccessService);

		assertThat(group(service.report(), "SALES").hasChecks()).isFalse();
	}

	@Test
	void organisationWideChecksAlwaysRun() {
		when(moduleAccessService.disabledModules(7L)).thenReturn(EnumSet.complementOf(EnumSet.noneOf(AppModule.class)));
		GoLiveService service = new GoLiveService(
				List.of(check(null, ReadinessFinding.fail(ReadinessStage.CONFIGURATION, "No academic year", "/settings/academic-years"))),
				moduleAccessService);

		GoLiveReport report = service.report();

		assertThat(report.ready()).isFalse();
		assertThat(group(report, "ORGANISATION").findings()).hasSize(1);
	}

	private static GoLiveReport.Group group(GoLiveReport report, String code) {
		return report.groups().stream().filter(group -> group.code().equals(code)).findFirst().orElseThrow();
	}
}
