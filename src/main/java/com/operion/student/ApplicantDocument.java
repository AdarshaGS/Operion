package com.operion.student;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One row of an applicant's staff-managed received-documents checklist. */
@Getter
@Entity
@Table(name = "applicant_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicantDocument extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "applicant_id")
	private Applicant applicant;

	@Column(name = "doc_type", nullable = false, length = 50)
	private String docType;

	@Column(nullable = false)
	private boolean received;

	public ApplicantDocument(Applicant applicant, String docType, boolean received) {
		this.applicant = applicant;
		this.docType = docType;
		this.received = received;
	}

	public void markReceived(boolean received) {
		this.received = received;
	}
}
