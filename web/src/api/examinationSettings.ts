import { api } from "./client";

export type PassFailStrategy = "PASS_EVERY_SUBJECT" | "MINIMUM_AGGREGATE_PERCENTAGE" | "BOTH";

export interface ExaminationSettingsResponse {
	rankingEnabled: boolean;
	passFailStrategy: PassFailStrategy;
	minimumAggregatePercentage: number;
}

export function getExaminationSettings(): Promise<ExaminationSettingsResponse> {
	return api.get<ExaminationSettingsResponse>("/api/v1/examinations/settings");
}

export function updateExaminationSettings(request: ExaminationSettingsResponse): Promise<ExaminationSettingsResponse> {
	return api.put<ExaminationSettingsResponse>("/api/v1/examinations/settings", request);
}
