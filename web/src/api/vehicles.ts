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

export interface AddVehicleDocumentRequest {
	documentType: string;
	fileReference: string;
	fileName: string;
	mimeType?: string | null;
	expiryDate?: string | null;
}

export interface VehicleDocumentResponse {
	id: number;
	vehicleId: number;
	documentType: string;
	fileReference: string;
	fileName: string;
	mimeType: string | null;
	expiryDate: string | null;
	verificationStatus: string;
	verifiedBy: number | null;
	verifiedAt: string | null;
	status: string;
}

export interface VerifyVehicleDocumentRequest {
	verificationStatus: string;
	verifiedBy: number;
}

export function addVehicleDocument(vehicleId: number, request: AddVehicleDocumentRequest): Promise<VehicleDocumentResponse> {
	return api.post<VehicleDocumentResponse>(`/api/v1/transport/vehicles/${vehicleId}/documents`, request);
}

export function listVehicleDocuments(vehicleId: number): Promise<VehicleDocumentResponse[]> {
	return api.get<VehicleDocumentResponse[]>(`/api/v1/transport/vehicles/${vehicleId}/documents`);
}

export function verifyVehicleDocument(documentId: number, request: VerifyVehicleDocumentRequest): Promise<VehicleDocumentResponse> {
	return api.post<VehicleDocumentResponse>(`/api/v1/transport/vehicles/documents/${documentId}/verify`, request);
}
