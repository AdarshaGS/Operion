import { platformApi } from "./platformClient";
import type { CreateOrganisationRequest } from "./organisations";

const BASE = "/api/v1/platform/crm";

export const LEAD_STAGES = ["NEW", "CONTACTED", "DEMO_SCHEDULED", "PROPOSAL", "WON", "LOST"] as const;
export type LeadStage = (typeof LEAD_STAGES)[number];

export const LEAD_SOURCES = ["REFERRAL", "INBOUND", "OUTREACH", "OTHER"] as const;
export const ACTIVITY_TYPES = ["CALL", "EMAIL", "MEETING", "NOTE"] as const;

/** Which record a contact/activity/task hangs off - exactly one of a lead or an organisation. */
export type CrmOwner = { leadId: number } | { organisationId: number };

export interface LeadResponse {
	id: number;
	schoolName: string;
	source: string;
	stage: LeadStage;
	estimatedStudentCount: number | null;
	ownerId: number | null;
	ownerName: string | null;
	notes: string | null;
	convertedOrganisationId: number | null;
	createdAt: string;
	updatedAt: string;
}

export interface CreateLeadRequest {
	schoolName: string;
	source: string;
	estimatedStudentCount?: number | null;
	ownerId?: number | null;
	notes?: string | null;
}

/** Every field optional - null/undefined leaves the current value unchanged. */
export interface UpdateLeadRequest {
	schoolName?: string;
	source?: string;
	estimatedStudentCount?: number | null;
	ownerId?: number | null;
	notes?: string | null;
}

export interface ContactResponse {
	id: number;
	leadId: number | null;
	organisationId: number | null;
	name: string;
	role: string | null;
	email: string | null;
	phone: string | null;
}

export interface ContactInput {
	name: string;
	role: string;
	email: string;
	phone: string;
}

export interface ActivityResponse {
	id: number;
	leadId: number | null;
	organisationId: number | null;
	type: string;
	body: string;
	occurredAt: string;
	authorId: number | null;
	authorName: string | null;
}

export interface CrmTaskResponse {
	id: number;
	leadId: number | null;
	organisationId: number | null;
	title: string;
	dueAt: string;
	assigneeId: number | null;
	assigneeName: string | null;
	completedAt: string | null;
}

export interface CrmAdminResponse {
	id: number;
	name: string;
}

export interface CrmMetricsResponse {
	leadsByStage: Record<LeadStage, number>;
	wonCount: number;
	lostCount: number;
	/** WON / (WON + LOST); null until at least one lead has closed. */
	winRate: number | null;
	openTaskCount: number;
	overdueTaskCount: number;
}

function ownerQuery(owner: CrmOwner): string {
	return "leadId" in owner ? `leadId=${owner.leadId}` : `organisationId=${owner.organisationId}`;
}

export function listLeads(): Promise<LeadResponse[]> {
	return platformApi.get<LeadResponse[]>(`${BASE}/leads`);
}

export function getLead(id: number): Promise<LeadResponse> {
	return platformApi.get<LeadResponse>(`${BASE}/leads/${id}`);
}

export function createLead(request: CreateLeadRequest): Promise<LeadResponse> {
	return platformApi.post<LeadResponse>(`${BASE}/leads`, request);
}

export function updateLead(id: number, request: UpdateLeadRequest): Promise<LeadResponse> {
	return platformApi.patch<LeadResponse>(`${BASE}/leads/${id}`, request);
}

export function changeLeadStage(id: number, stage: LeadStage): Promise<LeadResponse> {
	return platformApi.patch<LeadResponse>(`${BASE}/leads/${id}/stage`, { stage });
}

export function convertLead(id: number, request: CreateOrganisationRequest): Promise<LeadResponse> {
	return platformApi.post<LeadResponse>(`${BASE}/leads/${id}/convert`, request);
}

export function listContacts(owner: CrmOwner): Promise<ContactResponse[]> {
	return platformApi.get<ContactResponse[]>(`${BASE}/contacts?${ownerQuery(owner)}`);
}

export function createContact(owner: CrmOwner, input: ContactInput): Promise<ContactResponse> {
	return platformApi.post<ContactResponse>(`${BASE}/contacts`, { ...owner, ...input });
}

export function updateContact(id: number, input: ContactInput): Promise<ContactResponse> {
	return platformApi.patch<ContactResponse>(`${BASE}/contacts/${id}`, input);
}

export function deleteContact(id: number): Promise<void> {
	return platformApi.delete(`${BASE}/contacts/${id}`);
}

export function listActivities(owner: CrmOwner): Promise<ActivityResponse[]> {
	return platformApi.get<ActivityResponse[]>(`${BASE}/activities?${ownerQuery(owner)}`);
}

export function logActivity(owner: CrmOwner, type: string, body: string, occurredAt?: string): Promise<ActivityResponse> {
	return platformApi.post<ActivityResponse>(`${BASE}/activities`, { ...owner, type, body, occurredAt });
}

export function listTasks(owner: CrmOwner): Promise<CrmTaskResponse[]> {
	return platformApi.get<CrmTaskResponse[]>(`${BASE}/tasks?${ownerQuery(owner)}`);
}

/** Every open (not yet completed) task across all leads and organisations, soonest due first. */
export function listOpenTasks(): Promise<CrmTaskResponse[]> {
	return platformApi.get<CrmTaskResponse[]>(`${BASE}/tasks?open=true`);
}

export function createTask(owner: CrmOwner, title: string, dueAt: string, assigneeId: number | null): Promise<CrmTaskResponse> {
	return platformApi.post<CrmTaskResponse>(`${BASE}/tasks`, { ...owner, title, dueAt, assigneeId });
}

export function completeTask(id: number): Promise<CrmTaskResponse> {
	return platformApi.post<CrmTaskResponse>(`${BASE}/tasks/${id}/complete`);
}

export function getCrmMetrics(): Promise<CrmMetricsResponse> {
	return platformApi.get<CrmMetricsResponse>(`${BASE}/metrics`);
}

export function listCrmAdmins(): Promise<CrmAdminResponse[]> {
	return platformApi.get<CrmAdminResponse[]>(`${BASE}/admins`);
}
