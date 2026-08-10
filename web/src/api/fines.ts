import { api } from "./client";

export interface RaiseFineRequest {
	borrowRecordId: number;
	amount: number;
	reason: string;
}

export interface FineResponse {
	id: number;
	borrowRecordId: number;
	amount: number;
	reason: string;
	status: string;
	paidDate: string | null;
	waivedBy: number | null;
	waivedReason: string | null;
}

export function raiseFine(request: RaiseFineRequest): Promise<FineResponse> {
	return api.post<FineResponse>("/api/v1/library/fines", request);
}

export function listFinesForRecord(borrowRecordId: number): Promise<FineResponse[]> {
	return api.get<FineResponse[]>(`/api/v1/library/fines?borrowRecordId=${borrowRecordId}`);
}

export function payFine(id: number, paidDate: string): Promise<FineResponse> {
	return api.post<FineResponse>(`/api/v1/library/fines/${id}/pay`, { paidDate });
}

export function waiveFine(id: number, waivedBy: number, waivedReason: string): Promise<FineResponse> {
	return api.post<FineResponse>(`/api/v1/library/fines/${id}/waive`, { waivedBy, waivedReason });
}
