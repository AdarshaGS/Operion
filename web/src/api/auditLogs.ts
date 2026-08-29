import { api, type PageResponse } from "./client";

export interface AuditLogResponse {
	id: number;
	actorUserId: number | null;
	entityType: string;
	entityId: number;
	action: string;
	occurredAt: string;
}

export interface AuditLogFilters {
	entityType?: string | null;
	actorUserId?: number | null;
	from?: string | null;
	to?: string | null;
	page?: number;
	size?: number;
}

function buildQuery(filters: AuditLogFilters): string {
	const params = new URLSearchParams();
	if (filters.entityType) params.set("entityType", filters.entityType);
	if (filters.actorUserId != null) params.set("actorUserId", String(filters.actorUserId));
	if (filters.from) params.set("from", filters.from);
	if (filters.to) params.set("to", filters.to);
	if (filters.page != null) params.set("page", String(filters.page));
	if (filters.size != null) params.set("size", String(filters.size));
	const query = params.toString();
	return query ? `?${query}` : "";
}

export function getAuditLogs(filters: AuditLogFilters): Promise<PageResponse<AuditLogResponse>> {
	return api.get<PageResponse<AuditLogResponse>>(`/api/v1/audit-logs${buildQuery(filters)}`);
}

export function getAuditLogEntityTypes(): Promise<string[]> {
	return api.get<string[]>("/api/v1/audit-logs/entity-types");
}
