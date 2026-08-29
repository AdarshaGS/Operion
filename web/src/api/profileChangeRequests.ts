import { api } from "./client";

export interface SubmitProfileChangeRequest {
	phone?: string | null;
	email?: string | null;
	photoUrl?: string | null;
}

export interface ProfileChangeRequestResponse {
	id: number;
	personId: number;
	phone: string | null;
	email: string | null;
	photoUrl: string | null;
	status: string;
	requestedBy: number;
	reviewedBy: number | null;
	reviewedAt: string | null;
}

export function submitOwnProfileChangeRequest(request: SubmitProfileChangeRequest): Promise<ProfileChangeRequestResponse> {
	return api.post<ProfileChangeRequestResponse>("/api/v1/me/profile-change-requests", request);
}

export function listOwnProfileChangeRequests(): Promise<ProfileChangeRequestResponse[]> {
	return api.get<ProfileChangeRequestResponse[]>("/api/v1/me/profile-change-requests");
}

export function listProfileChangeRequests(status?: string): Promise<ProfileChangeRequestResponse[]> {
	const params = new URLSearchParams();
	if (status) params.set("status", status);
	return api.get<ProfileChangeRequestResponse[]>(`/api/v1/profile-change-requests?${params.toString()}`);
}

export function approveProfileChangeRequest(id: number): Promise<ProfileChangeRequestResponse> {
	return api.post<ProfileChangeRequestResponse>(`/api/v1/profile-change-requests/${id}/approve`);
}

export function rejectProfileChangeRequest(id: number): Promise<ProfileChangeRequestResponse> {
	return api.post<ProfileChangeRequestResponse>(`/api/v1/profile-change-requests/${id}/reject`);
}
