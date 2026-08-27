package com.operion.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The fixed IANA timezone catalog (Asia/Kolkata, America/New_York, ...). Global, seeded
 * via Flyway migration from the JVM's own {@code ZoneId.getAvailableZoneIds()} - same
 * closed-catalog shape as {@link com.operion.authorization.Permission}, so every row is
 * guaranteed valid for {@code ZoneId.of(name)} at runtime.
 */
@Getter
@Entity
@Table(name = "timezones")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Timezone extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String name;

	/** The segment before the first '/' (e.g. "Asia"), or the whole name for zones
	 * without one (e.g. "UTC") - lets the frontend group the picker by region. */
	@Column(nullable = false)
	private String region;

	/** Package-private, not exposed via any service/controller - the catalog is closed and
	 * only ever populated by a Flyway migration in real deployments. Exists so tests can
	 * build a fixture row directly instead of depending on migration data that intentionally
	 * doesn't run against the H2 test database (see application-test properties). */
	Timezone(String name, String region) {
		this.name = name;
		this.region = region;
	}
}
