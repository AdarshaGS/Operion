import { platformApi } from "./platformClient";

export interface ActivityResponse {
	id: number;
	organisationId: number | null;
	actorUserId: number | null;
	entityType: string;
	entityId: number;
	action: string;
	occurredAt: string;
}

export function listRecentActivity(): Promise<ActivityResponse[]> {
	return platformApi.get<ActivityResponse[]>("/api/v1/platform/activity");
}
