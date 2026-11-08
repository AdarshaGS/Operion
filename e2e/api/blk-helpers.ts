// Helpers for the stabilisation-blocker API specs (tests/api/blockers/*.spec.ts).
//
// Every spec file provisions its own organisation(s) through these helpers instead of
// relying on global-setup's .auth files, so a spec is self-contained and can run
// alongside other API specs on the same backend. Setup calls reuse the throwing `api.*`
// client (api/client.ts); assertions on status codes go through `raw()`, which never
// throws and always returns the status + parsed body.

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { API_BASE_URL, api } from "./client";
import {
	admitStudent,
	createAcademicYear,
	createGradeLevel,
	createPerson,
	createSchoolClass,
	createSection,
	enrollStudent,
	listCampuses,
	login,
	markAcademicYearCurrent,
	provisionOrganisation,
} from "./organisations";
import { claimStaffInvite, createRole, grantMembership, inviteUser } from "./roles";
import { claimParentInvite, createGuardian, grantPortalAccess, linkGuardianToStudent } from "./parentPortal";

export const BLK_PASSWORD = "BlkPassw0rd!123";

// ---------------------------------------------------------------------------------------
// Raw HTTP: status + body, never throws on non-2xx.

export interface Raw {
	status: number;
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	body: any;
	text: string;
}

export async function raw(
	method: string,
	path: string,
	opts: { token?: string; body?: unknown; headers?: Record<string, string>; form?: FormData; baseUrl?: string } = {},
): Promise<Raw> {
	const headers: Record<string, string> = { ...(opts.headers ?? {}) };
	if (opts.token) {
		headers.Authorization = `Bearer ${opts.token}`;
	}
	let payload: BodyInit | undefined;
	if (opts.form) {
		payload = opts.form;
	} else if (opts.body !== undefined) {
		headers["Content-Type"] = "application/json";
		payload = typeof opts.body === "string" ? opts.body : JSON.stringify(opts.body);
	}
	const response = await fetch(`${opts.baseUrl ?? API_BASE_URL}${path}`, { method, headers, body: payload, redirect: "manual" });
	const text = await response.text();
	let body: unknown = null;
	try {
		body = text ? JSON.parse(text) : null;
	} catch {
		body = null;
	}
	return { status: response.status, body, text };
}

export const get = (path: string, token?: string) => raw("GET", path, { token });
export const post = (path: string, body?: unknown, token?: string) => raw("POST", path, { token, body });
export const put = (path: string, body?: unknown, token?: string) => raw("PUT", path, { token, body });

export const isOk = (r: Raw) => r.status >= 200 && r.status < 300;

/** Compact description of a response, for assertion failure messages. */
export function describe(r: Raw): string {
	return `HTTP ${r.status} ${r.text.slice(0, 300)}`;
}

// ---------------------------------------------------------------------------------------
// Organisations, users, roles.

export function uniq(): string {
	return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

export interface BlkOrg {
	slug: string;
	organisationId: number;
	ownerEmail: string;
	ownerToken: string;
	ownerUserId: number;
	campusId: number;
}

/** Provisions a fresh, uniquely-slugged organisation through the public endpoint and logs its owner in. */
export async function newOrg(tag: string): Promise<BlkOrg> {
	const slug = `blk-${tag}-${uniq()}`.slice(0, 60);
	const ownerEmail = `owner@${slug}.test`;
	const org = await provisionOrganisation({
		name: `Blk ${tag}`,
		legalName: `Blk ${tag} Pvt Ltd`,
		slug,
		adminEmail: ownerEmail,
		adminPassword: BLK_PASSWORD,
		adminFirstName: "Owner",
		adminLastName: "Blk",
	});
	const session = await login(slug, ownerEmail, BLK_PASSWORD);
	const campuses = await listCampuses(session.token);
	return { slug, organisationId: org.id, ownerEmail, ownerToken: session.token, ownerUserId: session.userId, campusId: campuses[0].id };
}

export interface BlkUser {
	userId: number;
	personId: number;
	email: string;
	token: string;
}

/** An invited-and-claimed login (PENDING -> ACTIVE) with a Person but, optionally, no membership yet. */
export async function newUser(org: BlkOrg, label: string, roleId?: number): Promise<BlkUser> {
	const email = `${label}-${uniq()}@${org.slug}.test`;
	const person = await createPerson(org.ownerToken, { firstName: "Blk", lastName: label, email });
	const invite = await inviteUser(org.ownerToken, email);
	const claimed = await claimStaffInvite(org.slug, invite.claimToken, BLK_PASSWORD);
	if (roleId !== undefined) {
		await grantMembership(org.ownerToken, { userId: invite.userId, personId: person.id, roleId, campusId: org.campusId });
	}
	return { userId: invite.userId, personId: person.id, email, token: claimed.token };
}

export interface BlkStaff extends BlkUser {
	roleId: number;
}

/** A role with exactly `permissionCodes`, plus a claimed user holding it. */
export async function newStaff(org: BlkOrg, label: string, permissionCodes: string[]): Promise<BlkStaff> {
	const role = await createRole(org.ownerToken, `blk-${label}-${uniq()}`, `blocker spec ${label}`, permissionCodes);
	const user = await newUser(org, label, role.id);
	return { ...user, roleId: role.id };
}

export function newRole(org: BlkOrg, label: string, permissionCodes: string[]) {
	return createRole(org.ownerToken, `blk-${label}-${uniq()}`, `blocker spec ${label}`, permissionCodes);
}

// ---------------------------------------------------------------------------------------
// Platform admin plane. There is no API to create a platform admin, so a throwaway one is
// inserted straight into the throwaway platform DB (never the seeded admin, which other
// specs depend on and which must not be locked or disabled).

const PLATFORM_DB = process.env.BLK_PLATFORM_DB ?? "operion_apitest_platform";
const MYSQL_USER = process.env.BLK_MYSQL_USER ?? "root";
const MYSQL_PASSWORD = process.env.BLK_MYSQL_PASSWORD ?? "mysql";
const MYSQL_BIN = process.env.BLK_MYSQL_BIN ?? "mysql";

export function mysql(sql: string, database = PLATFORM_DB): string {
	return execFileSync(MYSQL_BIN, [`-u${MYSQL_USER}`, `-p${MYSQL_PASSWORD}`, "-N", "-B", "-D", database, "-e", sql], {
		encoding: "utf8",
		stdio: ["ignore", "pipe", "ignore"],
	}).trim();
}

export const PLATFORM_ADMIN_PASSWORD = "BlkAdminPw!12345";

export interface BlkPlatformAdmin {
	id: number;
	email: string;
	token: string;
}

/** Throws if the admin cannot be created (mysql/htpasswd unavailable) - callers turn that into a skip. */
export async function newPlatformAdmin(label = "blk"): Promise<BlkPlatformAdmin> {
	const email = `${label}-${uniq()}@operion.test`;
	// htpasswd emits ":<bcrypt hash>" ($2y$ is accepted by Spring's BCryptPasswordEncoder).
	const hash = execFileSync("htpasswd", ["-bnBC", "10", "", PLATFORM_ADMIN_PASSWORD], { encoding: "utf8" })
		.trim()
		.replace(/^:/, "");
	mysql(
		`INSERT INTO platform_admins (name, email, password_hash, status, created_at, updated_at) ` +
			`VALUES ('Blk throwaway', '${email}', '${hash}', 'ACTIVE', NOW(6), NOW(6))`,
	);
	const session = await api.post<{ token: string; platformAdminId: number }>("/api/v1/platform/auth/login", {
		email,
		password: PLATFORM_ADMIN_PASSWORD,
	});
	return { id: session.platformAdminId, email, token: session.token };
}

let cachedAdmin: Promise<BlkPlatformAdmin> | undefined;
/** One throwaway platform admin per worker process, for module toggles / plan lookups. */
export function sharedPlatformAdmin(): Promise<BlkPlatformAdmin> {
	cachedAdmin ??= newPlatformAdmin("blk-shared");
	return cachedAdmin;
}

export async function enableModules(organisationId: number, modules: string[]): Promise<void> {
	const admin = await sharedPlatformAdmin();
	for (const module of modules) {
		await api.put(`/api/v1/platform/organisations/${organisationId}/modules/${module}`, { enabled: true }, admin.token);
	}
}

// ---------------------------------------------------------------------------------------
// Academic structure, students, fees.

export interface BlkAcademics {
	academicYearId: number;
	classId: number;
	sectionId: number;
}

export async function seedAcademics(org: BlkOrg): Promise<BlkAcademics> {
	const year = await createAcademicYear(org.ownerToken, `Blk Year ${uniq()}`, "2026-06-01", "2027-03-31");
	await markAcademicYearCurrent(org.ownerToken, year.id);
	const grade = await createGradeLevel(org.ownerToken, `Blk Grade ${uniq()}`, 1, "PRIMARY");
	const klass = await createSchoolClass(org.ownerToken, year.id, org.campusId, grade.id, `Blk Class ${uniq()}`);
	const section = await createSection(org.ownerToken, klass.id, "A", 40, "Room 1");
	return { academicYearId: year.id, classId: klass.id, sectionId: section.id };
}

export interface BlkStudent {
	studentId: number;
	personId: number;
	enrollmentId?: number;
}

export async function admitBlkStudent(
	org: BlkOrg,
	opts: { academics?: BlkAcademics; admissionNumber?: string; rollNumber?: number } = {},
): Promise<BlkStudent> {
	const person = await createPerson(org.ownerToken, {
		firstName: "Blk",
		lastName: `Student${uniq()}`,
		dateOfBirth: "2015-04-10",
		gender: "FEMALE",
	});
	const student = await api.post<{ id: number }>(
		"/api/v1/students",
		{ personId: person.id, admissionNumber: opts.admissionNumber, admissionDate: "2026-06-01" },
		org.ownerToken,
	);
	let enrollmentId: number | undefined;
	if (opts.academics) {
		const enrollment = await enrollStudent(org.ownerToken, student.id, {
			academicYearId: opts.academics.academicYearId,
			sectionId: opts.academics.sectionId,
			rollNumber: opts.rollNumber ?? 1,
			enrolledDate: "2026-06-01",
		});
		enrollmentId = enrollment.id;
	}
	return { studentId: student.id, personId: person.id, enrollmentId };
}

export { admitStudent };

export interface BlkInvoices {
	academicYearId: number;
	invoiceIds: number[];
}

/** Fee category -> structure group -> structure (two installments) -> assignment -> two invoices. */
export async function seedInvoices(org: BlkOrg, academics: BlkAcademics, enrollmentId: number): Promise<BlkInvoices> {
	const t = org.ownerToken;
	const category = await api.post<{ id: number }>(
		"/api/v1/fees/categories",
		{ code: `BLK${uniq()}`.toUpperCase(), name: `Blk tuition ${uniq()}`, categoryType: "GENERAL" },
		t,
	);
	const group = await api.post<{ id: number }>(
		"/api/v1/fees/structure-groups",
		{ name: `Blk group ${uniq()}`, academicYearId: academics.academicYearId, schoolClassId: academics.classId },
		t,
	);
	const structure = await api.post<{ id: number; installments: { id: number }[] }>(
		"/api/v1/fees/structures",
		{
			feeStructureGroupId: group.id,
			feeCategoryId: category.id,
			amount: 2000,
			paymentFrequency: "MONTHLY",
			installments: [
				{ installmentNumber: 1, dueDate: "2026-12-01", amount: 1000 },
				{ installmentNumber: 2, dueDate: "2027-01-01", amount: 1000 },
			],
		},
		t,
	);
	const assignment = await api.post<{ id: number }>(
		"/api/v1/fees/assignments",
		{ studentEnrollmentId: enrollmentId, feeStructureId: structure.id },
		t,
	);
	const invoiceIds: number[] = [];
	for (const installment of structure.installments) {
		const invoice = await api.post<{ id: number }>(
			`/api/v1/fees/assignments/${assignment.id}/invoices`,
			{ feeStructureInstallmentId: installment.id },
			t,
		);
		invoiceIds.push(invoice.id);
	}
	return { academicYearId: academics.academicYearId, invoiceIds };
}

// ---------------------------------------------------------------------------------------
// Guardians (portal users).

export interface BlkGuardian {
	guardianId: number;
	personId: number;
	userToken: string;
	userId: number;
}

/** A guardian Person linked to `studentId`, with portal access claimed (so it has a login + membership). */
export async function newGuardian(org: BlkOrg, label: string, studentId: number): Promise<BlkGuardian> {
	const person = await createPerson(org.ownerToken, {
		firstName: "Blk",
		lastName: `Guardian${label}`,
		email: `g-${label}-${uniq()}@${org.slug}.test`,
	});
	const guardian = await createGuardian(org.ownerToken, person.id, "Engineer");
	await linkGuardianToStudent(org.ownerToken, studentId, { guardianId: guardian.id, relationshipType: "MOTHER", primaryGuardian: true });
	const invite = await grantPortalAccess(org.ownerToken, guardian.id);
	const session = await claimParentInvite(org.slug, invite.claimToken, BLK_PASSWORD);
	return { guardianId: guardian.id, personId: person.id, userToken: session.token, userId: session.userId };
}

// ---------------------------------------------------------------------------------------
// Dev-only: raw reset token from the backend log (APP_DEV_LOGAUTHTOKENS=true). Used only to
// drive the reset flow forward, never as an assertion target.

const BACKEND_LOG =
	process.env.BLK_BACKEND_LOG ??
	"/private/tmp/claude-503/-Users-adarshgs-Documents-projects-Operion/677d65bf-fb28-4175-8b9a-ea0cccfe20ae/scratchpad/backend.log";

export async function resetTokenFromLog(slug: string, timeoutMs = 10_000): Promise<string | undefined> {
	const pattern = new RegExp(`Password reset requested: org=${slug} userId=\\d+ link=\\S*?token=(\\S+) expiresAt`);
	const deadline = Date.now() + timeoutMs;
	while (Date.now() < deadline) {
		const match = readFileSync(BACKEND_LOG, "utf8").match(pattern);
		if (match) {
			return decodeURIComponent(match[1]);
		}
		await new Promise((resolve) => setTimeout(resolve, 300));
	}
	return undefined;
}

/** 1x1 transparent PNG, small enough to pass any upload validation. */
export const TINY_PNG = Buffer.from(
	"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
	"base64",
);
