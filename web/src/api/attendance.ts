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
	markedBy: number | null;
	markedAt: string;
	correctedBy: number | null;
	correctedAt: string | null;
}

export interface AttendanceCorrectionResponse {
	id: number;
	previousStatus: string;
	newStatus: string;
	reason: string;
	correctedBy: number | null;
	correctedAt: string;
}

export interface MonthlyAttendanceSummaryResponse {
	totalMarkedDays: number;
	presentCount: number;
	absentCount: number;
	lateCount: number;
	halfDayCount: number;
	leaveCount: number;
	percentage: number;
}

export interface AttendanceRegisterResponse {
	register: ClassAttendanceRegisterResponse;
	entries: StudentAttendanceResponse[];
}

/** Which sections the caller may mark: ALL, OWN (homeroom sections in sectionIds) or NONE. */
export interface MarkableSectionsResponse {
	scope: "ALL" | "OWN" | "NONE";
	sectionIds: number[];
	schoolClassIds: number[];
}

export function getMarkableSections(): Promise<MarkableSectionsResponse> {
	return api.get<MarkableSectionsResponse>("/api/v1/attendance/markable-sections");
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

export function unlockRegister(registerId: number): Promise<AttendanceRegisterResponse> {
	return api.post<AttendanceRegisterResponse>(`/api/v1/attendance/register/${registerId}/unlock`);
}

export function correctAttendance(attendanceId: number, newStatus: string, reason: string): Promise<StudentAttendanceResponse> {
	return api.patch<StudentAttendanceResponse>(`/api/v1/attendance/students/${attendanceId}`, { newStatus, reason });
}

export function getCorrections(attendanceId: number): Promise<AttendanceCorrectionResponse[]> {
	return api.get<AttendanceCorrectionResponse[]>(`/api/v1/attendance/students/${attendanceId}/corrections`);
}

export function getMonthlySummary(enrollmentId: number, year: number, month: number): Promise<MonthlyAttendanceSummaryResponse> {
	return api.get<MonthlyAttendanceSummaryResponse>(
		`/api/v1/attendance/enrollments/${enrollmentId}/summary?year=${year}&month=${month}`,
	);
}

export interface StaffAttendanceResponse {
	id: number;
	personId: number;
	campusId: number;
	attendanceDate: string;
	attendanceStatus: string;
	checkInTime: string | null;
	checkOutTime: string | null;
	remarks: string | null;
}

export interface CheckInStaffRequest {
	personId: number;
	campusId: number;
	attendanceDate: string;
	status: string;
	checkInTime: string | null;
	remarks: string | null;
}

export function listStaffAttendance(campusId: number, date: string): Promise<StaffAttendanceResponse[]> {
	return api.get<StaffAttendanceResponse[]>(`/api/v1/attendance/staff?campusId=${campusId}&date=${date}`);
}

export function markStaffAttendance(request: CheckInStaffRequest): Promise<StaffAttendanceResponse> {
	return api.post<StaffAttendanceResponse>("/api/v1/attendance/staff", request);
}

export function checkOutStaff(attendanceId: number, checkOutTime: string): Promise<StaffAttendanceResponse> {
	return api.patch<StaffAttendanceResponse>(`/api/v1/attendance/staff/${attendanceId}/check-out`, { checkOutTime });
}
