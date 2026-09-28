import { api } from "./client";

export interface UpsertDriverProfileRequest {
	licenceNumber: string;
	licenceExpiryDate: string;
}

export interface DriverProfileResponse {
	id: number;
	personId: number;
	licenceNumber: string;
	licenceExpiryDate: string;
}

export function getDriverProfile(personId: number): Promise<DriverProfileResponse | null> {
	return api.get<DriverProfileResponse | null>(`/api/v1/transport/drivers/${personId}`);
}

export function upsertDriverProfile(personId: number, request: UpsertDriverProfileRequest): Promise<DriverProfileResponse> {
	return api.post<DriverProfileResponse>(`/api/v1/transport/drivers/${personId}`, request);
}
