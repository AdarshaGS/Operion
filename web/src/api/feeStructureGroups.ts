import { api } from "./client";

export interface CreateFeeStructureGroupRequest {
	name: string;
	academicYearId: number;
	schoolClassId: number;
}

export interface FeeStructureGroupResponse {
	id: number;
	name: string;
	academicYearId: number;
	schoolClassId: number;
	status: string;
}

export function createFeeStructureGroup(request: CreateFeeStructureGroupRequest): Promise<FeeStructureGroupResponse> {
	return api.post<FeeStructureGroupResponse>("/api/v1/fees/structure-groups", request);
}

export function listFeeStructureGroups(academicYearId: number, schoolClassId: number): Promise<FeeStructureGroupResponse[]> {
	return api.get<FeeStructureGroupResponse[]>(`/api/v1/fees/structure-groups?academicYearId=${academicYearId}&schoolClassId=${schoolClassId}`);
}
