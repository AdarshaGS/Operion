import { api } from "./client";

export interface MarkEntry {
	studentEnrollmentId: number;
	marksObtained: number | null;
	absent: boolean;
	remarks?: string | null;
}

export interface MarksEntryResponse {
	id: number;
	examScheduleId: number;
	studentEnrollmentId: number;
	marksObtained: number | null;
	absent: boolean;
	remarks: string | null;
}

export function enterMarks(scheduleId: number, marks: MarkEntry[]): Promise<MarksEntryResponse[]> {
	return api.post<MarksEntryResponse[]>(`/api/v1/examinations/schedules/${scheduleId}/marks`, { marks });
}

export function listMarks(scheduleId: number): Promise<MarksEntryResponse[]> {
	return api.get<MarksEntryResponse[]>(`/api/v1/examinations/schedules/${scheduleId}/marks`);
}

export function correctMarks(marksEntryId: number, marksObtained: number | null, absent: boolean, remarks: string): Promise<MarksEntryResponse> {
	return api.patch<MarksEntryResponse>(`/api/v1/examinations/marks/${marksEntryId}`, { marksObtained, absent, remarks });
}
