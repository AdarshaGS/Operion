import { api } from "./client";

export interface AttendanceClassRow {
	sectionId: number;
	schoolClassId: number;
	className: string;
	sectionName: string;
	classLabel: string;
	teacher: string | null;
	enrolled: number;
	present: number;
	absent: number;
	late: number;
	marked: number;
	ratePercent: number;
	status: "RECORDED" | "PENDING";
	registerStatus: "DRAFT" | "SUBMITTED" | "LOCKED" | null;
}

export interface AttendanceActivity {
	kind: "RECORDED" | "PENDING" | "STAFF";
	title: string;
	detail: string;
	at: string | null;
}

export interface AttendanceOverviewResponse {
	date: string;
	studentsEnrolled: number;
	present: number;
	absent: number;
	late: number;
	classesNotRecorded: number;
	classesTotal: number;
	classes: AttendanceClassRow[];
	activity: AttendanceActivity[];
}

export function getAttendanceOverview(date: string): Promise<AttendanceOverviewResponse> {
	return api.get<AttendanceOverviewResponse>(`/api/v1/attendance/overview?date=${date}`);
}
