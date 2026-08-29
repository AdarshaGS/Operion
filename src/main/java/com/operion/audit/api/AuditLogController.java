package com.operion.audit.api;

import java.time.Instant;
import java.util.List;

import com.operion.audit.AuditLogRepository;
import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.common.api.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
	public PageResponse<AuditLogResponse> list(@RequestParam(required = false) String entityType,
			@RequestParam(required = false) Long actorUserId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(auditLogRepository
				.search(TenantContext.getOrganisationId(), entityType, actorUserId, from, to, pageable)
				.map(AuditLogResponse::from));
	}

	@GetMapping("/entity-types")
	public List<String> entityTypes() {
		return auditLogRepository.findDistinctEntityTypes(TenantContext.getOrganisationId());
	}
}
