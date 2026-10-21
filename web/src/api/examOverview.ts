import { api } from "./client";

export type ResultStatus = "PUBLISHED" | "IN_PROGRESS" | "NOT_STARTED" | "DRAFT";

export interface ExamOverviewRow {
	id: number;
	name: string;
	examType: string;
	status: string;
	classes: string;
	startDate: string | null;
	endDate: string | null;
	scheduleCount: number;
	marksEntryPercent: number;
	resultStatus: ResultStatus;
}

export interface ExamOverviewResponse {
	totalExams: number;
	upcomingExams: number;
	nextExam: { examId: number; name: string; startDate: string } | null;
	marksPendingSubjects: number;
	marksPendingClasses: number;
	resultsPublishedExams: number;
	lastPublished: { examId: number; name: string; endDate: string | null } | null;
	exams: ExamOverviewRow[];
	upcoming: { examId: number; scheduleId: number; examName: string; examDate: string; classLabel: string; subjectName: string }[];
	marksByClass: { schoolClassId: number; classLabel: string; percent: number }[];
	resultSummary: { passRate: number | null; reportCards: number; grades: { grade: string; count: number }[] };
}

export function getExamOverview(academicYearId: number): Promise<ExamOverviewResponse> {
	return api.get<ExamOverviewResponse>(`/api/v1/examinations/overview?academicYearId=${academicYearId}`);
}
