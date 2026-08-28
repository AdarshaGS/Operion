import { api } from "./client";

export interface AcademicSetupStatusResponse {
	configured: boolean;
}

export function getAcademicSetupStatus(): Promise<AcademicSetupStatusResponse> {
	return api.get<AcademicSetupStatusResponse>("/api/v1/academics/setup-status");
}
