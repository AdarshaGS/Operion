package com.operion.common.persistence;

import javax.sql.DataSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.persistenceunit.ManagedClassNameFilter;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;

/**
 * Two databases, two persistence units (Operion's multi-database architecture):
 * <ul>
 * <li><b>Platform</b> - {@code app.platform.datasource.*}: organisations, plans, billing,
 * feature flags, platform admins, CRM, support, integration catalogue.</li>
 * <li><b>School</b> - {@code spring.datasource.*} (the primary, so unqualified injection and
 * every existing {@code @Transactional} resolve here): all school data in one shared
 * database, isolated per school by {@code organisation_id} through Hibernate's
 * {@code @TenantId}.</li>
 * </ul>
 * There is deliberately no database per organisation. An entity or repository joins the
 * platform side with {@link PlatformPlane}; everything else is school. Auditing and the
 * tenant resolver (MultiTenancyConfig) apply to the school unit only - platform entities
 * are never tenant-filtered.
 */
@Configuration
@EnableConfigurationProperties({ JpaProperties.class, HibernateProperties.class })
public class PersistenceConfig {

	public static final String PLATFORM_TRANSACTION_MANAGER = "platformTransactionManager";
	public static final String SCHOOL_TRANSACTION_MANAGER = "transactionManager";
	static final String PLATFORM_ENTITY_MANAGER_FACTORY = "platformEntityManagerFactory";
	static final String SCHOOL_ENTITY_MANAGER_FACTORY = "entityManagerFactory";

	private static final String ENTITY_ROOT_PACKAGE = "com.operion";

	// ---- data sources -----------------------------------------------------------------

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource")
	public DataSourceProperties schoolDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@Primary
	public DataSource schoolDataSource(@Qualifier("schoolDataSourceProperties") DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().build();
	}

	@Bean
	@ConfigurationProperties("app.platform.datasource")
	public DataSourceProperties platformDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	public DataSource platformDataSource(@Qualifier("platformDataSourceProperties") DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().build();
	}

	// ---- school persistence unit (primary) --------------------------------------------

	@Bean(SCHOOL_ENTITY_MANAGER_FACTORY)
	@Primary
	public LocalContainerEntityManagerFactoryBean schoolEntityManagerFactory(
			@Qualifier("schoolDataSource") DataSource dataSource, JpaProperties jpaProperties, HibernateProperties hibernateProperties,
			ObjectProvider<HibernatePropertiesCustomizer> customizers, ResourceLoader resourceLoader,
			ConfigurableListableBeanFactory beanFactory,
			@Qualifier(DatabaseMigrationConfig.SCHOOL_FLYWAY) ObjectProvider<Flyway> schoolMigration) {
		schoolMigration.getIfAvailable(); // migrate the School DB before Hibernate validates it
		return buildEntityManagerFactory("school", dataSource, jpaProperties, hibernateProperties,
				customizers.orderedStream().toList(), scan(resourceLoader, false), beanFactory);
	}

	@Bean(SCHOOL_TRANSACTION_MANAGER)
	@Primary
	public PlatformTransactionManager schoolTransactionManager(@Qualifier(SCHOOL_ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory);
	}

	// ---- platform persistence unit ----------------------------------------------------

	@Bean(PLATFORM_ENTITY_MANAGER_FACTORY)
	public LocalContainerEntityManagerFactoryBean platformEntityManagerFactory(
			@Qualifier("platformDataSource") DataSource dataSource, JpaProperties jpaProperties, HibernateProperties hibernateProperties,
			ResourceLoader resourceLoader, ConfigurableListableBeanFactory beanFactory,
			@Qualifier(DatabaseMigrationConfig.PLATFORM_FLYWAY) ObjectProvider<Flyway> platformMigration) {
		platformMigration.getIfAvailable(); // migrate the Platform DB before Hibernate validates it
		// No Hibernate customizers: the @TenantId resolver must not apply to platform
		// entities, which are never tenant-filtered.
		return buildEntityManagerFactory("platform", dataSource, jpaProperties, hibernateProperties, List.of(),
				scan(resourceLoader, true), beanFactory);
	}

	@Bean(PLATFORM_TRANSACTION_MANAGER)
	public PlatformTransactionManager platformTransactionManager(
			@Qualifier(PLATFORM_ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory);
	}

	// ---- repositories -----------------------------------------------------------------

	@Configuration
	@EnableJpaRepositories(basePackages = ENTITY_ROOT_PACKAGE, entityManagerFactoryRef = SCHOOL_ENTITY_MANAGER_FACTORY,
			transactionManagerRef = SCHOOL_TRANSACTION_MANAGER,
			excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = FilterType.ANNOTATION, classes = PlatformPlane.class))
	static class SchoolRepositories {
	}

	@Configuration
	@EnableJpaRepositories(basePackages = ENTITY_ROOT_PACKAGE, entityManagerFactoryRef = PLATFORM_ENTITY_MANAGER_FACTORY,
			transactionManagerRef = PLATFORM_TRANSACTION_MANAGER,
			includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = FilterType.ANNOTATION, classes = PlatformPlane.class))
	static class PlatformRepositories {
	}

	// ---- helpers ----------------------------------------------------------------------

	private static LocalContainerEntityManagerFactoryBean buildEntityManagerFactory(String unitName, DataSource dataSource,
			JpaProperties jpaProperties, HibernateProperties hibernateProperties, List<HibernatePropertiesCustomizer> customizers,
			PersistenceManagedTypes managedTypes, ConfigurableListableBeanFactory beanFactory) {
		HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
		vendorAdapter.setShowSql(jpaProperties.isShowSql());
		vendorAdapter.setGenerateDdl(jpaProperties.isGenerateDdl());
		if (jpaProperties.getDatabase() != null) {
			vendorAdapter.setDatabase(jpaProperties.getDatabase());
		}
		if (jpaProperties.getDatabasePlatform() != null) {
			vendorAdapter.setDatabasePlatform(jpaProperties.getDatabasePlatform());
		}

		Map<String, Object> properties = new HashMap<>(hibernateProperties.determineHibernateProperties(
				jpaProperties.getProperties(), new HibernateSettings().hibernatePropertiesCustomizers(customizers)));
		// Lets Hibernate-instantiated listeners (Spring Data's AuditingEntityListener) be
		// wired from the application context, as Spring Boot's single-unit setup does.
		properties.put(org.hibernate.cfg.AvailableSettings.BEAN_CONTAINER, new SpringBeanContainer(beanFactory));

		LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
		factory.setPersistenceUnitName(unitName);
		factory.setDataSource(dataSource);
		factory.setJpaVendorAdapter(vendorAdapter);
		factory.setManagedTypes(managedTypes);
		factory.setJpaPropertyMap(properties);
		return factory;
	}

	/**
	 * Scans {@code com.operion} for managed types and keeps the ones for this unit: a
	 * {@code @PlatformPlane} entity belongs to the platform unit only, every other entity to
	 * the school unit only. Mapped superclasses and converters (no {@code @Entity}) go to both.
	 */
	private static PersistenceManagedTypes scan(ResourceLoader resourceLoader, boolean platform) {
		ClassLoader classLoader = resourceLoader.getClassLoader();
		ManagedClassNameFilter filter = className -> {
			Class<?> type = loadClass(className, classLoader);
			if (!type.isAnnotationPresent(Entity.class)) {
				return true;
			}
			return type.isAnnotationPresent(PlatformPlane.class) == platform;
		};
		return new PersistenceManagedTypesScanner(resourceLoader, filter).scan(ENTITY_ROOT_PACKAGE);
	}

	private static Class<?> loadClass(String className, ClassLoader classLoader) {
		try {
			return ClassUtils.forName(className, classLoader);
		} catch (ClassNotFoundException | LinkageError e) {
			throw new IllegalStateException("Cannot load managed class " + className, e);
		}
	}
}
