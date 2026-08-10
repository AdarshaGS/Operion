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

export function login(request: LoginRequest): Promise<LoginResponse> {
	return api.post<LoginResponse>("/api/v1/auth/login", request);
}
