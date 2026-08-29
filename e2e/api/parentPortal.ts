// Guardian self-service chain: link a guardian to a student, issue portal access, then
// claim it (public flow) exactly as PortalInviteService.claim() expects - same shape as
// StaffInviteService but under /guardians and /claim-invite.

import { api } from "./client";
import type { LoginResponse } from "./organisations";

export interface GuardianResponse {
	id: number;
	personId: number;
}

export function createGuardian(token: string, personId: number, occupation?: string) {
	return api.post<GuardianResponse>("/api/v1/guardians", { personId, occupation: occupation ?? null }, token);
}

export type GuardianRelationshipType = "FATHER" | "MOTHER" | "LEGAL_GUARDIAN" | "GRANDPARENT" | "SIBLING" | "OTHER" | "EMERGENCY_CONTACT_ONLY";

export function linkGuardianToStudent(
	token: string,
	studentId: number,
	input: { guardianId: number; relationshipType: GuardianRelationshipType; primaryGuardian: boolean },
) {
	return api.post(
		`/api/v1/students/${studentId}/guardians`,
		{
			guardianId: input.guardianId,
			relationshipType: input.relationshipType,
			primaryGuardian: input.primaryGuardian,
			emergencyContact: false,
			canPickup: true,
			canReceiveCommunication: true,
			contactPriority: 1,
		},
		token,
	);
}

export interface PortalInviteResponse {
	inviteId: number;
	claimToken: string;
	expiresAt: string;
}

export function grantPortalAccess(token: string, guardianId: number) {
	return api.post<PortalInviteResponse>(`/api/v1/guardians/${guardianId}/grant-portal-access`, undefined, token);
}

export function claimParentInvite(organisationSlug: string, rawToken: string, password: string) {
	return api.post<LoginResponse>("/api/v1/auth/claim-invite", { organisationSlug, token: rawToken, password });
}
