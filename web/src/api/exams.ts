import { api } from "./client";

export interface CreateExamRequest {
	academicYearId: number;
	name: string;
	examType: string;
}

export interface ExamResponse {
	id: number;
	academicYearId: number;
	name: string;
	examType: string;
	status: string;
}

export function createExam(request: CreateExamRequest): Promise<ExamResponse> {
	return api.post<ExamResponse>("/api/v1/examinations/exams", request);
}

export function listExams(academicYearId: number): Promise<ExamResponse[]> {
	return api.get<ExamResponse[]>(`/api/v1/examinations/exams?academicYearId=${academicYearId}`);
}

export function getExam(examId: number): Promise<ExamResponse> {
	return api.get<ExamResponse>(`/api/v1/examinations/exams/${examId}`);
}

export interface CreateExamScheduleRequest {
	schoolClassId: number;
	sectionId?: number | null;
	subjectId: number;
	examDate: string;
	maxMarks: number;
	passMarks: number;
}

export interface ExamScheduleResponse {
	id: number;
	examId: number;
	schoolClassId: number;
	sectionId: number | null;
	subjectId: number;
	examDate: string;
	maxMarks: number;
	passMarks: number;
}

export function addSchedule(examId: number, request: CreateExamScheduleRequest): Promise<ExamScheduleResponse> {
	return api.post<ExamScheduleResponse>(`/api/v1/examinations/exams/${examId}/schedules`, request);
}

export function listSchedules(examId: number): Promise<ExamScheduleResponse[]> {
	return api.get<ExamScheduleResponse[]>(`/api/v1/examinations/exams/${examId}/schedules`);
}
