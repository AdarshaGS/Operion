import { api } from "./client";

export interface FeeApproverResponse {
	userId: number;
	name: string;
}

export function listFeeApprovers(): Promise<FeeApproverResponse[]> {
	return api.get<FeeApproverResponse[]>("/api/v1/fees/approvers");
}
