import { api, downloadFile } from "./client";
import type { LeaveTypeResponse } from "./leaveTypes";

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

/** The guardian's own copy of a published report card as a PDF. */
export function downloadChildReportCard(studentId: number, reportCardId: number): Promise<void> {
	return downloadFile(`/api/v1/me/children/${studentId}/report-cards/${reportCardId}/report-card.pdf`, `report-card-${reportCardId}.pdf`);
}

export function getChildReportCards(studentId: number): Promise<GuardianReportCardSummary[]> {
	return api.get<GuardianReportCardSummary[]>(`/api/v1/me/children/${studentId}/report-cards`);
}

export function getPortalAnnouncements(): Promise<PortalAnnouncementSummary[]> {
	return api.get<PortalAnnouncementSummary[]>("/api/v1/me/announcements");
}

export interface TeacherSectionSummary {
	sectionId: number;
	sectionName: string;
	schoolClassDisplayName: string;
	subjectName: string | null;
	assignmentType: string;
}

export interface TeacherLeaveBalanceSummary {
	leaveTypeId: number;
	leaveTypeName: string;
	allocatedDays: number;
	remainingDays: number;
}

export interface TeacherLeaveRequestSummary {
	id: number;
	leaveTypeName: string;
	startDate: string;
	endDate: string;
	numberOfDays: number;
	status: string;
	reason: string | null;
}

export interface RaiseOwnLeaveRequest {
	leaveTypeId: number;
	academicYearId: number;
	startDate: string;
	endDate: string;
	numberOfDays: number;
	reason: string | null;
}

/** Every call here is scoped server-side to the caller's own StaffProfile/TeacherAssignments
 * - see TeacherPortalService. */
export function getTeacherSections(): Promise<TeacherSectionSummary[]> {
	return api.get<TeacherSectionSummary[]>("/api/v1/me/sections");
}

/** Catalog data, not HR_VIEW-gated like the admin-facing GET /api/v1/hr/leave-types -
 * see TeacherPortalService.leaveTypeCatalog. */
export function getPortalLeaveTypes(): Promise<LeaveTypeResponse[]> {
	return api.get<LeaveTypeResponse[]>("/api/v1/me/leave-types");
}

export function getTeacherLeaveBalance(academicYearId: number): Promise<TeacherLeaveBalanceSummary[]> {
	return api.get<TeacherLeaveBalanceSummary[]>(`/api/v1/me/leave-balance?academicYearId=${academicYearId}`);
}

export function getTeacherLeaveRequests(): Promise<TeacherLeaveRequestSummary[]> {
	return api.get<TeacherLeaveRequestSummary[]>("/api/v1/me/leave-requests");
}

export function raiseOwnLeaveRequest(request: RaiseOwnLeaveRequest): Promise<TeacherLeaveRequestSummary> {
	return api.post<TeacherLeaveRequestSummary>("/api/v1/me/leave-requests", request);
}

export interface GuardianPaymentSummary {
	paymentId: number;
	receiptNumber: string;
	paymentDate: string;
	amount: number;
	paymentMethod: string;
	status: string;
}

export function getChildPayments(studentId: number): Promise<GuardianPaymentSummary[]> {
	return api.get<GuardianPaymentSummary[]>(`/api/v1/me/children/${studentId}/payments`);
}

export function downloadChildReceipt(studentId: number, payment: GuardianPaymentSummary): Promise<void> {
	return downloadFile(`/api/v1/me/children/${studentId}/payments/${payment.paymentId}/receipt.pdf`, `receipt-${payment.receiptNumber}.pdf`);
}

export interface GuardianCertificate {
	id: number;
	certificateName: string;
	status: "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | "ISSUED" | "CANCELLED";
	purpose: string | null;
	requestedAt: string;
	certificateNumber: string | null;
	issuedAt: string | null;
	rejectionReason: string | null;
}

export function getRequestableCertificateTypes(): Promise<{ id: number; name: string }[]> {
	return api.get<{ id: number; name: string }[]>("/api/v1/me/certificate-types");
}

export function getChildCertificates(studentId: number): Promise<GuardianCertificate[]> {
	return api.get<GuardianCertificate[]>(`/api/v1/me/children/${studentId}/certificates`);
}

export function requestChildCertificate(studentId: number, certificateTypeId: number, purpose: string | null): Promise<GuardianCertificate> {
	return api.post<GuardianCertificate>(`/api/v1/me/children/${studentId}/certificates`, { certificateTypeId, purpose });
}

export function downloadChildCertificate(studentId: number, requestId: number, certificateNumber: string | null): Promise<void> {
	return downloadFile(`/api/v1/me/children/${studentId}/certificates/${requestId}/certificate.pdf`, `${certificateNumber ?? "certificate"}.pdf`);
}
