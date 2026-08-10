import { api } from "./client";

export interface ReportCardResponse {
	id: number;
	examId: number;
	studentEnrollmentId: number;
	totalMarksObtained: number;
	totalMaxMarks: number;
	percentage: number;
	overallGrade: string;
}

export function publishReportCard(examId: number, studentEnrollmentId: number, gradingScaleId: number): Promise<ReportCardResponse> {
	return api.post<ReportCardResponse>(`/api/v1/examinations/exams/${examId}/report-cards`, { studentEnrollmentId, gradingScaleId });
}

export function listReportCards(studentEnrollmentId: number): Promise<ReportCardResponse[]> {
	return api.get<ReportCardResponse[]>(`/api/v1/examinations/report-cards?studentEnrollmentId=${studentEnrollmentId}`);
}
