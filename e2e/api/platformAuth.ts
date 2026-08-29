import { api } from "./client";

export interface PlatformLoginResponse {
	token: string;
	expiresAt: string;
	platformAdminId: number;
}

export function platformLogin(email: string, password: string) {
	return api.post<PlatformLoginResponse>("/api/v1/platform/auth/login", { email, password });
}
