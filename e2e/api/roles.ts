// Role + access-grant calls: creating a permission-bundle Role, creating a login
// identity for a fixture user, and granting them a membership under that role.
// This is the "give someone access" chain UsersPanel.tsx drives through the UI -
// StaffInviteService.issue() creates only a login shell (no Person, no membership),
// so a fixture identity needs all three calls below wired together.

import { api } from "./client";
import type { LoginResponse } from "./organisations";

export interface RoleResponse {
	id: number;
	name: string;
	description: string | null;
	permissionCodes: string[];
}

export function createRole(token: string, name: string, description: string, permissionCodes: string[]) {
	return api.post<RoleResponse>("/api/v1/roles", { name, description, permissionCodes }, token);
}

export interface StaffInviteResponse {
	userId: number;
	inviteId: number;
	claimToken: string;
	expiresAt: string;
	emailSent: boolean;
}

/** Creates a login-shell User (status PENDING) and returns the raw claim token directly -
 * no email delivery needed for seeding. Phone is nullable and globally unique on User
 * (see User.java), so it's omitted by default rather than risk a collision across runs. */
export function inviteUser(token: string, email: string, phone: string | null = null) {
	return api.post<StaffInviteResponse>("/api/v1/users/invite", { email, phone }, token);
}

/** Public/unauthenticated - activates the invited user with a real password and logs
 * them in immediately, same as a staff member following the emailed link. */
export function claimStaffInvite(organisationSlug: string, rawToken: string, password: string) {
	return api.post<LoginResponse>("/api/v1/auth/claim-staff-invite", { organisationSlug, token: rawToken, password });
}

export interface MembershipResponse {
	id: number;
	roleId: number;
	campusId: number;
}

export interface TeacherAssignmentResponse {
	id: number;
	sectionId: number;
	subjectId: number | null;
	teacherPersonId: number;
	assignmentType: string;
}

/** Metadata only - TEACHER_ASSIGNMENT_MANAGE records who teaches what, but no backend
 * endpoint filters attendance/exam/marks data by it (see e2e/README.md's "Known gaps"
 * section). Seeded here so the teacher fixture at least has a real assignment on record. */
export function assignTeacher(
	token: string,
	sectionId: number,
	input: { subjectId: number; teacherPersonId: number; assignmentType: "HOMEROOM" | "SUBJECT" | "CO_TEACHER"; startDate: string },
) {
	return api.post<TeacherAssignmentResponse>(`/api/v1/sections/${sectionId}/teacher-assignments`, input, token);
}

export function grantMembership(
	token: string,
	input: { userId: number; personId: number; roleId: number; campusId: number; departmentId?: number | null; memberId?: string | null },
) {
	return api.post<MembershipResponse>(
		"/api/v1/memberships",
		{
			userId: input.userId,
			personId: input.personId,
			roleId: input.roleId,
			campusId: input.campusId,
			departmentId: input.departmentId ?? null,
			memberId: input.memberId ?? null,
			joiningDate: new Date().toISOString().slice(0, 10),
		},
		token,
	);
}
