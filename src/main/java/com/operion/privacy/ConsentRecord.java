package com.operion.privacy;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Insert-only: a withdrawal is a new row, so the full history of who agreed to what and when
 * survives. The latest row per (person, purpose) is the current state. */
@Getter
@Entity
@Table(name = "consent_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentRecord extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	@ManyToOne(optional = false)
	@JoinColumn(name = "purpose_id")
	private ConsentPurpose purpose;

	@Column(nullable = false)
	private boolean granted;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ConsentSource source;

	/** Nullable. */
	private String note;

	/** Nullable - a User id, no FK by design. */
	@Column(name = "recorded_by")
	private Long recordedBy;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	public ConsentRecord(Person person, ConsentPurpose purpose, boolean granted, ConsentSource source, String note,
			Long recordedBy) {
		this.person = person;
		this.purpose = purpose;
		this.granted = granted;
		this.source = source;
		this.note = note;
		this.recordedBy = recordedBy;
		this.recordedAt = Instant.now();
	}
}
