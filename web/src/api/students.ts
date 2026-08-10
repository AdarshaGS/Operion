import { api } from "./client";

export interface CreateStudentRequest {
	personId: number;
	admissionNumber: string;
	admissionDate: string;
	admissionSource?: string | null;
	previousSchool?: string | null;
	tcNumber?: string | null;
	entranceScore?: number | null;
	bloodGroup?: string | null;
	category?: string | null;
	nationality?: string | null;
	remarks?: string | null;
}

export interface StudentResponse {
	id: number;
	personId: number;
	admissionNumber: string;
	admissionDate: string;
	admissionSource: string | null;
	previousSchool: string | null;
	tcNumber: string | null;
	entranceScore: number | null;
	bloodGroup: string | null;
	category: string | null;
	nationality: string | null;
	remarks: string | null;
	status: string;
}

export function admitStudent(request: CreateStudentRequest): Promise<StudentResponse> {
	return api.post<StudentResponse>("/api/v1/students", request);
}

export function listStudents(): Promise<StudentResponse[]> {
	return api.get<StudentResponse[]>("/api/v1/students");
}

export function getStudent(id: number): Promise<StudentResponse> {
	return api.get<StudentResponse>(`/api/v1/students/${id}`);
}
