import { api } from "./client";

export interface CreateRoleRequest {
	name: string;
	description: string;
	permissionCodes: string[];
}

export interface RoleResponse {
	id: number;
	name: string;
	description: string;
	systemDefault: boolean;
	status: string;
	permissionCodes: string[];
}

export function listRoles(): Promise<RoleResponse[]> {
	return api.get<RoleResponse[]>("/api/v1/roles");
}

export function createRole(request: CreateRoleRequest): Promise<RoleResponse> {
	return api.post<RoleResponse>("/api/v1/roles", request);
}

export function updateRolePermissions(id: number, permissionCodes: string[]): Promise<RoleResponse> {
	return api.post<RoleResponse>(`/api/v1/roles/${id}/permissions`, { permissionCodes });
}

export function changeRoleStatus(id: number, status: "ACTIVE" | "INACTIVE"): Promise<RoleResponse> {
	return api.post<RoleResponse>(`/api/v1/roles/${id}/status`, { status });
}
