import { api } from "./client";

export interface AllocateLeaveBalanceRequest {
	staffProfileId: number;
	leaveTypeId: number;
	academicYearId: number;
	allocatedDays: number;
}

export interface LeaveBalanceResponse {
	staffProfileId: number;
	leaveTypeId: number;
	academicYearId: number;
	allocatedDays: number;
	remainingDays: number;
}

export function allocateLeaveBalance(request: AllocateLeaveBalanceRequest): Promise<LeaveBalanceResponse> {
	return api.post<LeaveBalanceResponse>("/api/v1/hr/leave-balances", request);
}

export function getLeaveBalance(staffProfileId: number, leaveTypeId: number, academicYearId: number): Promise<LeaveBalanceResponse> {
	return api.get<LeaveBalanceResponse>(
		`/api/v1/hr/leave-balances?staffProfileId=${staffProfileId}&leaveTypeId=${leaveTypeId}&academicYearId=${academicYearId}`,
	);
}
