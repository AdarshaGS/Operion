package com.operion.regulatory;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.student.StudentExit;
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

/**
 * A prepared hand-off of one student's transfer record to an external system. Operion builds
 * and tracks the payload; a person sends it and records the outcome here - no live call is made
 * to any external system. {@link #connectorName} is org-defined free text, so no particular
 * registry is wired into the core.
 */
@Getter
@Entity
@Table(name = "transfer_connector_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferConnectorJob extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_exit_id")
	private StudentExit studentExit;

	@ManyToOne(optional = false)
	@JoinColumn(name = "schema_id")
	private RegulatoryExportSchema schema;

	@Column(name = "connector_name", nullable = false)
	private String connectorName;

	@Column(nullable = false, columnDefinition = "longtext")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransferConnectorJobStatus status;

	/** Nullable - the receiving system's own reference, recorded on submission. */
	@Column(name = "external_reference")
	private String externalReference;

	/** Nullable - only set while FAILED. */
	@Column(name = "failure_reason")
	private String failureReason;

	/** Nullable - a User id, no FK by design. */
	@Column(name = "prepared_by")
	private Long preparedBy;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	public TransferConnectorJob(StudentExit studentExit, RegulatoryExportSchema schema, String connectorName, String payload,
			Long preparedBy) {
		this.studentExit = studentExit;
		this.schema = schema;
		this.connectorName = connectorName;
		this.payload = payload;
		this.preparedBy = preparedBy;
		this.status = TransferConnectorJobStatus.PREPARED;
	}

	public void markSubmitted(String externalReference) {
		requireStatus(TransferConnectorJobStatus.PREPARED);
		this.status = TransferConnectorJobStatus.SUBMITTED;
		this.externalReference = externalReference;
		this.failureReason = null;
		this.submittedAt = Instant.now();
	}

	public void markFailed(String failureReason) {
		requireStatus(TransferConnectorJobStatus.PREPARED);
		this.status = TransferConnectorJobStatus.FAILED;
		this.failureReason = failureReason;
	}

	/** Rebuilds the payload from current data and puts a FAILED job back to PREPARED. */
	public void reprepare(String payload) {
		requireStatus(TransferConnectorJobStatus.FAILED);
		this.payload = payload;
		this.status = TransferConnectorJobStatus.PREPARED;
		this.failureReason = null;
	}

	public void cancel() {
		if (status == TransferConnectorJobStatus.SUBMITTED || status == TransferConnectorJobStatus.CANCELLED) {
			throw new IllegalStateException("A " + status + " connector job cannot be cancelled");
		}
		this.status = TransferConnectorJobStatus.CANCELLED;
	}

	private void requireStatus(TransferConnectorJobStatus expected) {
		if (status != expected) {
			throw new IllegalStateException("Connector job is " + status + ", expected " + expected);
		}
	}
}
