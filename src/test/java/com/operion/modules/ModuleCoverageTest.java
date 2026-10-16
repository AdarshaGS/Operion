package com.operion.modules;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.junit.jupiter.api.Test;

/**
 * Guards the feature-flag catalogue against drift: every controller either belongs to a
 * module (by package or @RequireModule) or lives in a package deliberately listed as core.
 * Without this a new vertical's controllers would silently stay reachable after the
 * platform owner switched that feature off.
 */
class ModuleCoverageTest {

	/** Never switchable: turning these off would lock every user out or break the platform itself. */
	private static final Set<String> CORE_PACKAGES = Set.of(
			"com.operion.audit", "com.operion.authorization", "com.operion.billing", "com.operion.common",
			"com.operion.customfield", "com.operion.document", "com.operion.identity", "com.operion.integration",
			"com.operion.modules", "com.operion.onboarding", "com.operion.organisation", "com.operion.platform",
			"com.operion.storage", "com.operion.support");

	/** The portal package mixes modules: every handler in it must be pinned except the identity lookup. */
	private static final Set<String> PORTAL_CORE_CONTROLLERS = Set.of("MeController");

	@Test
	void everyControllerIsEitherAModuleOrExplicitlyCore() {
		List<String> uncovered = new ArrayList<>();
		for (Class<?> controller : controllers()) {
			String pkg = controller.getPackageName();
			boolean explicitlyCore = CORE_PACKAGES.stream().anyMatch(core -> pkg.equals(core) || pkg.startsWith(core + "."));
			boolean inPortal = pkg.equals("com.operion.portal") || pkg.startsWith("com.operion.portal.");
			if (inPortal) {
				continue;
			}
			boolean claimed = AppModule.forPackage(pkg).isPresent() || AnnotatedElementUtils.hasAnnotation(controller, RequireModule.class);
			if (!claimed && !explicitlyCore) {
				uncovered.add(controller.getName());
			}
		}
		assertThat(uncovered).as("controllers in a package that is neither an AppModule nor a core package").isEmpty();
	}

	@Test
	void everyPortalHandlerIsPinnedToAModule() {
		List<String> unpinned = new ArrayList<>();
		for (Class<?> controller : controllers()) {
			if (!controller.getPackageName().startsWith("com.operion.portal") || PORTAL_CORE_CONTROLLERS.contains(controller.getSimpleName())) {
				continue;
			}
			for (Method method : controller.getDeclaredMethods()) {
				if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
						&& ModuleAccessInterceptor.resolve(method, controller) == null) {
					unpinned.add(controller.getSimpleName() + "." + method.getName());
				}
			}
		}
		assertThat(unpinned).as("portal handlers with no @RequireModule").isEmpty();
	}

	@Test
	void packageResolutionPicksTheMatchingModule() {
		assertThat(AppModule.forPackage("com.operion.finance.api")).contains(AppModule.FEES);
		assertThat(AppModule.forPackage("com.operion.parent")).contains(AppModule.STUDENTS);
		assertThat(AppModule.forPackage("com.operion.identity.auth.api")).isEmpty();
		// "student" must not swallow an unrelated package that merely shares the prefix.
		assertThat(AppModule.forPackage("com.operion.studentx")).isEmpty();
	}

	private static List<Class<?>> controllers() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
		List<Class<?>> found = new ArrayList<>();
		for (BeanDefinition definition : scanner.findCandidateComponents("com.operion")) {
			try {
				found.add(Class.forName(definition.getBeanClassName()));
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException(e);
			}
		}
		return found;
	}
}
