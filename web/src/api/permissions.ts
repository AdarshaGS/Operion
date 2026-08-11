import { api } from "./client";

export interface PermissionResponse {
	id: number;
	code: string;
	module: string;
	description: string;
}

export function listPermissions(): Promise<PermissionResponse[]> {
	return api.get<PermissionResponse[]>("/api/v1/permissions");
}
