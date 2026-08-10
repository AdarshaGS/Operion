import { api } from "./client";

export interface NotificationRecipientResponse {
	id: number;
	announcementId: number | null;
	channel: string;
	deliveryStatus: string;
	sentAt: string | null;
	readAt: string | null;
}

export function myNotifications(): Promise<NotificationRecipientResponse[]> {
	return api.get<NotificationRecipientResponse[]>("/api/v1/notifications/me");
}

export function markNotificationRead(id: number): Promise<NotificationRecipientResponse> {
	return api.post<NotificationRecipientResponse>(`/api/v1/notifications/${id}/read`);
}

export interface NotificationPreferenceResponse {
	id: number;
	channel: string;
	enabled: boolean;
}

export function myNotificationPreferences(): Promise<NotificationPreferenceResponse[]> {
	return api.get<NotificationPreferenceResponse[]>("/api/v1/notification-preferences/me");
}

export function setMyNotificationPreference(channel: string, enabled: boolean): Promise<NotificationPreferenceResponse> {
	return api.put<NotificationPreferenceResponse>("/api/v1/notification-preferences/me", { channel, enabled });
}
