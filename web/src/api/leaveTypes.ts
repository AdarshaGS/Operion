import { api } from "./client";

export interface CreateLeaveTypeRequest {
	code: string;
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
