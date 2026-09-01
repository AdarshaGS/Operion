import { api } from "./client";

export interface CreatePersonRequest {
	firstName: string;
	lastName: string;
	dateOfBirth?: string | null;
	gender?: string | null;
	phone?: string | null;
	email?: string | null;
	photoUrl?: string | null;
	address?: string | null;
}

export interface PersonResponse {
	id: number;
	firstName: string;
	lastName: string;
	dateOfBirth: string | null;
	gender: string | null;
	phone: string | null;
	email: string | null;
	photoUrl: string | null;
	address: string | null;
	status: string;
}

export function createPerson(request: CreatePersonRequest): Promise<PersonResponse> {
	return api.post<PersonResponse>("/api/v1/persons", request);
}

export function listPersons(): Promise<PersonResponse[]> {
	return api.get<PersonResponse[]>("/api/v1/persons");
}

export function getPerson(id: number): Promise<PersonResponse> {
	return api.get<PersonResponse>(`/api/v1/persons/${id}`);
}

export function updatePersonPhoto(id: number, photoUrl: string): Promise<PersonResponse> {
	return api.patch<PersonResponse>(`/api/v1/persons/${id}/photo`, { photoUrl });
}

export interface UpdatePersonRequest {
	firstName: string;
	lastName: string;
	dateOfBirth?: string | null;
	gender?: string | null;
	phone?: string | null;
	email?: string | null;
	address?: string | null;
}

export function updatePerson(id: number, request: UpdatePersonRequest): Promise<PersonResponse> {
	return api.patch<PersonResponse>(`/api/v1/persons/${id}`, request);
}
