import { api } from "./client";

export interface AssignFeeRequest {
	studentEnrollmentId: number;
	feeStructureId: number;
	discountAmount?: number | null;
	discountReason?: string | null;
	approvedBy?: number | null;
}

export interface StudentFeeAssignmentResponse {
	id: number;
	studentEnrollmentId: number;
	feeStructureId: number;
	baseAmount: number;
	discountAmount: number | null;
	effectiveAmount: number;
	discountReason: string | null;
	approvedBy: number | null;
	status: string;
}

export function assignFee(request: AssignFeeRequest): Promise<StudentFeeAssignmentResponse> {
	return api.post<StudentFeeAssignmentResponse>("/api/v1/fees/assignments", request);
}

export function listFeeAssignments(studentEnrollmentId: number): Promise<StudentFeeAssignmentResponse[]> {
	return api.get<StudentFeeAssignmentResponse[]>(`/api/v1/fees/assignments?studentEnrollmentId=${studentEnrollmentId}`);
}
