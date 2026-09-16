import { api } from "./client";

export interface AcademicConfigurationResponse {
	schoolStartTime: string | null;
	schoolEndTime: string | null;
}

export interface UpdateAcademicConfigurationRequest {
	schoolStartTime: string | null;
	schoolEndTime: string | null;
}

export function getAcademicConfiguration(): Promise<AcademicConfigurationResponse> {
	return api.get<AcademicConfigurationResponse>("/api/v1/academic/settings");
}

export function updateAcademicConfiguration(
	request: UpdateAcademicConfigurationRequest,
): Promise<AcademicConfigurationResponse> {
	return api.patch<AcademicConfigurationResponse>("/api/v1/academic/settings", request);
}

export type WorkingCalendarEntryType = "HOLIDAY" | "SPECIAL_WORKING_DAY";

export interface WorkingCalendarEntryResponse {
	id: number;
	academicYearId: number;
	campusId: number | null;
	date: string;
	label: string;
	type: WorkingCalendarEntryType;
}

export interface CreateWorkingCalendarEntryRequest {
	academicYearId: number;
	campusId: number | null;
	date: string;
	label: string;
	type: WorkingCalendarEntryType;
}

export function listWorkingCalendarEntries(academicYearId: number): Promise<WorkingCalendarEntryResponse[]> {
	return api.get<WorkingCalendarEntryResponse[]>(`/api/v1/academic/working-calendar?academicYearId=${academicYearId}`);
}

export function createWorkingCalendarEntry(
	request: CreateWorkingCalendarEntryRequest,
): Promise<WorkingCalendarEntryResponse> {
	return api.post<WorkingCalendarEntryResponse>("/api/v1/academic/working-calendar", request);
}

export function deleteWorkingCalendarEntry(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/academic/working-calendar/${id}`);
}
