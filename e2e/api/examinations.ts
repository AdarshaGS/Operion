// Exam + schedule seeding for tests/flows/teacher-flow.spec.ts's marks-entry step. Exam
// *creation* through the real UI is already covered by tests/tenant/examinations/create-exam.spec.ts
// (owner-only) - this exists purely to give the teacher fixture a schedule to enter marks
// against, without re-driving UI coverage that already exists elsewhere.
import { api } from "./client";

export interface ExamResponse {
	id: number;
	name: string;
	examType: string;
}

export function createExam(token: string, academicYearId: number, name: string, examType: string) {
	return api.post<ExamResponse>("/api/v1/examinations/exams", { academicYearId, name, examType }, token);
}

export interface ExamScheduleResponse {
	id: number;
	examId: number;
	schoolClassId: number;
	subjectId: number;
	maxMarks: number;
	passMarks: number;
}

export function addExamSchedule(
	token: string,
	examId: number,
	input: { schoolClassId: number; subjectId: number; examDate: string; maxMarks: number; passMarks: number },
) {
	return api.post<ExamScheduleResponse>(`/api/v1/examinations/exams/${examId}/schedules`, input, token);
}
