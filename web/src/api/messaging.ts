import { api } from "./client";

export interface ParticipantSummary {
	personId: number;
	name: string;
}

export interface MessageThreadResponse {
	id: number;
	type: string;
	sectionId: number | null;
	sectionLabel: string | null;
	lastMessageAt: string | null;
	unread: boolean;
	participants: ParticipantSummary[];
}

export interface MessageResponse {
	id: number;
	threadId: number;
	senderPersonId: number;
	senderName: string;
	body: string;
	sentAt: string;
}

export function listThreads(): Promise<MessageThreadResponse[]> {
	return api.get<MessageThreadResponse[]>("/api/v1/messaging/threads");
}

export function openClassGroupThread(sectionId: number): Promise<MessageThreadResponse> {
	return api.post<MessageThreadResponse>(`/api/v1/messaging/threads/class-group/${sectionId}`);
}

export function openDirectThread(personId: number): Promise<MessageThreadResponse> {
	return api.post<MessageThreadResponse>(`/api/v1/messaging/threads/direct/${personId}`);
}

export function listMessages(threadId: number): Promise<MessageResponse[]> {
	return api.get<MessageResponse[]>(`/api/v1/messaging/threads/${threadId}/messages`);
}

export function sendMessage(threadId: number, body: string): Promise<MessageResponse> {
	return api.post<MessageResponse>(`/api/v1/messaging/threads/${threadId}/messages`, { body });
}

export function markThreadRead(threadId: number): Promise<void> {
	return api.post<void>(`/api/v1/messaging/threads/${threadId}/read`);
}
