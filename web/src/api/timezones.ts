import { api } from "./client";

export interface TimezoneResponse {
	id: number;
	name: string;
	region: string;
}

export function listTimezones(): Promise<TimezoneResponse[]> {
	return api.get<TimezoneResponse[]>("/api/v1/timezones");
}
