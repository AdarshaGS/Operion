import { api } from "./client";

export interface CreateLeaveTypeRequest {
	code: string;
	name: string;
	defaultAnnualDays?: number | null;
}

export interface UpdateLeaveTypeRequest {
	name: string;
	defaultAnnualDays?: number | null;
}

export interface LeaveTypeResponse {
	id: number;
	code: string;
	name: string;
	defaultAnnualDays: number | null;
	status: string;
}

export function createLeaveType(request: CreateLeaveTypeRequest): Promise<LeaveTypeResponse> {
	return api.post<LeaveTypeResponse>("/api/v1/hr/leave-types", request);
}

export function listLeaveTypes(): Promise<LeaveTypeResponse[]> {
	return api.get<LeaveTypeResponse[]>("/api/v1/hr/leave-types");
}

export function updateLeaveType(id: number, request: UpdateLeaveTypeRequest): Promise<LeaveTypeResponse> {
	return api.put<LeaveTypeResponse>(`/api/v1/hr/leave-types/${id}`, request);
}
