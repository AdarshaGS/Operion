import { api } from "./client";

export interface SectionOption {
	sectionId: number;
	classId: number;
	className: string;
	sectionName: string;
	writableSubjectIds: number[];
}

export interface SubjectOption {
	id: number;
	name: string;
}

export interface LearningScope {
	manager: boolean;
	/** Holds LESSON_PLAN_REVIEW. */
	reviewer: boolean;
	sections: SectionOption[];
	subjects: SubjectOption[];
}

/** What the caller may pick in learning forms - all sections for admins, own assignments for teachers. */
export function getLearningScope(): Promise<LearningScope> {
	return api.get<LearningScope>("/api/v1/learning/scope");
}

// --- Homework (#226) ---

export interface HomeworkResponse {
	id: number;
	sectionId: number;
	className: string;
	sectionName: string;
	subjectId: number;
	subjectName: string;
	title: string;
	description: string | null;
	dueDate: string;
	attachmentRef: string | null;
	attachmentUrl: string | null;
	status: "ACTIVE" | "CANCELLED";
	assignedByName: string;
	enrolled: number;
	submitted: number;
	reviewed: number;
}

export interface CreateHomeworkRequest {
	sectionId: number;
	subjectId: number;
	title: string;
	description?: string | null;
	dueDate: string;
	attachmentRef?: string | null;
}

export interface HomeworkTrackingRow {
	studentId: number;
	studentName: string;
	rollNumber: number | null;
	submissionId: number | null;
	status: "NOT_SUBMITTED" | "SUBMITTED" | "REVIEWED";
	late: boolean;
	submittedAt: string | null;
	submissionText: string | null;
	attachmentUrl: string | null;
	feedback: string | null;
}

export function listHomework(sectionId?: number): Promise<HomeworkResponse[]> {
	return api.get<HomeworkResponse[]>(`/api/v1/learning/homework${sectionId ? `?sectionId=${sectionId}` : ""}`);
}

export function createHomework(request: CreateHomeworkRequest): Promise<HomeworkResponse> {
	return api.post<HomeworkResponse>("/api/v1/learning/homework", request);
}

export function cancelHomework(id: number): Promise<HomeworkResponse> {
	return api.post<HomeworkResponse>(`/api/v1/learning/homework/${id}/cancel`);
}

export function getHomeworkSubmissions(id: number): Promise<HomeworkTrackingRow[]> {
	return api.get<HomeworkTrackingRow[]>(`/api/v1/learning/homework/${id}/submissions`);
}

export function reviewHomeworkSubmission(submissionId: number, feedback: string): Promise<{ submissionId: number; status: string; feedback: string | null }> {
	return api.post(`/api/v1/learning/homework/submissions/${submissionId}/review`, { feedback });
}

// --- Guardian side, acting for a linked child ---

export interface ChildHomework {
	id: number;
	subjectName: string;
	title: string;
	description: string | null;
	dueDate: string;
	attachmentUrl: string | null;
	assignedByName: string;
	submissionStatus: "NOT_SUBMITTED" | "SUBMITTED" | "REVIEWED";
	late: boolean;
	submittedAt: string | null;
	submissionText: string | null;
	submissionAttachmentUrl: string | null;
	feedback: string | null;
}

export function getChildHomework(studentId: number): Promise<ChildHomework[]> {
	return api.get<ChildHomework[]>(`/api/v1/me/children/${studentId}/homework`);
}

export function submitChildHomework(studentId: number, homeworkId: number, text: string, attachmentRef: string | null): Promise<ChildHomework> {
	return api.post<ChildHomework>(`/api/v1/me/children/${studentId}/homework/${homeworkId}/submission`, { text, attachmentRef });
}

// --- Learning materials (#228) ---

export type MaterialKind = "FILE" | "LINK" | "NOTE";

export interface MaterialResponse {
	id: number;
	classId: number;
	className: string;
	subjectId: number | null;
	subjectName: string | null;
	title: string;
	description: string | null;
	kind: MaterialKind;
	fileUrl: string | null;
	url: string | null;
	uploadedByName: string;
	status: "ACTIVE" | "ARCHIVED";
}

export interface CreateMaterialRequest {
	classId: number;
	subjectId?: number | null;
	title: string;
	description?: string | null;
	kind: MaterialKind;
	attachmentRef?: string | null;
	url?: string | null;
}

export function listMaterials(classId?: number): Promise<MaterialResponse[]> {
	return api.get<MaterialResponse[]>(`/api/v1/learning/materials${classId ? `?classId=${classId}` : ""}`);
}

export function createMaterial(request: CreateMaterialRequest): Promise<MaterialResponse> {
	return api.post<MaterialResponse>("/api/v1/learning/materials", request);
}

export function archiveMaterial(id: number): Promise<MaterialResponse> {
	return api.post<MaterialResponse>(`/api/v1/learning/materials/${id}/archive`);
}

export function getChildMaterials(studentId: number): Promise<MaterialResponse[]> {
	return api.get<MaterialResponse[]>(`/api/v1/me/children/${studentId}/materials`);
}

// --- Lesson planner (#229) ---

export type LessonPlanStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED";

export interface LessonPlanResponse {
	id: number;
	sectionId: number;
	className: string;
	sectionName: string;
	subjectId: number;
	subjectName: string;
	planDate: string;
	title: string;
	objectives: string | null;
	activities: string | null;
	resources: string | null;
	status: LessonPlanStatus;
	authorName: string;
	submittedAt: string | null;
	reviewNote: string | null;
	reviewedByName: string | null;
	reviewedAt: string | null;
}

export interface LessonPlanRequest {
	sectionId?: number;
	subjectId?: number;
	planDate: string;
	title: string;
	objectives?: string | null;
	activities?: string | null;
	resources?: string | null;
}

export function listLessonPlans(status?: LessonPlanStatus): Promise<LessonPlanResponse[]> {
	return api.get<LessonPlanResponse[]>(`/api/v1/learning/lesson-plans${status ? `?status=${status}` : ""}`);
}

export function getLessonPlanCalendar(from: string, to: string): Promise<LessonPlanResponse[]> {
	return api.get<LessonPlanResponse[]>(`/api/v1/learning/lesson-plans/calendar?from=${from}&to=${to}`);
}

export function createLessonPlan(request: LessonPlanRequest): Promise<LessonPlanResponse> {
	return api.post<LessonPlanResponse>("/api/v1/learning/lesson-plans", request);
}

export function editLessonPlan(id: number, request: LessonPlanRequest): Promise<LessonPlanResponse> {
	return api.put<LessonPlanResponse>(`/api/v1/learning/lesson-plans/${id}`, request);
}

export function submitLessonPlan(id: number): Promise<LessonPlanResponse> {
	return api.post<LessonPlanResponse>(`/api/v1/learning/lesson-plans/${id}/submit`);
}

export function approveLessonPlan(id: number, feedback: string): Promise<LessonPlanResponse> {
	return api.post<LessonPlanResponse>(`/api/v1/learning/lesson-plans/${id}/approve`, { feedback });
}

export function rejectLessonPlan(id: number, feedback: string): Promise<LessonPlanResponse> {
	return api.post<LessonPlanResponse>(`/api/v1/learning/lesson-plans/${id}/reject`, { feedback });
}

export function getChildLessonPlans(studentId: number, from: string, to: string): Promise<LessonPlanResponse[]> {
	return api.get<LessonPlanResponse[]>(`/api/v1/me/children/${studentId}/lesson-plans?from=${from}&to=${to}`);
}

// --- Quizzes (#227) ---

export type QuizAvailability = "UPCOMING" | "OPEN" | "CLOSED";

export interface QuizResponse {
	id: number;
	sectionId: number;
	className: string;
	sectionName: string;
	subjectId: number;
	subjectName: string;
	title: string;
	instructions: string | null;
	opensAt: string;
	closesAt: string;
	status: "DRAFT" | "PUBLISHED";
	availability: QuizAvailability;
	questionCount: number;
	totalMarks: number;
	attempts: number;
	enrolled: number;
}

export interface CreateQuizRequest {
	sectionId: number;
	subjectId: number;
	title: string;
	instructions?: string | null;
	opensAt: string;
	closesAt: string;
}

export interface OptionInput {
	text: string;
	correct: boolean;
}

export interface QuestionRequest {
	subjectId?: number;
	text: string;
	marks: number;
	options: OptionInput[];
}

export interface QuestionResponse {
	id: number;
	subjectId: number;
	ordinal: number;
	text: string;
	marks: number;
	options: { id: number; text: string; correct: boolean }[];
}

export interface QuizResultRow {
	studentId: number;
	studentName: string;
	rollNumber: number | null;
	attempted: boolean;
	score: number | null;
	maxScore: number | null;
	percentage: number | null;
	submittedAt: string | null;
}

export function listQuizzes(sectionId?: number): Promise<QuizResponse[]> {
	return api.get<QuizResponse[]>(`/api/v1/learning/quizzes${sectionId ? `?sectionId=${sectionId}` : ""}`);
}

export function createQuiz(request: CreateQuizRequest): Promise<QuizResponse> {
	return api.post<QuizResponse>("/api/v1/learning/quizzes", request);
}

export function getQuizQuestions(quizId: number): Promise<QuestionResponse[]> {
	return api.get<QuestionResponse[]>(`/api/v1/learning/quizzes/${quizId}/questions`);
}

export function addQuizQuestion(quizId: number, request: QuestionRequest): Promise<QuestionResponse> {
	return api.post<QuestionResponse>(`/api/v1/learning/quizzes/${quizId}/questions`, request);
}

export function addQuizQuestionFromBank(quizId: number, bankQuestionId: number): Promise<QuestionResponse> {
	return api.post<QuestionResponse>(`/api/v1/learning/quizzes/${quizId}/questions/from-bank/${bankQuestionId}`);
}

export function removeQuizQuestion(quizId: number, questionId: number): Promise<void> {
	return api.delete<void>(`/api/v1/learning/quizzes/${quizId}/questions/${questionId}`);
}

export function publishQuiz(quizId: number): Promise<QuizResponse> {
	return api.post<QuizResponse>(`/api/v1/learning/quizzes/${quizId}/publish`);
}

export function getQuizResults(quizId: number): Promise<QuizResultRow[]> {
	return api.get<QuizResultRow[]>(`/api/v1/learning/quizzes/${quizId}/results`);
}

export function listQuestionBank(subjectId?: number): Promise<QuestionResponse[]> {
	return api.get<QuestionResponse[]>(`/api/v1/learning/question-bank${subjectId ? `?subjectId=${subjectId}` : ""}`);
}

export function addBankQuestion(request: QuestionRequest): Promise<QuestionResponse> {
	return api.post<QuestionResponse>("/api/v1/learning/question-bank", request);
}

export function deleteBankQuestion(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/learning/question-bank/${id}`);
}

export interface ChildQuiz {
	id: number;
	subjectName: string;
	title: string;
	instructions: string | null;
	opensAt: string;
	closesAt: string;
	availability: QuizAvailability;
	questionCount: number;
	totalMarks: number;
	attempted: boolean;
	score: number | null;
	maxScore: number | null;
}

export interface AttemptQuestion {
	id: number;
	ordinal: number;
	text: string;
	marks: number;
	options: { id: number; text: string }[];
}

export interface AttemptSummary {
	quizId: number;
	quizTitle: string;
	subjectName: string;
	score: number;
	maxScore: number;
	percentage: number;
	submittedAt: string;
}

export interface AttemptResult {
	summary: AttemptSummary;
	correctAnswersShown: boolean;
	answers: {
		questionId: number;
		text: string;
		marks: number;
		options: { id: number; text: string }[];
		selectedOptionId: number | null;
		correctOptionId: number | null;
		correct: boolean;
		marksAwarded: number;
	}[];
}

export function getChildQuizzes(studentId: number): Promise<ChildQuiz[]> {
	return api.get<ChildQuiz[]>(`/api/v1/me/children/${studentId}/quizzes`);
}

export function getChildQuizQuestions(studentId: number, quizId: number): Promise<AttemptQuestion[]> {
	return api.get<AttemptQuestion[]>(`/api/v1/me/children/${studentId}/quizzes/${quizId}/questions`);
}

export function submitChildQuizAttempt(studentId: number, quizId: number, answers: { questionId: number; optionId: number | null }[]): Promise<AttemptSummary> {
	return api.post<AttemptSummary>(`/api/v1/me/children/${studentId}/quizzes/${quizId}/attempt`, { answers });
}

export function getChildQuizResult(studentId: number, quizId: number): Promise<AttemptResult> {
	return api.get<AttemptResult>(`/api/v1/me/children/${studentId}/quizzes/${quizId}/result`);
}

export function getChildQuizHistory(studentId: number): Promise<AttemptSummary[]> {
	return api.get<AttemptSummary[]>(`/api/v1/me/children/${studentId}/quiz-history`);
}
