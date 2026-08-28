import { api } from "./client";

export interface ReportParameter {
	name: string;
	type: string;
	label: string;
	sortOrder: number;
}

export interface ReportColumn {
	sourceColumn: string;
	label: string;
	sortOrder: number;
}

export interface SaveReportRequest {
	name: string;
	description: string | null;
	sqlQuery: string;
	parameters: ReportParameter[];
	columns: ReportColumn[];
}

export interface SavedReportResponse {
	id: number;
	name: string;
	description: string | null;
	sqlQuery: string;
	status: string;
	createdBy: number;
	parameters: ReportParameter[];
	columns: ReportColumn[];
}

export interface ReportResultResponse {
	columns: string[];
	rows: Record<string, unknown>[];
}

export interface ShareReportRequest {
	principalType: "USER" | "ROLE";
	principalId: number;
	canRun: boolean;
	canEdit: boolean;
}

export function listReports(): Promise<SavedReportResponse[]> {
	return api.get<SavedReportResponse[]>("/api/v1/reports");
}

export function getReport(id: number): Promise<SavedReportResponse> {
	return api.get<SavedReportResponse>(`/api/v1/reports/${id}`);
}

export function createReport(request: SaveReportRequest): Promise<SavedReportResponse> {
	return api.post<SavedReportResponse>("/api/v1/reports", request);
}

export function updateReport(id: number, request: SaveReportRequest): Promise<SavedReportResponse> {
	return api.put<SavedReportResponse>(`/api/v1/reports/${id}`, request);
}

export function duplicateReport(id: number): Promise<SavedReportResponse> {
	return api.post<SavedReportResponse>(`/api/v1/reports/${id}/duplicate`);
}

export function seedStandardReports(): Promise<SavedReportResponse[]> {
	return api.post<SavedReportResponse[]>("/api/v1/reports/seed-standard");
}

export function publishReport(id: number): Promise<SavedReportResponse> {
	return api.post<SavedReportResponse>(`/api/v1/reports/${id}/publish`);
}

export function archiveReport(id: number): Promise<SavedReportResponse> {
	return api.post<SavedReportResponse>(`/api/v1/reports/${id}/archive`);
}

export function shareReport(id: number, request: ShareReportRequest): Promise<void> {
	return api.post<void>(`/api/v1/reports/${id}/share`, request);
}

export function runReport(id: number, parameters: Record<string, unknown>): Promise<ReportResultResponse> {
	return api.post<ReportResultResponse>(`/api/v1/reports/${id}/run`, { parameters });
}

export function exportReport(id: number, parameters: Record<string, unknown>): Promise<ReportResultResponse> {
	return api.post<ReportResultResponse>(`/api/v1/reports/${id}/export`, { parameters });
}
