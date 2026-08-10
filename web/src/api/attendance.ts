import { api, ApiError } from "./client";

export interface StudentMarkEntry {
	studentEnrollmentId: number;
	status: string;
	excused: boolean;
	remarks: string | null;
}

export interface MarkAttendanceRequest {
	attendanceDate: string;
	marks: StudentMarkEntry[];
}

export interface ClassAttendanceRegisterResponse {
	id: number;
	academicYearId: number;
	sectionId: number;
	attendanceDate: string;
	registerStatus: string;
}

export interface StudentAttendanceResponse {
	id: number;
	studentEnrollmentId: number;
	academicYearId: number;
	schoolClassId: number;
	sectionId: number;
	attendanceDate: string;
	attendanceStatus: string;
	excused: boolean;
	remarks: string | null;
}

export interface AttendanceRegisterResponse {
	register: ClassAttendanceRegisterResponse;
	entries: StudentAttendanceResponse[];
}

export function markAttendance(sectionId: number, request: MarkAttendanceRequest): Promise<AttendanceRegisterResponse> {
	return api.post<AttendanceRegisterResponse>(`/api/v1/attendance/sections/${sectionId}/register`, request);
}

/** Returns null (rather than letting the 404 propagate) when no register exists yet for
 * that section+date - the caller uses that to switch into "fresh marking" mode. */
export async function getRegister(sectionId: number, date: string): Promise<AttendanceRegisterResponse | null> {
	try {
		return await api.get<AttendanceRegisterResponse>(`/api/v1/attendance/sections/${sectionId}/register?date=${date}`);
	} catch (err) {
		if (err instanceof ApiError && err.status === 404) return null;
		throw err;
	}
}

export function submitRegister(registerId: number): Promise<AttendanceRegisterResponse> {
	return api.post<AttendanceRegisterResponse>(`/api/v1/attendance/register/${registerId}/submit`);
}

export function lockRegister(registerId: number): Promise<AttendanceRegisterResponse> {
	return api.post<AttendanceRegisterResponse>(`/api/v1/attendance/register/${registerId}/lock`);
}

export function correctAttendance(attendanceId: number, newStatus: string, reason: string): Promise<StudentAttendanceResponse> {
	return api.patch<StudentAttendanceResponse>(`/api/v1/attendance/students/${attendanceId}`, { newStatus, reason });
}
