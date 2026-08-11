import { api } from "./client";

export interface GrantMembershipRequest {
	userId: number;
	personId: number;
	roleId: number;
	campusId?: number | null;
}

export interface MembershipResponse {
	id: number;
	userId: number;
	personId: number;
	personName: string;
	roleId: number;
	roleName: string;
	campusId: number | null;
	status: string;
}

export function listMemberships(): Promise<MembershipResponse[]> {
	return api.get<MembershipResponse[]>("/api/v1/memberships");
}

export function grantMembership(request: GrantMembershipRequest): Promise<MembershipResponse> {
	return api.post<MembershipResponse>("/api/v1/memberships", request);
}

export function revokeMembership(id: number): Promise<MembershipResponse> {
	return api.post<MembershipResponse>(`/api/v1/memberships/${id}/revoke`, undefined);
}
