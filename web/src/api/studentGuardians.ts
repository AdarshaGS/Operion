import { api } from "./client";

export const GUARDIAN_RELATIONSHIP_TYPES = [
	"FATHER",
	"MOTHER",
	"LEGAL_GUARDIAN",
	"GRANDPARENT",
	"SIBLING",
	"OTHER",
	"EMERGENCY_CONTACT_ONLY",
] as const;

export type GuardianRelationshipType = (typeof GUARDIAN_RELATIONSHIP_TYPES)[number];

export interface LinkGuardianRequest {
	guardianId: number;
	relationshipType: GuardianRelationshipType;
	primaryGuardian: boolean;
	emergencyContact: boolean;
	canPickup: boolean;
	canReceiveCommunication: boolean;
	contactPriority: number;
}

export type UpdateGuardianRelationshipRequest = Omit<LinkGuardianRequest, "guardianId">;

export interface StudentGuardianResponse {
	id: number;
	studentId: number;
	guardianId: number;
	relationshipType: GuardianRelationshipType;
	primaryGuardian: boolean;
	emergencyContact: boolean;
	canPickup: boolean;
	canReceiveCommunication: boolean;
	contactPriority: number;
	status: string;
}

export function linkGuardian(studentId: number, request: LinkGuardianRequest): Promise<StudentGuardianResponse> {
	return api.post<StudentGuardianResponse>(`/api/v1/students/${studentId}/guardians`, request);
}

export function updateGuardianRelationship(
	studentId: number,
	studentGuardianId: number,
	request: UpdateGuardianRelationshipRequest,
): Promise<StudentGuardianResponse> {
	return api.patch<StudentGuardianResponse>(`/api/v1/students/${studentId}/guardians/${studentGuardianId}`, request);
}

export function listGuardiansForStudent(studentId: number): Promise<StudentGuardianResponse[]> {
	return api.get<StudentGuardianResponse[]>(`/api/v1/students/${studentId}/guardians`);
}

export function listStudentsForGuardian(guardianId: number): Promise<StudentGuardianResponse[]> {
	return api.get<StudentGuardianResponse[]>(`/api/v1/guardians/${guardianId}/students`);
}
