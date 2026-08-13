import { platformApi } from "./platformClient";

export interface PlatformLoginRequest {
	email: string;
	password: string;
}

export interface PlatformLoginResponse {
	token: string;
	expiresAt: string;
	platformAdminId: number;
}

export function platformLogin(request: PlatformLoginRequest): Promise<PlatformLoginResponse> {
	return platformApi.post<PlatformLoginResponse>("/api/v1/platform/auth/login", request);
}
