package com.operion.organisation;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Rarely-changing org settings, split from {@link Organisation} so the hot-path
 * organisations table (looked up by slug on every request) stays narrow. 1:1, keyed
 * by the same id as its Organisation - not tenant-filtered via @TenantId since the
 * PK already is the tenant id.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_configurations")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganisationConfiguration {

	@Id
	@Column(name = "organisation_id")
	private Long organisationId;

	@OneToOne
	@MapsId
	private Organisation organisation;

	private String timezone;

	@Column(name = "default_currency")
	private String defaultCurrency;

	@Column(name = "date_format")
	private String dateFormat;

	/** Bit 0 = Monday .. bit 6 = Sunday. A plain int - not worth a child table or JSON for 7 static days. */
	@Column(name = "working_days_mask", nullable = false)
	private int workingDaysMask;

	@Column(name = "school_start_time")
	private LocalTime schoolStartTime;

	@Column(name = "school_end_time")
	private LocalTime schoolEndTime;

	@Column(name = "logo_url")
	private String logoUrl;

	@Column(name = "primary_color")
	private String primaryColor;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@LastModifiedBy
	@Column(name = "updated_by")
	private Long updatedBy;

	public OrganisationConfiguration(Organisation organisation) {
		this.organisation = organisation;
		this.timezone = "Asia/Kolkata";
		this.defaultCurrency = "INR";
		this.dateFormat = "dd-MM-yyyy";
		this.workingDaysMask = 0b0111111; // Monday-Saturday
	}
}
