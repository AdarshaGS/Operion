import { api } from "./client";

export interface CreateCustomerRequest {
	studentId?: number | null;
	guardianId?: number | null;
	name: string;
	phone?: string | null;
}

export interface CustomerResponse {
	id: number;
	studentId: number | null;
	guardianId: number | null;
	name: string;
	phone: string | null;
	status: string;
}

export function createCustomer(request: CreateCustomerRequest): Promise<CustomerResponse> {
	return api.post<CustomerResponse>("/api/v1/inventory/customers", request);
}

export function listCustomers(): Promise<CustomerResponse[]> {
	return api.get<CustomerResponse[]>("/api/v1/inventory/customers");
}

export function changeCustomerStatus(id: number, status: "ACTIVE" | "INACTIVE"): Promise<CustomerResponse> {
	return api.post<CustomerResponse>(`/api/v1/inventory/customers/${id}/status`, { status });
}
