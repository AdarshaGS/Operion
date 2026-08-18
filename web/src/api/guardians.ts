import { api } from "./client";

export interface CreateGuardianRequest {
	personId: number;
	occupation?: string | null;
}

export interface GuardianResponse {
	id: number;
	personId: number;
	occupation: string | null;
	status: string;
}

export interface PortalInviteResponse {
	inviteId: number;
	claimToken: string;
	expiresAt: string;
}

/** Idempotent - returns the existing Guardian if this Person is already one. */
export function createOrGetGuardian(request: CreateGuardianRequest): Promise<GuardianResponse> {
	return api.post<GuardianResponse>("/api/v1/guardians", request);
}

export function getGuardian(id: number): Promise<GuardianResponse> {
	return api.get<GuardianResponse>(`/api/v1/guardians/${id}`);
}

/** Raw claim token is returned once, here, and nowhere else - never persisted client-side. */
export function grantPortalAccess(guardianId: number): Promise<PortalInviteResponse> {
	return api.post<PortalInviteResponse>(`/api/v1/guardians/${guardianId}/grant-portal-access`);
}
