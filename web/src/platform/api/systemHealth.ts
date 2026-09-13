import { platformApi } from "./platformClient";

export interface SystemHealthComponent {
	name: string;
	operational: boolean;
}

export function getSystemHealth(): Promise<SystemHealthComponent[]> {
	return platformApi.get<SystemHealthComponent[]>("/api/v1/platform/system-health");
}
