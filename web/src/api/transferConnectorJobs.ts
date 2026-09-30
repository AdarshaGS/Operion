import { api } from "./client";

export type TransferConnectorJobStatus = "PREPARED" | "SUBMITTED" | "FAILED" | "CANCELLED";

export interface TransferConnectorJobResponse {
	id: number;
	studentExitId: number;
	studentId: number;
	studentName: string;
	schemaId: number;
	schemaCode: string;
	connectorName: string;
	status: TransferConnectorJobStatus;
	/** JSON object text: column header -> value. */
	payload: string;
	externalReference: string | null;
	failureReason: string | null;
	submittedAt: string | null;
	createdAt: string;
}

export interface StudentExitResponse {
	id: number;
	studentId: number;
	exitType: string;
	exitDate: string;
	reason: string | null;
	destinationSchool: string | null;
	initiatedBy: number | null;
}

export function listStudentExits(studentId: number): Promise<StudentExitResponse[]> {
	return api.get<StudentExitResponse[]>(`/api/v1/students/${studentId}/exits`);
}

export function listTransferConnectorJobs(status?: TransferConnectorJobStatus): Promise<TransferConnectorJobResponse[]> {
	return api.get<TransferConnectorJobResponse[]>(`/api/v1/transfer-connector-jobs${status ? `?status=${status}` : ""}`);
}

export function prepareTransferConnectorJob(request: {
	studentExitId: number;
	schemaId: number;
	connectorName: string;
}): Promise<TransferConnectorJobResponse> {
	return api.post<TransferConnectorJobResponse>("/api/v1/transfer-connector-jobs", request);
}

export function submitTransferConnectorJob(id: number, externalReference: string | null): Promise<TransferConnectorJobResponse> {
	return api.post<TransferConnectorJobResponse>(`/api/v1/transfer-connector-jobs/${id}/submit`, { externalReference });
}

export function failTransferConnectorJob(id: number, reason: string): Promise<TransferConnectorJobResponse> {
	return api.post<TransferConnectorJobResponse>(`/api/v1/transfer-connector-jobs/${id}/fail`, { reason });
}

export function reprepareTransferConnectorJob(id: number): Promise<TransferConnectorJobResponse> {
	return api.post<TransferConnectorJobResponse>(`/api/v1/transfer-connector-jobs/${id}/reprepare`);
}

export function cancelTransferConnectorJob(id: number): Promise<TransferConnectorJobResponse> {
	return api.post<TransferConnectorJobResponse>(`/api/v1/transfer-connector-jobs/${id}/cancel`);
}
