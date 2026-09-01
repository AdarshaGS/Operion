import { api } from "./client";

export interface EnrollmentSummary {
	activeStudents: number;
	campuses: number;
}

export interface AttendanceSummary {
	present: number;
	absent: number;
	late: number;
	halfDay: number;
	marked: number;
	attendanceRatePercent: number;
}

export interface FeeSummary {
	totalInvoiced: number;
	totalCollected: number;
	collectionRatePercent: number;
	outstanding: number;
	overdueInvoices: number;
}

export interface StaffSummary {
	activeStaff: number;
	departments: number;
	designations: number;
}

export interface ExaminationSummary {
	activeExams: number;
}

export interface LibrarySummary {
	activeBooks: number;
	currentlyBorrowed: number;
	overdueBorrows: number;
}

export interface TransportSummary {
	activeVehicles: number;
	activeRoutes: number;
	studentsUsingTransport: number;
}

export interface InventorySummary {
	activeItems: number;
	categories: number;
}

export interface CommunicationSummary {
	announcementsThisMonth: number;
}

export interface SetupChecklist {
	structureConfigured: boolean;
	rolesConfigured: boolean;
	membersAdded: boolean;
	academicSetupConfigured: boolean;
	studentsAdded: boolean;
	feesConfigured: boolean;
	attendanceStarted: boolean;
}

export interface DashboardPreferences {
	setupProgressDismissed: boolean;
	quickActionsDismissed: boolean;
}

export interface DashboardSummaryResponse {
	enrollment: EnrollmentSummary;
	attendanceToday: AttendanceSummary;
	fees: FeeSummary;
	staff: StaffSummary;
	examinations: ExaminationSummary;
	library: LibrarySummary;
	transport: TransportSummary;
	inventory: InventorySummary;
	communication: CommunicationSummary;
	setupChecklist: SetupChecklist;
	preferences: DashboardPreferences;
}

export function getDashboardSummary(): Promise<DashboardSummaryResponse> {
	return api.get<DashboardSummaryResponse>("/api/v1/dashboard/summary");
}

/** Permanent per-user dismissal (#backend DashboardController) - no undo endpoint by
 * design; re-showing these cards is a future Settings > Learning affordance, not this one. */
export function dismissSetupProgress(): Promise<void> {
	return api.post<void>("/api/v1/dashboard/setup-progress/dismiss");
}

export function dismissQuickActions(): Promise<void> {
	return api.post<void>("/api/v1/dashboard/quick-actions/dismiss");
}
