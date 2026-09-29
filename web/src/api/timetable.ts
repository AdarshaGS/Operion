import { api } from "./client";

export interface PeriodRequest {
	ordinal?: number;
	label: string;
	startTime: string;
	endTime: string;
	breakSlot: boolean;
}

export interface PeriodResponse {
	id: number;
	ordinal: number;
	label: string;
	startTime: string;
	endTime: string;
	breakSlot: boolean;
}

export interface TimetableEntryResponse {
	id: number;
	sectionId: number;
	sectionName: string;
	className: string;
	subjectId: number;
	subjectName: string;
	teacherPersonId: number;
	teacherName: string;
	dayOfWeek: string;
	periodId: number;
}

export interface SetTimetableEntryRequest {
	subjectId: number;
	teacherPersonId: number;
	dayOfWeek: string;
	periodId: number;
}

export function listPeriods(): Promise<PeriodResponse[]> {
	return api.get<PeriodResponse[]>("/api/v1/timetable/periods");
}

export function createPeriod(request: PeriodRequest): Promise<PeriodResponse> {
	return api.post<PeriodResponse>("/api/v1/timetable/periods", request);
}

export function updatePeriod(id: number, request: PeriodRequest): Promise<PeriodResponse> {
	return api.put<PeriodResponse>(`/api/v1/timetable/periods/${id}`, request);
}

export function deletePeriod(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/timetable/periods/${id}`);
}

export function listSectionTimetable(sectionId: number): Promise<TimetableEntryResponse[]> {
	return api.get<TimetableEntryResponse[]>(`/api/v1/sections/${sectionId}/timetable`);
}

export function listTeacherTimetable(personId: number, academicYearId?: number): Promise<TimetableEntryResponse[]> {
	const query = academicYearId ? `?academicYearId=${academicYearId}` : "";
	return api.get<TimetableEntryResponse[]>(`/api/v1/teachers/${personId}/timetable${query}`);
}

export function setTimetableEntry(sectionId: number, request: SetTimetableEntryRequest): Promise<TimetableEntryResponse> {
	return api.put<TimetableEntryResponse>(`/api/v1/sections/${sectionId}/timetable`, request);
}

export function removeTimetableEntry(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/timetable-entries/${id}`);
}
