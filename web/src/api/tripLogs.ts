import { api } from "./client";

export interface ScheduleTripRequest {
	routeId: number;
	vehicleId: number;
	driverPersonId?: number | null;
	tripDate: string;
	tripType: string;
}

export interface TripLogResponse {
	id: number;
	routeId: number;
	vehicleId: number;
	driverPersonId: number | null;
	tripDate: string;
	tripType: string;
	status: string;
	startedAt: string | null;
	completedAt: string | null;
	remarks: string | null;
}

export function scheduleTrip(request: ScheduleTripRequest): Promise<TripLogResponse> {
	return api.post<TripLogResponse>("/api/v1/transport/trip-logs", request);
}

export function listTripLogs(routeId: number, tripDate: string): Promise<TripLogResponse[]> {
	return api.get<TripLogResponse[]>(`/api/v1/transport/trip-logs?routeId=${routeId}&tripDate=${tripDate}`);
}

export function startTrip(id: number): Promise<TripLogResponse> {
	return api.post<TripLogResponse>(`/api/v1/transport/trip-logs/${id}/start`);
}

export function completeTrip(id: number, remarks: string): Promise<TripLogResponse> {
	return api.post<TripLogResponse>(`/api/v1/transport/trip-logs/${id}/complete`, { remarks });
}

export function cancelTrip(id: number, remarks: string): Promise<TripLogResponse> {
	return api.post<TripLogResponse>(`/api/v1/transport/trip-logs/${id}/cancel`, { remarks });
}
