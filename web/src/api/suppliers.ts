import { api } from "./client";

export interface CreateSupplierRequest {
	name: string;
	contactPerson?: string | null;
	phone?: string | null;
	email?: string | null;
	address?: string | null;
}

export interface SupplierResponse {
	id: number;
	name: string;
	contactPerson: string | null;
	phone: string | null;
	email: string | null;
	address: string | null;
	status: string;
}

export function createSupplier(request: CreateSupplierRequest): Promise<SupplierResponse> {
	return api.post<SupplierResponse>("/api/v1/inventory/suppliers", request);
}

export function listSuppliers(): Promise<SupplierResponse[]> {
	return api.get<SupplierResponse[]>("/api/v1/inventory/suppliers");
}

export function changeSupplierStatus(id: number, status: "ACTIVE" | "INACTIVE"): Promise<SupplierResponse> {
	return api.post<SupplierResponse>(`/api/v1/inventory/suppliers/${id}/status`, { status });
}
