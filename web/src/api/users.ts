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
}

export function createUser(request: CreateUserRequest): Promise<UserResponse> {
	return api.post<UserResponse>("/api/v1/users", request);
}

/** Preferred path for onboarding a new staff login - see UserController.invite(). The
 * admin never sees or sets the real password, unlike createUser() above. */
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
