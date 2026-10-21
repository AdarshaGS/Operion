package com.operion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.operion.authorization.PermissionChecker;
import com.operion.authorization.PortalSessionPolicy;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SEC-M6: the person directory stays open to staff pickers, but only people-viewing roles get contact details. */
class PersonContactVisibilityTest {

	private static final long ACTOR = 42L;

	private final PersonRepository personRepository = mock(PersonRepository.class);
	private final PortalSessionPolicy portalSessionPolicy = mock(PortalSessionPolicy.class);
	private final PermissionChecker permissionChecker = mock(PermissionChecker.class);
	private final PersonController controller = new PersonController(personRepository, portalSessionPolicy, permissionChecker);

	@BeforeEach
	void setUp() {
		TenantContext.set(1L, ACTOR);
		Person person = new Person("Asha", "Rao");
		person.setPhone("9000000000");
		person.setEmail("asha@example.com");
		person.setAddress("12 Main Road");
		when(personRepository.findAll()).thenReturn(List.of(person));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void aRoleWithoutPeopleViewingPermissionsGetsNamesOnly() {
		when(permissionChecker.holdsAny(eq(ACTOR), any())).thenReturn(false);

		PersonResponse response = controller.list().get(0);

		assertThat(response.firstName()).isEqualTo("Asha");
		assertThat(response.phone()).isNull();
		assertThat(response.email()).isNull();
		assertThat(response.address()).isNull();
	}

	@Test
	void aRoleThatSeesPeopleRecordsGetsContactDetails() {
		when(permissionChecker.holdsAny(eq(ACTOR), any())).thenReturn(true);

		PersonResponse response = controller.list().get(0);

		assertThat(response.phone()).isEqualTo("9000000000");
		assertThat(response.email()).isEqualTo("asha@example.com");
	}
}
