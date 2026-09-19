import { platformApi } from "./platformClient";

export interface SupportTicketResponse {
	id: number;
	organisationId: number;
	subject: string;
	description: string | null;
	status: string;
	priority: string;
	requesterEmail: string;
	assigneeEmail: string | null;
	createdAt: string;
}

export interface CreateSupportTicketRequest {
	organisationId: number;
	subject: string;
	description: string | null;
	priority: string;
	requesterEmail: string;
}

export function listSupportTickets(): Promise<SupportTicketResponse[]> {
	return platformApi.get<SupportTicketResponse[]>("/api/v1/platform/support-tickets");
}

export function getSupportTicket(id: number): Promise<SupportTicketResponse> {
	return platformApi.get<SupportTicketResponse>(`/api/v1/platform/support-tickets/${id}`);
}

export function createSupportTicket(request: CreateSupportTicketRequest): Promise<SupportTicketResponse> {
	return platformApi.post<SupportTicketResponse>("/api/v1/platform/support-tickets", request);
}

export function changeSupportTicketStatus(id: number, status: string): Promise<SupportTicketResponse> {
	return platformApi.patch<SupportTicketResponse>(`/api/v1/platform/support-tickets/${id}/status`, { status });
}

export function assignSupportTicket(id: number, assigneeEmail: string): Promise<SupportTicketResponse> {
	return platformApi.patch<SupportTicketResponse>(`/api/v1/platform/support-tickets/${id}/assignee`, { assigneeEmail });
}
