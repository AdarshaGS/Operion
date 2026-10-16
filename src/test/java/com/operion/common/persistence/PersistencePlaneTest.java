package com.operion.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * The Platform DB and the School DB are different databases, so a JPA relation (a join, a
 * foreign key) cannot cross between them - a cross-plane reference must be a plain id column,
 * resolved in the application. Fails the build the moment an entity maps a relation to an
 * entity on the other side of the split.
 */
class PersistencePlaneTest {

	@Test
	void noJpaRelationCrossesThePlatformSchoolBoundary() throws ClassNotFoundException {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

		List<String> violations = new ArrayList<>();
		for (BeanDefinition definition : scanner.findCandidateComponents("com.operion")) {
			Class<?> owner = Class.forName(definition.getBeanClassName());
			boolean ownerIsPlatform = owner.isAnnotationPresent(PlatformPlane.class);
			for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
				for (Field field : type.getDeclaredFields()) {
					if (!isRelation(field)) {
						continue;
					}
					Class<?> target = targetEntity(field);
					if (target != null && target.isAnnotationPresent(Entity.class)
							&& target.isAnnotationPresent(PlatformPlane.class) != ownerIsPlatform) {
						violations.add(owner.getSimpleName() + "." + field.getName() + " -> " + target.getSimpleName());
					}
				}
			}
		}

		assertThat(violations).as("cross-plane JPA relations; use a plain id column instead").isEmpty();
	}

	private static boolean isRelation(Field field) {
		return field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)
				|| field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class);
	}

	private static Class<?> targetEntity(Field field) {
		Type generic = field.getGenericType();
		if (generic instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length == 1
				&& parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
			return element;
		}
		return field.getType();
	}
}
