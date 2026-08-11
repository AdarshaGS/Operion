package com.operion.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
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
 * Proves PermissionInterceptor's decision logic in isolation: no annotation = open to any
 * authenticated member, OPTIONS always bypasses (CORS preflight, same reasoning as
 * JwtAuthenticationInterceptor), and a method/class @RequirePermission is enforced against
 * the caller's actual resolved permissions (backed by real DB rows, not mocks - matching
 * the DataJpaTest convention used everywhere else in this codebase).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PermissionInterceptorTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PermissionRepository permissionRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	private PermissionInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new PermissionInterceptor(membershipRepository);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@RequirePermission("CLASS_LEVEL_VIEW")
	static class ClassLevelGatedController {
		public void anyMethod() {
		}
	}

	static class MethodLevelGatedController {
		@RequirePermission("METHOD_LEVEL_VIEW")
		public void gatedMethod() {
		}

		public void openMethod() {
		}
	}

	@Test
	void allowsAnyAuthenticatedCallerWhenNoAnnotationIsPresent() throws NoSuchMethodException {
		HandlerMethod handlerMethod = new HandlerMethod(new MethodLevelGatedController(),
				MethodLevelGatedController.class.getMethod("openMethod"));

		boolean allowed = interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/open"), new MockHttpServletResponse(),
				handlerMethod);

		assertThat(allowed).isTrue();
	}

	@Test
	void alwaysAllowsCorsPreflightRegardlessOfAnnotation() throws NoSuchMethodException {
		HandlerMethod handlerMethod = new HandlerMethod(new MethodLevelGatedController(),
				MethodLevelGatedController.class.getMethod("gatedMethod"));

		boolean allowed = interceptor.preHandle(new MockHttpServletRequest("OPTIONS", "/api/v1/gated"), new MockHttpServletResponse(),
				handlerMethod);

		assertThat(allowed).isTrue();
	}

	@Test
	void deniesAMethodLevelGatedEndpointWhenTheCallerLacksThePermission() throws NoSuchMethodException {
		Organisation org = organisationRepository.save(new Organisation("Test School", "Test School Trust", "pi-deny-method"));
		User user = userRepository.save(new User("no-perm-method@example.com", null, "hash"));
		TenantContext.set(org.getId(), user.getId());

		HandlerMethod handlerMethod = new HandlerMethod(new MethodLevelGatedController(),
				MethodLevelGatedController.class.getMethod("gatedMethod"));

		assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/gated"),
				new MockHttpServletResponse(), handlerMethod)).isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void allowsAMethodLevelGatedEndpointWhenTheCallerHasThePermission() throws NoSuchMethodException {
		Organisation org = organisationRepository.save(new Organisation("Test School", "Test School Trust", "pi-allow-method"));
		User user = userRepository.save(new User("has-perm-method@example.com", null, "hash"));
		TenantContext.set(org.getId(), user.getId());
		grantPermission(user, "METHOD_LEVEL_VIEW");

		HandlerMethod handlerMethod = new HandlerMethod(new MethodLevelGatedController(),
				MethodLevelGatedController.class.getMethod("gatedMethod"));

		boolean allowed = interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/gated"), new MockHttpServletResponse(),
				handlerMethod);

		assertThat(allowed).isTrue();
	}

	@Test
	void deniesAClassLevelGatedEndpointWhenTheCallerLacksThePermission() throws NoSuchMethodException {
		Organisation org = organisationRepository.save(new Organisation("Test School", "Test School Trust", "pi-deny-class"));
		User user = userRepository.save(new User("no-perm-class@example.com", null, "hash"));
		TenantContext.set(org.getId(), user.getId());

		HandlerMethod handlerMethod = new HandlerMethod(new ClassLevelGatedController(), ClassLevelGatedController.class.getMethod("anyMethod"));

		assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/class-gated"),
				new MockHttpServletResponse(), handlerMethod)).isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void deniesWhenThereIsNoAuthenticatedCallerAtAll() throws NoSuchMethodException {
		HandlerMethod handlerMethod = new HandlerMethod(new ClassLevelGatedController(), ClassLevelGatedController.class.getMethod("anyMethod"));

		assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/class-gated"),
				new MockHttpServletResponse(), handlerMethod)).isInstanceOf(AuthorizationDeniedException.class);
	}

	private void grantPermission(User user, String code) {
		Permission permission = permissionRepository.save(new Permission(code, "test", null));
		Role role = new Role("Grantee Role " + code, "test role", false);
		role.grant(permission);
		role = roleRepository.save(role);
		Person person = personRepository.save(new Person("Grantee", code));
		membershipRepository.save(new OrganisationMembership(user, person, role, null));
	}
}
