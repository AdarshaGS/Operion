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

export interface MeResponse {
	userId: number;
	organisationId: number;
	organisationName: string | null;
	email: string | null;
	personName: string | null;
	roleNames: string[];
	permissions: string[];
}

export function login(request: LoginRequest): Promise<LoginResponse> {
	return api.post<LoginResponse>("/api/v1/auth/login", request);
}

export function me(): Promise<MeResponse> {
	return api.get<MeResponse>("/api/v1/auth/me");
}
