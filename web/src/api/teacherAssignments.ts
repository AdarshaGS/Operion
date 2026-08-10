import { api } from "./client";

export interface AssignTeacherRequest {
	subjectId?: number | null;
	teacherPersonId: number;
	assignmentType: string;
	startDate: string;
}

export interface TeacherAssignmentResponse {
	id: number;
	sectionId: number;
	subjectId: number | null;
	teacherPersonId: number;
	assignmentType: string;
	startDate: string;
	endDate: string | null;
	status: string;
}

export function assignTeacher(sectionId: number, request: AssignTeacherRequest): Promise<TeacherAssignmentResponse> {
	return api.post<TeacherAssignmentResponse>(`/api/v1/sections/${sectionId}/teacher-assignments`, request);
}

export function listTeacherAssignmentsForSection(sectionId: number): Promise<TeacherAssignmentResponse[]> {
	return api.get<TeacherAssignmentResponse[]>(`/api/v1/sections/${sectionId}/teacher-assignments`);
}

export function endTeacherAssignment(id: number, endDate: string): Promise<TeacherAssignmentResponse> {
	return api.post<TeacherAssignmentResponse>(`/api/v1/teacher-assignments/${id}/end`, { endDate });
}
