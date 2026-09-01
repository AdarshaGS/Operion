import { api } from "./client";

export interface InstallmentEntry {
	installmentNumber: number;
	dueDate: string;
	amount: number;
}

export interface CreateFeeStructureRequest {
	feeStructureGroupId: number;
	feeCategoryId: number;
	amount: number;
	installments: InstallmentEntry[];
}

export interface FeeStructureInstallmentResponse {
	id: number;
	installmentNumber: number;
	dueDate: string;
	amount: number;
}

export interface FeeStructureResponse {
	id: number;
	feeStructureGroupId: number;
	feeCategoryId: number;
	amount: number;
	status: string;
	installments: FeeStructureInstallmentResponse[];
}

export function createFeeStructure(request: CreateFeeStructureRequest): Promise<FeeStructureResponse> {
	return api.post<FeeStructureResponse>("/api/v1/fees/structures", request);
}

export function listFeeStructures(feeStructureGroupId: number): Promise<FeeStructureResponse[]> {
	return api.get<FeeStructureResponse[]>(`/api/v1/fees/structures?feeStructureGroupId=${feeStructureGroupId}`);
}
