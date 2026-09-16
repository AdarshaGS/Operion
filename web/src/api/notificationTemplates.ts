import { api } from "./client";

export type NotificationChannel = "IN_APP" | "EMAIL" | "SMS" | "WHATSAPP";

export interface NotificationTemplateResponse {
	id: number;
	code: string;
	channel: NotificationChannel;
	subjectTemplate: string | null;
	bodyTemplate: string;
}

export interface CreateNotificationTemplateRequest {
	code: string;
	channel: NotificationChannel;
	subjectTemplate: string | null;
	bodyTemplate: string;
}

export function listNotificationTemplates(): Promise<NotificationTemplateResponse[]> {
	return api.get<NotificationTemplateResponse[]>("/api/v1/notification-templates");
}

export function createNotificationTemplate(
	request: CreateNotificationTemplateRequest,
): Promise<NotificationTemplateResponse> {
	return api.post<NotificationTemplateResponse>("/api/v1/notification-templates", request);
}
