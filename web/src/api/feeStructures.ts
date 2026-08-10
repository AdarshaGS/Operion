import { api } from "./client";

export interface InstallmentEntry {
	installmentNumber: number;
	dueDate: string;
	amount: number;
}

export interface CreateFeeStructureRequest {
	academicYearId: number;
	schoolClassId: number;
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
	academicYearId: number;
	schoolClassId: number;
	feeCategoryId: number;
	amount: number;
	status: string;
	installments: FeeStructureInstallmentResponse[];
}

export function createFeeStructure(request: CreateFeeStructureRequest): Promise<FeeStructureResponse> {
	return api.post<FeeStructureResponse>("/api/v1/fees/structures", request);
}

export function listFeeStructures(academicYearId: number, schoolClassId: number): Promise<FeeStructureResponse[]> {
	return api.get<FeeStructureResponse[]>(`/api/v1/fees/structures?academicYearId=${academicYearId}&schoolClassId=${schoolClassId}`);
}
