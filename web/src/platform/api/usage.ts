import { platformApi } from "./platformClient";

export interface UsageResponse {
	organisationId: number;
	activeStudentCount: number;
}

export function listUsage(): Promise<UsageResponse[]> {
	return platformApi.get<UsageResponse[]>("/api/v1/platform/usage");
}
