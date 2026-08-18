import { api } from "./client";

export interface LoginRequest {
	organisationSlug: string;
	email: string;
	password: string;
}

export interface LoginResponse {
	token: string;
	expiresAt: string;
	userId: number;
	organisationId: number;
}

export interface ClaimInviteRequest {
	organisationSlug: string;
	token: string;
	password: string;
}

export interface ClaimStaffInviteRequest {
	organisationSlug: string;
	token: string;
	password: string;
}

export interface ChangePasswordRequest {
	currentPassword: string;
	newPassword: string;
}

export interface RequestPasswordResetRequest {
	organisationSlug: string;
	email: string;
}

export interface ConfirmPasswordResetRequest {
	organisationSlug: string;
	token: string;
	newPassword: string;
}

export interface VerifyEmailRequest {
	organisationSlug: string;
	token: string;
}

export interface AckResponse {
	message: string;
}

export interface MeResponse {
	userId: number;
	organisationId: number;
	organisationName: string | null;
	email: string | null;
	personId: number | null;
	personName: string | null;
	roleNames: string[];
	permissions: string[];
}

export function login(request: LoginRequest): Promise<LoginResponse> {
	return api.post<LoginResponse>("/api/v1/auth/login", request);
}

/** Public, unauthenticated - same trust tier as login. Logs the guardian straight in, same as backend's claim(). */
export function claimInvite(request: ClaimInviteRequest): Promise<LoginResponse> {
	return api.post<LoginResponse>("/api/v1/auth/claim-invite", request);
}

export function me(): Promise<MeResponse> {
	return api.get<MeResponse>("/api/v1/auth/me");
}

/** Public, unauthenticated - same trust tier as claimInvite, see StaffInviteService.claim(). */
export function claimStaffInvite(request: ClaimStaffInviteRequest): Promise<LoginResponse> {
	return api.post<LoginResponse>("/api/v1/auth/claim-staff-invite", request);
}

/** "Sign out everywhere" - revokes every refresh token this user holds, not just the
 * current browser tab's. Best-effort from the caller's side (AuthContext.logout() clears
 * the local session either way). */
export function logout(): Promise<AckResponse> {
	return api.post<AckResponse>("/api/v1/auth/logout");
}

export function changePassword(request: ChangePasswordRequest): Promise<AckResponse> {
	return api.put<AckResponse>("/api/v1/auth/password", request);
}

/** Public, unauthenticated - always succeeds regardless of whether the org/email matched
 * anything, see PasswordResetService. */
export function requestPasswordReset(request: RequestPasswordResetRequest): Promise<AckResponse> {
	return api.post<AckResponse>("/api/v1/auth/password-reset/request", request);
}

export function confirmPasswordReset(request: ConfirmPasswordResetRequest): Promise<AckResponse> {
	return api.post<AckResponse>("/api/v1/auth/password-reset/confirm", request);
}

export function verifyEmail(request: VerifyEmailRequest): Promise<AckResponse> {
	return api.post<AckResponse>("/api/v1/auth/verify-email", request);
}
