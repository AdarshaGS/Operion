import { api } from "./client";

export interface CreateAnnouncementRequest {
	campusId?: number | null;
	title: string;
	body: string;
	audienceType: string;
	audienceRefId?: number | null;
}

export interface AnnouncementResponse {
	id: number;
	campusId: number | null;
	title: string;
	body: string;
	audienceType: string;
	audienceRefId: number | null;
	status: string;
	publishedAt: string | null;
}

export function createAnnouncement(request: CreateAnnouncementRequest): Promise<AnnouncementResponse> {
	return api.post<AnnouncementResponse>("/api/v1/announcements", request);
}

export function publishAnnouncement(id: number): Promise<AnnouncementResponse> {
	return api.post<AnnouncementResponse>(`/api/v1/announcements/${id}/publish`);
}

export function cancelAnnouncement(id: number): Promise<AnnouncementResponse> {
	return api.post<AnnouncementResponse>(`/api/v1/announcements/${id}/cancel`);
}

export function listAnnouncements(status: string): Promise<AnnouncementResponse[]> {
	return api.get<AnnouncementResponse[]>(`/api/v1/announcements?status=${status}`);
}
