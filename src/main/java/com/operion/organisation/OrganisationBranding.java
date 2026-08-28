package com.operion.organisation;

import java.time.Instant;

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
 * Assets/text that appear on every printed/branded surface (receipts, letterheads, ID
 * cards - once those land in Phase C). 1:1 with {@link Organisation}, same shape as
 * {@link OrganisationConfiguration} - keyed by the same id, no separate surrogate PK.
 * Distinct from OrganisationConfiguration.logoUrl, which is a plain pasted URL used for
 * the top-bar/profile display; the refs here point into {@link com.operion.storage.AssetStorageService}
 * and are meant for print-quality output.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_branding")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganisationBranding {

	@Id
	@Column(name = "organisation_id")
	private Long organisationId;

	@OneToOne
	@MapsId
	private Organisation organisation;

	@Column(name = "logo_ref")
	private String logoRef;

	@Column(name = "stamp_ref")
	private String stampRef;

	@Column(name = "signature_ref")
	private String signatureRef;

	@Column(name = "school_name_override")
	private String schoolNameOverride;

	@Column(name = "address_line")
	private String addressLine;

	@Column(name = "affiliation_text")
	private String affiliationText;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@LastModifiedBy
	@Column(name = "updated_by")
	private Long updatedBy;

	public OrganisationBranding(Organisation organisation) {
		this.organisation = organisation;
	}
}
