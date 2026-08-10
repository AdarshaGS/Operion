import { api } from "./client";

export interface CreateVehicleRequest {
	campusId: number;
	registrationNumber: string;
	vehicleType: string;
	capacity: number;
	driverPersonId?: number | null;
	attendantPersonId?: number | null;
}

export interface VehicleResponse {
	id: number;
	campusId: number;
	registrationNumber: string;
	vehicleType: string;
	capacity: number;
	driverPersonId: number | null;
	attendantPersonId: number | null;
	status: string;
}

export function createVehicle(request: CreateVehicleRequest): Promise<VehicleResponse> {
	return api.post<VehicleResponse>("/api/v1/transport/vehicles", request);
}

export function listVehicles(): Promise<VehicleResponse[]> {
	return api.get<VehicleResponse[]>("/api/v1/transport/vehicles");
}

export function changeVehicleStatus(id: number, status: string): Promise<VehicleResponse> {
	return api.post<VehicleResponse>(`/api/v1/transport/vehicles/${id}/status`, { status });
}
