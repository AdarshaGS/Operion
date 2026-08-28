package com.operion.reporting;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * A second DataSource for report execution, connected as the restricted `reporting_ro`
 * role (V55) against the same `operion` database, but granted SELECT per-view only on the
 * `reporting_*` views (V58) - never the app's own tables.
 * Registering any second DataSource bean makes Spring Boot's auto-configured one (built
 * from spring.datasource.*) back off entirely (@ConditionalOnMissingBean), so the app's
 * main connection is redeclared here too via the officially documented "two DataSources"
 * pattern - same spring.datasource.* properties, just made explicit and marked @Primary
 * so every other bean's unqualified `DataSource` injection still resolves to it.
 */
@Configuration
public class ReportingDataSourceConfig {

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource")
	public DataSourceProperties applicationDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@Primary
	public DataSource applicationDataSource(@Qualifier("applicationDataSourceProperties") DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().build();
	}

	@Bean
	@ConfigurationProperties("app.reporting.datasource")
	public DataSourceProperties reportingDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	public DataSource reportingDataSource(@Qualifier("reportingDataSourceProperties") DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().build();
	}
}
