package com.operion.academic;

import java.time.Instant;
import java.time.LocalTime;

import com.operion.organisation.Organisation;
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
 * School-vertical settings, split from the core {@link OrganisationConfiguration} - school
 * start/end time is School vocabulary and doesn't belong on the generic tenant-config table
 * every future vertical (e.g. Healthcare) would also read/write. 1:1, keyed by the same id as
 * Organisation, same shape as OrganisationConfiguration.
 */
@Getter
@Setter
@Entity
@Table(name = "academic_configurations")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademicConfiguration {

	@Id
	@Column(name = "organisation_id")
	private Long organisationId;

	@OneToOne
	@MapsId
	private Organisation organisation;

	@Column(name = "school_start_time")
	private LocalTime schoolStartTime;

	@Column(name = "school_end_time")
	private LocalTime schoolEndTime;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@LastModifiedBy
	@Column(name = "updated_by")
	private Long updatedBy;

	public AcademicConfiguration(Organisation organisation) {
		this.organisation = organisation;
	}
}
