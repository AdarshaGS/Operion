package com.operion.common.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Permission;
import com.operion.authorization.PermissionInterceptor;
import com.operion.authorization.PermissionRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.timetable.SubstitutionService;
import com.operion.timetable.TimetableEntryRepository;
import com.operion.timetable.TimetableSubstitutionRepository;
import com.operion.timetable.api.SubstitutionController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The leave-to-cover endpoints over HTTP: the real SubstitutionController behind the real PermissionInterceptor (backed by
 * real membership rows) and ApiExceptionHandler, with the service stubbed. Lives in this package because the handler is
 * package-private.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SubstitutionCoverEndpointsTest {

	private static final LocalDate FROM = LocalDate.of(2025, 6, 2);
	private static final LocalDate TO = LocalDate.of(2025, 6, 8);

	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private RoleRepository roleRepository;
	@Autowired private PermissionRepository permissionRepository;
	@Autowired private OrganisationMembershipRepository membershipRepository;

	private final SubstitutionService service = mock(SubstitutionService.class);
	private MockMvc mockMvc;
	private User user;

	@BeforeEach
	void setUp() {
		Organisation org = organisationRepository.save(new Organisation("Test School", "Test School Trust", "sub-ep-" + System.nanoTime()));
		user = userRepository.save(new User("sub-ep-" + System.nanoTime() + "@example.com", null, "hash"));
		TenantContext.set(org.getId(), user.getId());
		SubstitutionController controller = new SubstitutionController(service, mock(TimetableEntryRepository.class),
				mock(TimetableSubstitutionRepository.class), mock(PersonRepository.class));
		mockMvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new PermissionInterceptor(membershipRepository))
				.setControllerAdvice(new ApiExceptionHandler()).build();
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void grant(String code) {
		// permission codes are unique across tests, which share one database
		Permission permission = permissionRepository.findByCode(code).orElseGet(() -> permissionRepository.save(new Permission(code, "test", null)));
		Role role = new Role("Role " + code + System.nanoTime(), "test role", false);
		role.grant(permission);
		role = roleRepository.save(role);
		Person person = personRepository.save(new Person("Grantee", code));
		membershipRepository.save(new OrganisationMembership(user, person, role, null));
	}

	@Test
	void uncoveredIsForbiddenWithoutTimetableView() throws Exception {
		mockMvc.perform(get("/api/v1/timetable/substitutions/uncovered").param("from", "2025-06-02").param("to", "2025-06-08"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}

	@Test
	void suggestionsAreForbiddenWithoutTimetableView() throws Exception {
		mockMvc.perform(get("/api/v1/timetable/substitutions/suggestions").param("from", "2025-06-02").param("to", "2025-06-08"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}

	@Test
	void anotherPermissionDoesNotOpenTheEndpoints() throws Exception {
		grant("TIMETABLE_MANAGE_ELSEWHERE");

		mockMvc.perform(get("/api/v1/timetable/substitutions/uncovered").param("from", "2025-06-02").param("to", "2025-06-08"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/timetable/substitutions/suggestions").param("from", "2025-06-02").param("to", "2025-06-08"))
				.andExpect(status().isForbidden());
	}

	@Test
	void uncoveredPassesItsParametersThroughForACallerWithTimetableView() throws Exception {
		grant("TIMETABLE_VIEW");
		when(service.uncovered(any(), any(), any(), eq(true))).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/timetable/substitutions/uncovered").param("from", "2025-06-02").param("to", "2025-06-08")
				.param("campusId", "7").param("uncoveredOnly", "true"))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
		verify(service).uncovered(FROM, TO, 7L, true);
	}

	@Test
	void uncoveredDefaultsToAllSlotsAndAllCampuses() throws Exception {
		grant("TIMETABLE_VIEW");
		when(service.uncovered(any(), any(), any(), eq(false))).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/timetable/substitutions/uncovered").param("from", "2025-06-02").param("to", "2025-06-08"))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
		verify(service).uncovered(FROM, TO, null, false);
	}

	@Test
	void suggestionsPassTheirParametersThroughForACallerWithTimetableView() throws Exception {
		grant("TIMETABLE_VIEW");
		when(service.suggest(any(), any(), any())).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/timetable/substitutions/suggestions").param("from", "2025-06-02").param("to", "2025-06-08")
				.param("campusId", "7"))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
		verify(service).suggest(FROM, TO, 7L);
		verify(service, never()).assign(any(), any(), any(), any());
	}
}
