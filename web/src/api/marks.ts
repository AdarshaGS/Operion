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
	passed: boolean;
	rank: number | null;
	published: boolean;
	enteredBy: number | null;
	enteredAt: string;
	correctedBy: number | null;
	correctedAt: string | null;
}

export interface MarksEntryRegisterResponse {
	id: number | null;
	examScheduleId: number;
	registerStatus: "DRAFT" | "SUBMITTED" | "APPROVED";
	approvedBy: number | null;
	approvedAt: string | null;
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

export function correctMarksAfterPublish(
	marksEntryId: number, marksObtained: number | null, absent: boolean, remarks: string,
): Promise<MarksEntryResponse> {
	return api.patch<MarksEntryResponse>(`/api/v1/examinations/marks/${marksEntryId}/correct-after-publish`, { marksObtained, absent, remarks });
}

export function getRegister(scheduleId: number): Promise<MarksEntryRegisterResponse> {
	return api.get<MarksEntryRegisterResponse>(`/api/v1/examinations/schedules/${scheduleId}/register`);
}

export function submitRegister(scheduleId: number): Promise<MarksEntryRegisterResponse> {
	return api.post<MarksEntryRegisterResponse>(`/api/v1/examinations/schedules/${scheduleId}/submit`);
}

export function approveRegister(scheduleId: number): Promise<MarksEntryRegisterResponse> {
	return api.post<MarksEntryRegisterResponse>(`/api/v1/examinations/schedules/${scheduleId}/approve`);
}
