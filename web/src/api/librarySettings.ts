import { api } from "./client";

export interface LibrarySettingsResponse {
	maxLoansStudent: number;
	maxLoansStaff: number;
	borrowingPeriodDaysStudent: number;
	borrowingPeriodDaysStaff: number;
	blockIssueOnOverdue: boolean;
}

export type UpdateLibrarySettingsRequest = LibrarySettingsResponse;

export function getLibrarySettings(): Promise<LibrarySettingsResponse> {
	return api.get<LibrarySettingsResponse>("/api/v1/library/settings");
}

export function updateLibrarySettings(request: UpdateLibrarySettingsRequest): Promise<LibrarySettingsResponse> {
	return api.put<LibrarySettingsResponse>("/api/v1/library/settings", request);
}
