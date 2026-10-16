package com.operion.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;

/**
 * Backed by real rows (DataJpaTest, no mocks) like the other authorization tests: the
 * default is "enabled", the latest toggle wins, one organisation's switch never touches
 * another's, and the interceptor turns a disabled module into a 403 for any caller.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, ModuleAccessService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ModuleAccessServiceTest {

	private static final AppModule[] DEFAULT_OFF = { AppModule.TRANSPORT, AppModule.COMMUNICATION, AppModule.MESSAGING };

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private OrganisationModuleSettingRepository settingRepository;

	@Autowired
	private ModuleAccessService service;

	private Organisation orgA;
	private Organisation orgB;

	@BeforeEach
	void setUp() {
		TenantContext.clear();
		settingRepository.deleteAll();
		String suffix = String.valueOf(System.nanoTime());
		orgA = organisationRepository.save(new Organisation("Org A " + suffix, "Org A Legal", "a-" + suffix));
		orgB = organisationRepository.save(new Organisation("Org B " + suffix, "Org B Legal", "b-" + suffix));
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	@Test
	void everyModuleIsEnabledByDefaultExceptTransportCommunicationAndMessaging() {
		assertThat(service.disabledModules(orgA.getId())).containsExactlyInAnyOrder(DEFAULT_OFF);
		assertThat(service.states(orgA.getId())).hasSize(AppModule.values().length)
				.allMatch(state -> state.enabled() == state.module().defaultEnabled());
	}

	@Test
	void aDefaultOffModuleCanBeSwitchedOnForOneOrganisationOnly() {
		service.setEnabled(orgA.getId(), AppModule.TRANSPORT, true, "requested");
		assertThat(service.isEnabled(orgA.getId(), AppModule.TRANSPORT)).isTrue();
		assertThat(service.isEnabled(orgB.getId(), AppModule.TRANSPORT)).isFalse();
	}

	@Test
	void disablingAndReenablingFollowsTheLatestToggle() {
		service.setEnabled(orgA.getId(), AppModule.FEES, false, "unpaid");
		assertThat(service.disabledModules(orgA.getId())).contains(AppModule.FEES);

		service.setEnabled(orgA.getId(), AppModule.FEES, true, null);
		assertThat(service.disabledModules(orgA.getId())).containsExactlyInAnyOrder(DEFAULT_OFF);
		assertThat(service.history(orgA.getId())).extracting(OrganisationModuleSetting::isEnabled).containsExactly(true, false);
	}

	@Test
	void repeatingTheCurrentStateWritesNoHistoryRow() {
		service.setEnabled(orgA.getId(), AppModule.LIBRARY, true, null);
		service.setEnabled(orgA.getId(), AppModule.LIBRARY, false, null);
		service.setEnabled(orgA.getId(), AppModule.LIBRARY, false, null);
		assertThat(service.history(orgA.getId())).hasSize(1);
	}

	@Test
	void oneOrganisationsToggleDoesNotAffectAnother() {
		service.setEnabled(orgA.getId(), AppModule.PAYROLL, false, null);
		assertThat(service.disabledModules(orgB.getId())).containsExactlyInAnyOrder(DEFAULT_OFF);
	}

	@Test
	void unknownOrganisationIsRejected() {
		assertThatThrownBy(() -> service.setEnabled(-42L, AppModule.FEES, false, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void interceptorBlocksADisabledModuleForAnyCallerButNotOtherModulesOrCore() throws Exception {
		ModuleAccessInterceptor interceptor = new ModuleAccessInterceptor(service);
		service.setEnabled(orgA.getId(), AppModule.LIBRARY, false, null);

		TenantContext.set(orgA.getId(), 1L);
		assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(),
				handler(LibraryProbe.class)))
						.isInstanceOf(ModuleDisabledException.class);
		assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), handler(PinnedFeesProbe.class)))
				.isTrue();
		assertThat(interceptor.preHandle(new MockHttpServletRequest("OPTIONS", "/x"), new MockHttpServletResponse(), handler(LibraryProbe.class)))
				.isTrue();

		// Another organisation, same endpoint: unaffected.
		TenantContext.set(orgB.getId(), 2L);
		assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), handler(LibraryProbe.class)))
				.isTrue();
	}

	@Test
	void methodLevelRequireModuleOverridesThePackage() throws Exception {
		ModuleAccessInterceptor interceptor = new ModuleAccessInterceptor(service);
		service.setEnabled(orgA.getId(), AppModule.FEES, false, null);
		TenantContext.set(orgA.getId(), 1L);

		assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(),
				handler(PinnedFeesProbe.class)))
						.isInstanceOf(ModuleDisabledException.class);
	}

	private static HandlerMethod handler(Class<?> probe) throws Exception {
		return new HandlerMethod(probe.getDeclaredConstructor().newInstance(), probe.getDeclaredMethod("handle"));
	}

	/** Package com.operion.modules is core, so only the explicit annotation pins a module for these probes. */
	@RequireModule(AppModule.LIBRARY)
	static class LibraryProbe {
		public void handle() {
		}
	}

	static class PinnedFeesProbe {
		@RequireModule(AppModule.FEES)
		public void handle() {
		}
	}
}
