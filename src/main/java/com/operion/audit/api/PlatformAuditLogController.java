package com.operion.audit.api;

import java.util.List;

import com.operion.audit.AuditLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-org activity feed for the platform-admin dashboard - AuditLogController's
 * equivalent is tenant-scoped (requires a tenant JWT + ORGANISATION_MANAGE, filtered by
 * TenantContext), so it's unreachable from the platform-admin token. Mounted under
 * /api/v1/platform/** so PlatformAuthenticationInterceptor gates it the same as every
 * other platform-plane controller (see PlatformOrganisationController) - no
 * @RequirePermission, since this plane has no granular permission catalog yet
 * (PlatformPermissions).
 */
@RestController
public class PlatformAuditLogController {

	/** The entity types BillingService/OrganisationService actually audit-log today -
	 * everything else in audit_logs is tenant-module noise (attendance, reporting, ...)
	 * that has no business being on a platform-wide feed. */
	private static final List<String> PLATFORM_ENTITY_TYPES =
			List.of("Organisation", "Plan", "Subscription", "PlatformInvoice", "PlatformSetting");

	private final AuditLogRepository auditLogRepository;

	public PlatformAuditLogController(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@GetMapping("/api/v1/platform/activity")
	public List<AuditLogResponse> recent() {
		return auditLogRepository.findTop50ByEntityTypeInOrderByOccurredAtDesc(PLATFORM_ENTITY_TYPES).stream()
				.map(AuditLogResponse::from).toList();
	}
}
