import { api } from "./client";

export type ReadinessLevel = "PASS" | "WARN" | "FAIL";

export interface ReadinessFinding {
	stage: "CONFIGURATION" | "DATA" | "ACCESS";
	level: ReadinessLevel;
	message: string;
	fixPath: string | null;
}

export interface GoLiveGroup {
	/** A module code, or ORGANISATION for organisation-wide checks. */
	code: string;
	label: string;
	level: ReadinessLevel;
	/** False when the module contributes no checks yet - shown as "not checked", never as passing. */
	hasChecks: boolean;
	findings: ReadinessFinding[];
}

export interface GoLiveReport {
	ready: boolean;
	groups: GoLiveGroup[];
	hiddenModules: string[];
}

export function getGoLiveReport(): Promise<GoLiveReport> {
	return api.get<GoLiveReport>("/api/v1/organisations/settings/go-live");
}
