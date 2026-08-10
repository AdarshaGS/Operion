import { api } from "./client";

export interface CreateStudentTransportAssignmentRequest {
	studentEnrollmentId: number;
	routeId: number;
	routeStopId: number;
	usesPickup: boolean;
	usesDrop: boolean;
	effectiveFrom: string;
}

export interface StudentTransportAssignmentResponse {
	id: number;
	studentEnrollmentId: number;
	routeId: number;
	routeStopId: number;
	usesPickup: boolean;
	usesDrop: boolean;
	status: string;
	effectiveFrom: string;
	effectiveTo: string | null;
}

export function assignStudentTransport(request: CreateStudentTransportAssignmentRequest): Promise<StudentTransportAssignmentResponse> {
	return api.post<StudentTransportAssignmentResponse>("/api/v1/transport/assignments", request);
}

export function listStudentAssignment(studentEnrollmentId: number): Promise<StudentTransportAssignmentResponse[]> {
	return api.get<StudentTransportAssignmentResponse[]>(`/api/v1/transport/assignments?studentEnrollmentId=${studentEnrollmentId}`);
}

export function endStudentAssignment(id: number, effectiveTo: string): Promise<StudentTransportAssignmentResponse> {
	return api.post<StudentTransportAssignmentResponse>(`/api/v1/transport/assignments/${id}/end`, { effectiveTo });
}
