import { api, ApiError } from "./client";
import { getSession } from "./tokenStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export interface CreateStudentRequest {
	personId: number;
	/** Optional - left null/blank, the backend auto-generates one from the org's numbering format. */
	admissionNumber?: string | null;
	admissionDate: string;
	admissionSource?: string | null;
	previousSchool?: string | null;
	tcNumber?: string | null;
	entranceScore?: number | null;
	bloodGroup?: string | null;
	category?: string | null;
	nationality?: string | null;
	remarks?: string | null;
	medicalAlerts?: string | null;
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
	/** Omitted (null) unless the caller holds STUDENT_SENSITIVE_VIEW. */
	category: string | null;
	/** Omitted (null) unless the caller holds STUDENT_SENSITIVE_VIEW. */
	medicalAlerts: string | null;
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

export interface StudentImportRowResult {
	row: number;
	success: boolean;
	message: string;
	studentId: number | null;
}

export interface StudentExportRow {
	id: number;
	firstName: string;
	lastName: string | null;
	email: string | null;
	phone: string | null;
	admissionNumber: string;
	admissionDate: string;
	bloodGroup: string | null;
	category: string | null;
	status: string;
}

/** Column order the backend's StudentRowImportService expects - mirrored here so the
 * downloadable template (#147) always matches what /students/import actually parses. */
export const STUDENT_IMPORT_TEMPLATE_HEADERS = [
	"firstName",
	"lastName",
	"dateOfBirth",
	"gender",
	"email",
	"phone",
	"admissionNumber",
	"admissionDate",
	"admissionSource",
	"previousSchool",
	"tcNumber",
	"entranceScore",
	"bloodGroup",
	"category",
	"nationality",
	"remarks",
	"medicalAlerts",
];

/** Separate from api/client.ts's request() - a file upload needs a multipart body with
 * a browser-generated boundary, same reasoning as uploadAsset() in api/assets.ts. */
export async function importStudents(file: File): Promise<StudentImportRowResult[]> {
	const session = getSession();
	const headers = new Headers();
	headers.set("ngrok-skip-browser-warning", "true");
	if (session) {
		headers.set("Authorization", `Bearer ${session.token}`);
	}

	const formData = new FormData();
	formData.append("file", file);

	const response = await fetch(`${API_BASE_URL}/api/v1/students/import`, { method: "POST", headers, body: formData });
	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new ApiError(response.status, body?.error ?? `Import failed with status ${response.status}`);
	}
	return response.json() as Promise<StudentImportRowResult[]>;
}

export function exportStudents(): Promise<StudentExportRow[]> {
	return api.get<StudentExportRow[]>("/api/v1/students/export");
}
