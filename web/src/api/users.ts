import { api } from "./client";

export interface CreateUserRequest {
	email: string;
	phone?: string | null;
	password: string;
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

export function listUsers(): Promise<UserResponse[]> {
	return api.get<UserResponse[]>("/api/v1/users");
}

export function getUser(id: number): Promise<UserResponse> {
	return api.get<UserResponse>(`/api/v1/users/${id}`);
}
