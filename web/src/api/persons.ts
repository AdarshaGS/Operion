import { api } from "./client";

export interface CreatePersonRequest {
	firstName: string;
	lastName: string;
	dateOfBirth?: string | null;
	gender?: string | null;
	phone?: string | null;
	email?: string | null;
	photoUrl?: string | null;
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
