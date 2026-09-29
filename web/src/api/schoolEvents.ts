import { api } from "./client";

export interface SchoolEventResponse {
	id: number;
	campusId: number | null;
	date: string;
	title: string;
	description: string | null;
}

export interface CreateSchoolEventRequest {
	campusId: number | null;
	date: string;
	title: string;
	description: string | null;
}

export function listSchoolEvents(from: string, to: string): Promise<SchoolEventResponse[]> {
	return api.get<SchoolEventResponse[]>(`/api/v1/academic/school-events?from=${from}&to=${to}`);
}

export function createSchoolEvent(request: CreateSchoolEventRequest): Promise<SchoolEventResponse> {
	return api.post<SchoolEventResponse>("/api/v1/academic/school-events", request);
}

export function deleteSchoolEvent(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/academic/school-events/${id}`);
}
