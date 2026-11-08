import { api } from "./client";

export interface CreateUserRequest {
	email: string;
	phone?: string | null;
	password: string;
}

export interface UpdateUserRequest {
	email: string;
	phone?: string | null;
}

export interface InviteUserRequest {
	email: string;
	phone?: string | null;
}

export interface StaffInviteResponse {
	userId: number;
	inviteId: number;
	claimToken: string;
	expiresAt: string;
	/** Whether the invite link was actually emailed (Brevo, falling back to Resend) - the
	 * claim link/token below are still always shown as a manual fallback regardless. */
	emailSent: boolean;
}

export interface UserResponse {
	id: number;
	email: string;
	phone: string | null;
	status: string;
	lastLoginAt: string | null;
	invitedAt: string;
}

/** How members are onboarded now: the admin sets the password and the member signs in with
 * it straight away (status ACTIVE) - see UserController.create(). */
export function createUser(request: CreateUserRequest): Promise<UserResponse> {
	return api.post<UserResponse>("/api/v1/users", request);
}

/** Token-based invite - no longer used when adding members (see createUser() above); kept
 * for portal/PENDING users. See UserController.invite(). */
export function inviteUser(request: InviteUserRequest): Promise<StaffInviteResponse> {
	return api.post<StaffInviteResponse>("/api/v1/users/invite", request);
}

export function listUsers(): Promise<UserResponse[]> {
	return api.get<UserResponse[]>("/api/v1/users");
}

export function getUser(id: number): Promise<UserResponse> {
	return api.get<UserResponse>(`/api/v1/users/${id}`);
}

export function updateUser(id: number, request: UpdateUserRequest): Promise<UserResponse> {
	return api.put<UserResponse>(`/api/v1/users/${id}`, request);
}

/** Also the deactivate path (status: "DISABLED") - see UserController.changeStatus(). */
export function changeUserStatus(id: number, status: string): Promise<UserResponse> {
	return api.post<UserResponse>(`/api/v1/users/${id}/status`, { status });
}

/** For a member who never claimed their original invite - see UserController.resendInvite(). */
export function resendInvite(id: number): Promise<StaffInviteResponse> {
	return api.post<StaffInviteResponse>(`/api/v1/users/${id}/resend-invite`, undefined);
}
