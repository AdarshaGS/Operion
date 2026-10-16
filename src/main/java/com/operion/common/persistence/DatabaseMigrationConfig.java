package com.operion.common.persistence;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One Flyway per database, each with its own migration folder and its own
 * {@code flyway_schema_history}: {@code db/migration/platform} for the Platform DB and
 * {@code db/migration/school} for the School DB. Spring Boot's single-datasource Flyway
 * auto-configuration is switched off ({@code spring.flyway.enabled=false}); these beans replace it.
 * Tests use Hibernate DDL instead and set {@code app.flyway.enabled=false}.
 */
@Configuration
@ConditionalOnProperty(name = "app.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseMigrationConfig {

	public static final String SCHOOL_FLYWAY = "schoolFlyway";
	public static final String PLATFORM_FLYWAY = "platformFlyway";

	@Bean(name = SCHOOL_FLYWAY, initMethod = "migrate")
	public Flyway schoolFlyway(@Qualifier("schoolDataSource") DataSource dataSource) {
		return flyway(dataSource, "classpath:db/migration/school");
	}

	@Bean(name = PLATFORM_FLYWAY, initMethod = "migrate")
	public Flyway platformFlyway(@Qualifier("platformDataSource") DataSource dataSource) {
		return flyway(dataSource, "classpath:db/migration/platform");
	}

	private static Flyway flyway(DataSource dataSource, String location) {
		return Flyway.configure().dataSource(dataSource).locations(location).placeholderReplacement(false).load();
	}
}
