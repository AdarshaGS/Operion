import { platformApi } from "./platformClient";

export interface SubscriptionResponse {
	id: number;
	organisationId: number;
	planId: number;
	pricePerStudentPerYear: number;
	startDate: string;
	endDate: string | null;
	status: string;
}

export interface CreateSubscriptionRequest {
	planId: number;
	startDate: string;
}

export function listSubscriptions(organisationId: number): Promise<SubscriptionResponse[]> {
	return platformApi.get<SubscriptionResponse[]>(`/api/v1/platform/organisations/${organisationId}/subscriptions`);
}

export function createSubscription(organisationId: number, request: CreateSubscriptionRequest): Promise<SubscriptionResponse> {
	return platformApi.post<SubscriptionResponse>(`/api/v1/platform/organisations/${organisationId}/subscriptions`, request);
}

export function listAllSubscriptions(): Promise<SubscriptionResponse[]> {
	return platformApi.get<SubscriptionResponse[]>("/api/v1/platform/subscriptions");
}
