import { api } from "./client";

export interface CreateCampusRequest {
	name: string;
	code: string;
	addressLine1?: string | null;
	addressLine2?: string | null;
	city?: string | null;
	state?: string | null;
	pincode?: string | null;
	timezone?: string | null;
}

export interface CampusResponse {
	id: number;
	name: string;
	code: string;
	addressLine1: string | null;
	addressLine2: string | null;
	city: string | null;
	state: string | null;
	pincode: string | null;
	timezone: string | null;
	status: string;
}

export function createCampus(request: CreateCampusRequest): Promise<CampusResponse> {
	return api.post<CampusResponse>("/api/v1/campuses", request);
}

export function listCampuses(): Promise<CampusResponse[]> {
	return api.get<CampusResponse[]>("/api/v1/campuses");
}
