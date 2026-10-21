import { api, downloadFile } from "./client";

export interface ReportCardResponse {
	id: number;
	examId: number;
	studentEnrollmentId: number;
	totalMarksObtained: number;
	totalMaxMarks: number;
	percentage: number;
	overallGrade: string;
	passed: boolean;
	classRank: number | null;
	status: "PUBLISHED" | "SUPERSEDED";
	stale: boolean;
	publishedBy: number | null;
	publishedAt: string;
}

export interface ReportCardSubjectMark {
	subjectName: string;
	maxMarks: number;
	passMarks: number;
	marksObtained: number | null;
	absent: boolean;
	passed: boolean;
	rank: number | null;
}

export interface ReportCardRenderResponse {
	logoRef: string | null;
	stampRef: string | null;
	signatureRef: string | null;
	schoolNameOverride: string | null;
	addressLine: string | null;
	affiliationText: string | null;
	footerText: string | null;
	templateStyle: string;
	pageSize: string;
	fontStyle: string;
	fontSize: number;
	headerSubtext: string | null;
	studentName: string;
	admissionNumber: string;
	className: string;
	sectionName: string;
	examName: string;
	examType: string;
	academicYearName: string;
	subjects: ReportCardSubjectMark[];
	totalMarksObtained: number;
	totalMaxMarks: number;
	percentage: number;
	overallGrade: string;
	passed: boolean;
	classRank: number | null;
	status: "PUBLISHED" | "SUPERSEDED";
	stale: boolean;
	publishedBy: number | null;
	publishedAt: string;
}

export function publishReportCard(examId: number, studentEnrollmentId: number, gradingScaleId: number): Promise<ReportCardResponse> {
	return api.post<ReportCardResponse>(`/api/v1/examinations/exams/${examId}/report-cards`, { studentEnrollmentId, gradingScaleId });
}

export function listReportCards(studentEnrollmentId: number): Promise<ReportCardResponse[]> {
	return api.get<ReportCardResponse[]>(`/api/v1/examinations/report-cards?studentEnrollmentId=${studentEnrollmentId}`);
}

export function renderReportCard(reportCardId: number): Promise<ReportCardRenderResponse> {
	return api.get<ReportCardRenderResponse>(`/api/v1/examinations/report-cards/${reportCardId}/render`);
}

/** One student's report card as a PDF; the first office download freezes it in the issued-document register. */
export function downloadReportCardPdf(reportCardId: number): Promise<void> {
	return downloadFile(`/api/v1/examinations/report-cards/${reportCardId}/report-card.pdf`, `report-card-${reportCardId}.pdf`);
}

/** Every published report card of a section for an exam, one page per student. */
export function downloadSectionReportCardsPdf(examId: number, sectionId: number): Promise<void> {
	return downloadFile(`/api/v1/examinations/exams/${examId}/report-cards.pdf?sectionId=${sectionId}`, `report-cards-${sectionId}.pdf`);
}
