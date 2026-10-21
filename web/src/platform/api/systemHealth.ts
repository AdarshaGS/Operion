import { platformApi } from "./platformClient";

export interface SystemHealthComponent {
	name: string;
	operational: boolean;
}

export function getSystemHealth(): Promise<SystemHealthComponent[]> {
	return platformApi.get<SystemHealthComponent[]>("/api/v1/platform/system-health");
}

export interface BackupFile {
	name: string;
	sizeBytes: number;
	sha256: string;
}

export interface BackupSummary {
	name: string;
	createdAt: string;
	totalBytes: number;
	files: BackupFile[];
}

export interface BackupStatus {
	scheduleEnabled: boolean;
	schedule: string;
	keep: number;
	directory: string;
	lastAttemptAt: string | null;
	lastError: string | null;
	lastSuccessAt: string | null;
	healthy: boolean;
	backups: BackupSummary[];
}

export function getBackupStatus(): Promise<BackupStatus> {
	return platformApi.get<BackupStatus>("/api/v1/platform/backups");
}

/** Takes a backup now; resolves once it is written and verified (can take minutes on a large school). */
export function runBackupNow(): Promise<BackupSummary> {
	return platformApi.post<BackupSummary>("/api/v1/platform/backups");
}

export interface SecretsFinding {
	setting: string;
	problem: string;
}

export interface SecretsReport {
	enforced: boolean;
	problems: SecretsFinding[];
	warnings: string[];
}

export function getSecretsReport(): Promise<SecretsReport> {
	return platformApi.get<SecretsReport>("/api/v1/platform/system-health/secrets");
}
