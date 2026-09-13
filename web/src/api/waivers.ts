import { api } from "./client";

export interface RecordWaiverRequest {
	invoiceId: number;
	amount: number;
	reason: string;
	approvedBy: number;
	waiverDate: string;
}

export interface WaiverResponse {
	id: number;
	invoiceId: number;
	amount: number;
	reason: string;
	approvedBy: number;
	waiverDate: string;
}

export function recordWaiver(request: RecordWaiverRequest): Promise<WaiverResponse> {
	return api.post<WaiverResponse>("/api/v1/fees/waivers", request);
}

export function listWaivers(invoiceId: number): Promise<WaiverResponse[]> {
	return api.get<WaiverResponse[]>(`/api/v1/fees/waivers?invoiceId=${invoiceId}`);
}
