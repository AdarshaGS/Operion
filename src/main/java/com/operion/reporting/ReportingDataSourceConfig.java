package com.operion.reporting;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
	public DataSource reportingDataSource(@Qualifier("reportingDataSourceProperties") DataSourceProperties properties,
			@Value("${app.reporting.datasource.max-pool-size:5}") int maxPoolSize,
			@Value("${app.reporting.datasource.connection-timeout-ms:5000}") long connectionTimeoutMs) {
		// Bounded so a burst of slow reports cannot hold every connection for ever or starve the box: at most maxPoolSize
		// reports run at once, and a request that cannot get a connection fails fast instead of queueing.
		HikariDataSource dataSource = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
		dataSource.setMaximumPoolSize(maxPoolSize);
		dataSource.setConnectionTimeout(connectionTimeoutMs);
		return dataSource;
	}
}
