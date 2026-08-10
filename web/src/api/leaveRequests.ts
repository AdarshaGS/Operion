import { api } from "./client";

export interface RaiseLeaveRequestRequest {
	staffProfileId: number;
	leaveTypeId: number;
	academicYearId: number;
	startDate: string;
	endDate: string;
	numberOfDays: number;
	reason?: string | null;
}

export interface LeaveRequestResponse {
	id: number;
	staffProfileId: number;
	leaveTypeId: number;
	academicYearId: number;
	startDate: string;
	endDate: string;
	numberOfDays: number;
	reason: string | null;
	status: string;
	approvedBy: number | null;
	decidedAt: string | null;
}

export interface DecideLeaveRequestRequest {
	decidedBy: number;
}

export function raiseLeaveRequest(request: RaiseLeaveRequestRequest): Promise<LeaveRequestResponse> {
	return api.post<LeaveRequestResponse>("/api/v1/hr/leave-requests", request);
}

/** Pass staffProfileId for one employee's history, status for an org-wide inbox (e.g. all
 * PENDING requests), or both together to scope by employee and status at once. */
export function listLeaveRequests(filter: { staffProfileId?: number; status?: string }): Promise<LeaveRequestResponse[]> {
	const params = new URLSearchParams();
	if (filter.staffProfileId) params.set("staffProfileId", String(filter.staffProfileId));
	if (filter.status) params.set("status", filter.status);
	return api.get<LeaveRequestResponse[]>(`/api/v1/hr/leave-requests?${params.toString()}`);
}

export function approveLeaveRequest(id: number, request: DecideLeaveRequestRequest): Promise<LeaveRequestResponse> {
	return api.post<LeaveRequestResponse>(`/api/v1/hr/leave-requests/${id}/approve`, request);
}

export function rejectLeaveRequest(id: number, request: DecideLeaveRequestRequest): Promise<LeaveRequestResponse> {
	return api.post<LeaveRequestResponse>(`/api/v1/hr/leave-requests/${id}/reject`, request);
}

export function cancelLeaveRequest(id: number): Promise<LeaveRequestResponse> {
	return api.post<LeaveRequestResponse>(`/api/v1/hr/leave-requests/${id}/cancel`);
}
