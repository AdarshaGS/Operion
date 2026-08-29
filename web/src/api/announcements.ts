import { api } from "./client";

export interface CreateAnnouncementRequest {
	campusId?: number | null;
	title: string;
	body: string;
	audienceType: string;
	audienceRefId?: number | null;
	audienceMemberPersonIds?: number[] | null;
	scheduledAt?: string | null;
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
	scheduledAt: string | null;
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

export interface AudiencePreviewResponse {
	audienceSize: number;
	notifiableCount: number;
}

export interface PreviewAudienceParams {
	campusId?: number | null;
	audienceType: string;
	audienceRefId?: number | null;
	audienceMemberPersonIds?: number[] | null;
}

export function previewAudience(params: PreviewAudienceParams): Promise<AudiencePreviewResponse> {
	const query = new URLSearchParams();
	if (params.campusId != null) query.set("campusId", String(params.campusId));
	query.set("audienceType", params.audienceType);
	if (params.audienceRefId != null) query.set("audienceRefId", String(params.audienceRefId));
	(params.audienceMemberPersonIds ?? []).forEach((id) => query.append("audienceMemberPersonIds", String(id)));
	return api.get<AudiencePreviewResponse>(`/api/v1/announcements/preview-audience?${query.toString()}`);
}
