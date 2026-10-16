package com.operion.reporting;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A second DataSource for report execution, connected as the restricted `reporting_ro`
 * role (V55) against the School database, but granted SELECT per-view only on the
 * `reporting_*` views (V58) - never the app's own tables. The application's own
 * DataSources (school = primary, platform) are declared in
 * {@link com.operion.common.persistence.PersistenceConfig}.
 */
@Configuration
public class ReportingDataSourceConfig {

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
