// Provisions one isolated organisation for the whole run, seeds a minimal academic
// structure (needed by attendance/examinations/etc. specs), creates every role fixture
// from fixtures/roles.ts, and writes a Playwright storageState file per role by
// constructing the localStorage entry directly from each login response - see
// tokenStore.ts/platformTokenStore.ts for the exact shape this mirrors. Runs once via
// the `setup` project in playwright.config.ts; every role project depends on it.

import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import type { FullConfig } from "@playwright/test";
import {
	admitStudent,
	createAcademicYear,
	createGradeLevel,
	createPerson,
	createSchoolClass,
	createSection,
	createSubject,
	enrollStudent,
	listCampuses,
	login,
	provisionOrganisation,
	type LoginResponse,
} from "./api/organisations";
import { assignTeacher, claimStaffInvite, createRole, grantMembership, inviteUser } from "./api/roles";
import { claimParentInvite, createGuardian, grantPortalAccess, linkGuardianToStudent } from "./api/parentPortal";
import { platformLogin } from "./api/platformAuth";
import { PLATFORM_ADMIN_CREDENTIALS, ROLE_FIXTURES } from "./fixtures/roles";
import { AUTH_DIR, FRONTEND_BASE_URL } from "./env";

// Exported so tests/flows/*.spec.ts can log in as a fixture through the real UI - every
// non-owner fixture (teacher, guardian, ...) is claimed with this same password below.
export const FIXED_PASSWORD = "E2ePassw0rd!123";

interface StorageState {
	cookies: [];
	origins: { origin: string; localStorage: { name: string; value: string }[] }[];
}

function tenantStorageState(session: LoginResponse): StorageState {
	return {
		cookies: [],
		origins: [
			{
				origin: FRONTEND_BASE_URL,
				localStorage: [
					{
						name: "operion.session",
						value: JSON.stringify({
							token: session.token,
							expiresAt: session.expiresAt,
							userId: session.userId,
							organisationId: session.organisationId,
						}),
					},
				],
			},
		],
	};
}

function platformStorageState(session: { token: string; expiresAt: string; platformAdminId: number }, email: string): StorageState {
	return {
		cookies: [],
		origins: [
			{
				origin: FRONTEND_BASE_URL,
				localStorage: [
					{
						name: "operion.platform.session",
						value: JSON.stringify({
							token: session.token,
							expiresAt: session.expiresAt,
							platformAdminId: session.platformAdminId,
							email,
						}),
					},
				],
			},
		],
	};
}

export interface SeedData {
	organisationSlug: string;
	ownerEmail: string;
	ownerPassword: string;
	campusId: number;
	academicYearId: number;
	academicYearName: string;
	gradeLevelId: number;
	classId: number;
	className: string;
	sectionId: number;
	sectionName: string;
	studentId: number;
	studentPersonId: number;
	subjectId: number;
	subjectName: string;
	teacherPersonId: number;
}

export default async function globalSetup(_config: FullConfig): Promise<void> {
	mkdirSync(AUTH_DIR, { recursive: true });

	const runId = Date.now().toString(36);
	const organisationSlug = `e2e-${runId}`;

	await provisionOrganisation({
		name: `E2E Org ${runId}`,
		legalName: `E2E Org ${runId} Pvt Ltd`,
		slug: organisationSlug,
		adminEmail: `owner@${organisationSlug}.test`,
		adminPassword: FIXED_PASSWORD,
		adminFirstName: "Owner",
		adminLastName: "E2E",
	});

	const ownerSession = await login(organisationSlug, `owner@${organisationSlug}.test`, FIXED_PASSWORD);
	const ownerToken = ownerSession.token;
	writeFileSync(join(AUTH_DIR, "owner.json"), JSON.stringify(tenantStorageState(ownerSession)));

	const campuses = await listCampuses(ownerToken);
	const campusId = campuses[0].id;

	const academicYearName = `E2E Year ${runId}`;
	const className = "E2E Grade 1";
	const sectionName = "A";
	const academicYear = await createAcademicYear(ownerToken, academicYearName, "2026-06-01", "2027-03-31");
	const gradeLevel = await createGradeLevel(ownerToken, "E2E Grade 1", 1, "PRIMARY");
	const schoolClass = await createSchoolClass(ownerToken, academicYear.id, campusId, gradeLevel.id, className);
	const section = await createSection(ownerToken, schoolClass.id, sectionName, 40, "Room 1");

	let teacherPersonId: number | undefined;

	for (const fixture of ROLE_FIXTURES) {
		if (fixture.name === "owner" || fixture.name === "guardian" || fixture.name === "platformAdmin") {
			continue;
		}

		const role = await createRole(ownerToken, fixture.roleName!, `Seeded for e2e fixture ${fixture.name}`, fixture.permissionCodes!);
		const person = await createPerson(ownerToken, { firstName: "E2E", lastName: fixture.name });
		const invite = await inviteUser(ownerToken, `${fixture.name}@${organisationSlug}.test`);
		const claimed = await claimStaffInvite(organisationSlug, invite.claimToken, FIXED_PASSWORD);
		await grantMembership(ownerToken, { userId: invite.userId, personId: person.id, roleId: role.id, campusId });

		if (fixture.name === "teacher") {
			teacherPersonId = person.id;
		}

		writeFileSync(join(AUTH_DIR, `${fixture.name}.json`), JSON.stringify(tenantStorageState(claimed)));
	}

	// A real (if backend-unenforced, see api/roles.ts's assignTeacher) assignment for the
	// teacher fixture, so tests/flows/teacher-flow.spec.ts has a subject+section to point at.
	const subjectName = "E2E Subject";
	const subject = await createSubject(ownerToken, subjectName, `E2ESUB${runId}`);
	await assignTeacher(ownerToken, section.id, {
		subjectId: subject.id,
		teacherPersonId: teacherPersonId!,
		assignmentType: "SUBJECT",
		startDate: "2026-06-01",
	});

	const studentPerson = await createPerson(ownerToken, {
		firstName: "E2E",
		lastName: "Student",
		dateOfBirth: "2015-04-10",
		gender: "FEMALE",
	});
	const student = await admitStudent(ownerToken, {
		personId: studentPerson.id,
		admissionNumber: `E2E-${runId}`,
		admissionDate: "2026-06-01",
	});
	await enrollStudent(ownerToken, student.id, {
		academicYearId: academicYear.id,
		sectionId: section.id,
		rollNumber: 1,
		enrolledDate: "2026-06-01",
	});

	const guardianPerson = await createPerson(ownerToken, { firstName: "E2E", lastName: "Guardian", email: `guardian@${organisationSlug}.test` });
	const guardian = await createGuardian(ownerToken, guardianPerson.id, "Engineer");
	await linkGuardianToStudent(ownerToken, student.id, { guardianId: guardian.id, relationshipType: "MOTHER", primaryGuardian: true });
	const portalInvite = await grantPortalAccess(ownerToken, guardian.id);
	const guardianSession = await claimParentInvite(organisationSlug, portalInvite.claimToken, FIXED_PASSWORD);
	writeFileSync(join(AUTH_DIR, "guardian.json"), JSON.stringify(tenantStorageState(guardianSession)));

	const platformSession = await platformLogin(PLATFORM_ADMIN_CREDENTIALS.email, PLATFORM_ADMIN_CREDENTIALS.password);
	writeFileSync(join(AUTH_DIR, "platformAdmin.json"), JSON.stringify(platformStorageState(platformSession, PLATFORM_ADMIN_CREDENTIALS.email)));

	const seed: SeedData = {
		organisationSlug,
		ownerEmail: `owner@${organisationSlug}.test`,
		ownerPassword: FIXED_PASSWORD,
		campusId,
		academicYearId: academicYear.id,
		academicYearName,
		gradeLevelId: gradeLevel.id,
		classId: schoolClass.id,
		className,
		sectionId: section.id,
		sectionName,
		studentId: student.id,
		studentPersonId: studentPerson.id,
		subjectId: subject.id,
		subjectName,
		teacherPersonId: teacherPersonId!,
	};
	writeFileSync(join(AUTH_DIR, "seed.json"), JSON.stringify(seed, null, 2));
}
