package com.operion.audit.api;

import com.operion.audit.AuditLogRepository;
import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.common.api.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read side of the audit ledger - AuditLog is deliberately not @TenantId-scoped (see its
 * own javadoc), so this explicitly filters by TenantContext.getOrganisationId() itself,
 * the same pattern every other reader of this table has to follow.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequirePermission("ORGANISATION_MANAGE")
public class AuditLogController {

	private final AuditLogRepository auditLogRepository;

	public AuditLogController(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@GetMapping
	public PageResponse<AuditLogResponse> list(@PageableDefault(size = 50, sort = "occurredAt") Pageable pageable) {
		return PageResponse.from(auditLogRepository.findByOrganisationId(TenantContext.getOrganisationId(), pageable)
				.map(AuditLogResponse::from));
	}
}
