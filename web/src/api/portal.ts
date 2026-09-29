import { api } from "./client";

export type PortalSessionType = "ADMIN" | "PORTAL";

export type PortalContextType = "GUARDIAN" | "TEACHER";

export interface ChildSummary {
	studentId: number;
	personId: number;
	name: string;
}

export interface PortalContext {
	type: PortalContextType;
	guardianId: number | null;
	children: ChildSummary[] | null;
	teacherPersonId: number | null;
}

export interface PortalIdentity {
	sessionType: PortalSessionType;
	portalContexts: PortalContext[];
}

/** Routing gate for admin shell vs. self-service portal shell - see PortalIdentityService
 * on the backend. Distinct from auth.ts's me() (/api/v1/auth/me), which stays the admin
 * shell's identity/permissions source and is unaffected by this. */
export function getPortalIdentity(): Promise<PortalIdentity> {
	return api.get<PortalIdentity>("/api/v1/me");
}

export interface GuardianAttendanceSummary {
	todayStatus: string | null;
	monthly: {
		totalMarkedDays: number;
		presentCount: number;
		absentCount: number;
		lateCount: number;
		halfDayCount: number;
		leaveCount: number;
		percentage: number;
	};
}

export interface GuardianInvoiceSummary {
	invoiceId: number;
	invoiceNumber: string;
	dueDate: string;
	totalAmount: string;
	amountPaid: string;
	outstanding: string;
	status: string;
}

export interface GuardianReportCardSummary {
	reportCardId: number;
	examName: string;
	percentage: number;
	overallGrade: string;
	passed: boolean;
	classRank: number | null;
}

export interface PortalAnnouncementSummary {
	id: number;
	title: string;
	body: string;
	publishedAt: string | null;
}

/** Every call here is scoped server-side to the caller's own linked child - see
 * GuardianPortalService. A studentId the caller isn't linked to 403s. */
export function getChildAttendance(studentId: number): Promise<GuardianAttendanceSummary> {
	return api.get<GuardianAttendanceSummary>(`/api/v1/me/children/${studentId}/attendance`);
}

export function getChildFees(studentId: number): Promise<GuardianInvoiceSummary[]> {
	return api.get<GuardianInvoiceSummary[]>(`/api/v1/me/children/${studentId}/fees`);
}

export function getChildReportCards(studentId: number): Promise<GuardianReportCardSummary[]> {
	return api.get<GuardianReportCardSummary[]>(`/api/v1/me/children/${studentId}/report-cards`);
}

export function getPortalAnnouncements(): Promise<PortalAnnouncementSummary[]> {
	return api.get<PortalAnnouncementSummary[]>("/api/v1/me/announcements");
}
