import { api } from "./client";

export interface SetupStepsResponse {
	completedSteps: string[];
}

/** Structure Setup wizard steps the school has completed (progress is stored per school, not per browser). */
export function getCompletedSetupSteps(): Promise<SetupStepsResponse> {
	return api.get<SetupStepsResponse>("/api/v1/setup/structure/steps");
}

/** Idempotent - completing an already-completed step is a no-op. */
export function completeSetupStep(stepKey: string): Promise<SetupStepsResponse> {
	return api.post<SetupStepsResponse>(`/api/v1/setup/structure/steps/${encodeURIComponent(stepKey)}/complete`);
}
