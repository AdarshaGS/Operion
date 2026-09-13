import { platformApi } from "./platformClient";

export interface PlatformSettingsResponse {
	trialDays: number;
}

export function getPlatformSettings(): Promise<PlatformSettingsResponse> {
	return platformApi.get<PlatformSettingsResponse>("/api/v1/platform/settings");
}

export function updatePlatformSettings(trialDays: number): Promise<PlatformSettingsResponse> {
	return platformApi.put<PlatformSettingsResponse>("/api/v1/platform/settings", { trialDays });
}
