import { api } from "./client";

export interface CreateRouteRequest {
	campusId: number;
	name: string;
	code: string;
	vehicleId?: number | null;
}

export interface RouteResponse {
	id: number;
	campusId: number;
	name: string;
	code: string;
	vehicleId: number | null;
	status: string;
}

export function createRoute(request: CreateRouteRequest): Promise<RouteResponse> {
	return api.post<RouteResponse>("/api/v1/transport/routes", request);
}

export function listRoutes(): Promise<RouteResponse[]> {
	return api.get<RouteResponse[]>("/api/v1/transport/routes");
}

export function assignVehicleToRoute(routeId: number, vehicleId: number): Promise<RouteResponse> {
	return api.post<RouteResponse>(`/api/v1/transport/routes/${routeId}/vehicle`, { vehicleId });
}

export interface AddRouteStopRequest {
	stopName: string;
	sequenceNumber: number;
	pickupTime?: string | null;
	dropTime?: string | null;
	latitude?: number | null;
	longitude?: number | null;
}

export interface RouteStopResponse {
	id: number;
	routeId: number;
	stopName: string;
	sequenceNumber: number;
	pickupTime: string | null;
	dropTime: string | null;
	latitude: number | null;
	longitude: number | null;
}

export function addRouteStop(routeId: number, request: AddRouteStopRequest): Promise<RouteStopResponse> {
	return api.post<RouteStopResponse>(`/api/v1/transport/routes/${routeId}/stops`, request);
}

export function listRouteStops(routeId: number): Promise<RouteStopResponse[]> {
	return api.get<RouteStopResponse[]>(`/api/v1/transport/routes/${routeId}/stops`);
}
