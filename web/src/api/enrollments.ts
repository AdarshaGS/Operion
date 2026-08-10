import { api } from "./client";

export interface StudentEnrollmentResponse {
	id: number;
	studentId: number;
	academicYearId: number;
	sectionId: number;
	rollNumber: number | null;
	enrollmentStatus: string;
	current: boolean;
	enrolledDate: string;
	exitDate: string | null;
}

export function listSectionEnrollments(sectionId: number): Promise<StudentEnrollmentResponse[]> {
	return api.get<StudentEnrollmentResponse[]>(`/api/v1/sections/${sectionId}/enrollments`);
}

export function listStudentEnrollments(studentId: number): Promise<StudentEnrollmentResponse[]> {
	return api.get<StudentEnrollmentResponse[]>(`/api/v1/students/${studentId}/enrollments`);
}
