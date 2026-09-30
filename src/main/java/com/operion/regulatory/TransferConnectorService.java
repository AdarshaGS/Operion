package com.operion.regulatory;

import java.util.Map;

import com.operion.audit.AuditLogService;
import com.operion.common.TenantContext;
import com.operion.student.StudentExit;
import com.operion.student.StudentExitType;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Prepares and tracks transfer hand-off jobs (#235), built on #35's exit record and #233's
 * export schemas: the payload is a STUDENT schema rendered for the leaving student, so which
 * fields an external system wants is configuration, not code here.
 */
@Service
public class TransferConnectorService {

	private final TransferConnectorJobRepository jobRepository;
	private final RegulatoryExportService exportService;
	private final AuditLogService auditLogService;
	private final ObjectMapper objectMapper;

	public TransferConnectorService(TransferConnectorJobRepository jobRepository, RegulatoryExportService exportService,
			AuditLogService auditLogService, ObjectMapper objectMapper) {
		this.jobRepository = jobRepository;
		this.exportService = exportService;
		this.auditLogService = auditLogService;
		this.objectMapper = objectMapper;
	}

	public TransferConnectorJob prepare(StudentExit studentExit, RegulatoryExportSchema schema, String connectorName) {
		if (studentExit.getExitType() != StudentExitType.TRANSFER) {
			throw new IllegalStateException("Connector jobs apply to TRANSFER exits only, not " + studentExit.getExitType());
		}
		if (connectorName == null || connectorName.isBlank()) {
			throw new IllegalArgumentException("A connector name is required");
		}
		TransferConnectorJob job = jobRepository.save(new TransferConnectorJob(studentExit, schema, connectorName.trim(),
				buildPayload(studentExit, schema), TenantContext.getActorId()));
		auditLogService.record("TRANSFER_CONNECTOR_JOB", job.getId(), "PREPARED", null, Map.of("connector", job.getConnectorName()));
		return job;
	}

	public TransferConnectorJob markSubmitted(TransferConnectorJob job, String externalReference) {
		job.markSubmitted(externalReference);
		return record(job, "SUBMITTED");
	}

	public TransferConnectorJob markFailed(TransferConnectorJob job, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A failure reason is required");
		}
		job.markFailed(reason.trim());
		return record(job, "FAILED");
	}

	public TransferConnectorJob reprepare(TransferConnectorJob job) {
		job.reprepare(buildPayload(job.getStudentExit(), job.getSchema()));
		return record(job, "REPREPARED");
	}

	public TransferConnectorJob cancel(TransferConnectorJob job) {
		job.cancel();
		return record(job, "CANCELLED");
	}

	private TransferConnectorJob record(TransferConnectorJob job, String action) {
		TransferConnectorJob saved = jobRepository.save(job);
		auditLogService.record("TRANSFER_CONNECTOR_JOB", saved.getId(), action, null, saved.getStatus());
		return saved;
	}

	private String buildPayload(StudentExit studentExit, RegulatoryExportSchema schema) {
		Map<String, String> record = exportService.buildStudentRecord(schema, studentExit.getStudent());
		return objectMapper.writeValueAsString(record);
	}
}
