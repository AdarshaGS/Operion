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
