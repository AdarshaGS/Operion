// Helpers for the Sales-Playbook API specs (tests/api/playbook). Unlike api/client.ts these
// never throw on a non-2xx: a playbook test wants to assert on the status itself.
// Every spec file builds its own organisation ("world") so it never depends on the shared
// global-setup org and can mutate freely.

import { API_BASE_URL } from "./client";
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
	markAcademicYearCurrent,
	provisionOrganisation,
} from "./organisations";
import { claimStaffInvite, createRole, grantMembership, inviteUser } from "./roles";
import { platformLogin } from "./platformAuth";
import { PLATFORM_ADMIN_CREDENTIALS } from "../fixtures/roles";

export const PB_PASSWORD = "E2ePassw0rd!123";
export const RUN = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`;
let counter = 0;

/** Unique, lower-case, slug-safe token. */
export function uid(prefix = "pb"): string {
	counter += 1;
	return `${prefix}${RUN}${counter}`.toLowerCase();
}

export interface Res<T = any> {
	status: number;
	ok: boolean;
	body: T;
}

export async function call<T = any>(method: string, path: string, token?: string, body?: unknown): Promise<Res<T>> {
	const headers: Record<string, string> = { "Content-Type": "application/json" };
	if (token) headers.Authorization = `Bearer ${token}`;
	const response = await fetch(`${API_BASE_URL}${path}`, {
		method,
		headers,
		body: body !== undefined ? JSON.stringify(body) : undefined,
	});
	const text = await response.text();
	let parsed: any = null;
	try {
		parsed = text ? JSON.parse(text) : null;
	} catch {
		parsed = text;
	}
	return { status: response.status, ok: response.ok, body: parsed };
}

export const get = <T = any>(path: string, token?: string) => call<T>("GET", path, token);
export const post = <T = any>(path: string, token?: string, body?: unknown) => call<T>("POST", path, token, body ?? {});
export const put = <T = any>(path: string, token?: string, body?: unknown) => call<T>("PUT", path, token, body);
export const patch = <T = any>(path: string, token?: string, body?: unknown) => call<T>("PATCH", path, token, body);
export const del = <T = any>(path: string, token?: string) => call<T>("DELETE", path, token);

/** Multipart upload of an in-memory CSV (field name "file"). */
export async function upload<T = any>(path: string, token: string, csv: string, filename = "import.csv"): Promise<Res<T>> {
	const form = new FormData();
	form.append("file", new Blob([csv], { type: "text/csv" }), filename);
	const response = await fetch(`${API_BASE_URL}${path}`, { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: form });
	const text = await response.text();
	let parsed: any = null;
	try {
		parsed = text ? JSON.parse(text) : null;
	} catch {
		parsed = text;
	}
	return { status: response.status, ok: response.ok, body: parsed };
}

/** Throws unless 2xx - for arrange steps where a failure means the test setup (not the claim) is broken. */
export async function must<T = any>(res: Promise<Res<T>>, what: string): Promise<T> {
	const r = await res;
	if (!r.ok) {
		throw new Error(`ARRANGE FAILED (${what}): HTTP ${r.status} ${JSON.stringify(r.body)}`);
	}
	return r.body;
}

export const YEAR_START = "2026-06-01";
export const YEAR_END = "2027-03-31";

export interface World {
	slug: string;
	orgId: number;
	ownerEmail: string;
	ownerToken: string;
	ownerUserId: number;
	campusId: number;
	yearId: number;
	yearName: string;
	gradeLevelId: number;
	className: string;
	classId: number;
	sectionId: number;
	subjectId: number;
}

export async function platformToken(): Promise<string> {
	return (await platformLogin(PLATFORM_ADMIN_CREDENTIALS.email, PLATFORM_ADMIN_CREDENTIALS.password)).token;
}

export async function enableModule(orgId: number, module: string): Promise<void> {
	const token = await platformToken();
	await must(put(`/api/v1/platform/organisations/${orgId}/modules/${module}`, token, { enabled: true }), `enable ${module}`);
}

/** A fresh org with a current academic year (2026-06-01..2027-03-31), grade level, class, section and subject. */
export async function createWorld(options: { modules?: string[]; warmStudentCounter?: boolean } = {}): Promise<World> {
	const slug = uid("pb");
	const ownerEmail = `owner@${slug}.test`;
	const org = await provisionOrganisation({
		name: `PB Org ${slug}`,
		legalName: `PB Org ${slug} Pvt Ltd`,
		slug,
		adminEmail: ownerEmail,
		adminPassword: PB_PASSWORD,
		adminFirstName: "Owner",
		adminLastName: slug,
	});
	const session = await login(slug, ownerEmail, PB_PASSWORD);
	for (const module of options.modules ?? []) {
		await enableModule(session.organisationId, module);
	}
	const token = session.token;
	const campuses = await listCampuses(token);
	const yearName = `PB Year ${slug}`;
	const year = await createAcademicYear(token, yearName, YEAR_START, YEAR_END);
	await markAcademicYearCurrent(token, year.id);
	const grade = await createGradeLevel(token, `Grade ${slug}`, 1, "PRIMARY");
	const className = `Class ${slug}`;
	const klass = await createSchoolClass(token, year.id, campuses[0].id, grade.id, className);
	const section = await createSection(token, klass.id, "A", 40, "Room 1");
	const subject = await createSubject(token, `Maths ${slug}`, `M${slug}`.toUpperCase());
	if (options.warmStudentCounter !== false) {
		// The first admission in a fresh org inserts the student_id_counters row lazily and
		// concurrent first admissions deadlock (product finding, see 09-admission-concurrency).
		// Admit one student serially so every other test starts from an existing counter.
		const warm = await createPerson(token, { firstName: "Warm", lastName: slug, dateOfBirth: "2015-01-01", gender: "MALE" });
		// Even this first admission can deadlock against another org's first admission (the
		// counter insert takes a gap lock), so retry on 500 - workaround only, finding reported.
		for (let attempt = 0; attempt < 8; attempt++) {
			const r = await call("POST", "/api/v1/students", token, { personId: warm.id, admissionNumber: `W-${slug}`.toUpperCase(), admissionDate: "2026-06-01" });
			if (r.status !== 500) {
				if (!r.ok) throw new Error(`ARRANGE FAILED (warm admission): HTTP ${r.status} ${JSON.stringify(r.body)}`);
				break;
			}
			await new Promise((resolve) => setTimeout(resolve, 100 + Math.random() * 300));
		}
	}
	return {
		slug,
		orgId: org.id,
		ownerEmail,
		ownerToken: token,
		ownerUserId: session.userId,
		campusId: campuses[0].id,
		yearId: year.id,
		yearName,
		gradeLevelId: grade.id,
		className,
		classId: klass.id,
		sectionId: section.id,
		subjectId: subject.id,
	};
}

export interface PbStudent {
	personId: number;
	studentId: number;
	enrollmentId: number;
	admissionNumber: string;
	firstName: string;
}

export async function addStudent(w: World, opts: { sectionId?: number; roll?: number; firstName?: string } = {}): Promise<PbStudent> {
	const firstName = opts.firstName ?? `Stu${uid("")}`;
	const person = await createPerson(w.ownerToken, { firstName, lastName: "Pb", dateOfBirth: "2015-04-10", gender: "FEMALE" });
	const admissionNumber = `A-${uid("")}`.toUpperCase();
	const student = await admitStudent(w.ownerToken, { personId: person.id, admissionNumber, admissionDate: "2026-06-01" });
	const enrollment = await enrollStudent(w.ownerToken, student.id, {
		academicYearId: w.yearId,
		sectionId: opts.sectionId ?? w.sectionId,
		rollNumber: opts.roll ?? Math.floor(Math.random() * 900) + 1,
		enrolledDate: "2026-06-01",
	});
	return { personId: person.id, studentId: student.id, enrollmentId: enrollment.id, admissionNumber, firstName };
}

export interface PbUser {
	userId: number;
	personId: number;
	roleId: number;
	email: string;
	token: string;
}

/** A staff user holding exactly `permissionCodes` (via a fresh role), logged in. */
export async function makeUser(w: World, label: string, permissionCodes: string[]): Promise<PbUser> {
	const role = await createRole(w.ownerToken, `${label}-${uid("r")}`, `pb ${label}`, permissionCodes);
	const person = await createPerson(w.ownerToken, { firstName: label, lastName: "Pb" });
	const email = `${label}-${uid("u")}@${w.slug}.test`;
	const invite = await inviteUser(w.ownerToken, email);
	const claimed = await claimStaffInvite(w.slug, invite.claimToken, PB_PASSWORD);
	await grantMembership(w.ownerToken, { userId: invite.userId, personId: person.id, roleId: role.id, campusId: w.campusId });
	return { userId: invite.userId, personId: person.id, roleId: role.id, email, token: claimed.token };
}

export function csv(headers: string[], rows: (string | number)[][]): string {
	return [headers.join(","), ...rows.map((r) => r.join(","))].join("\n") + "\n";
}

/** A date string `n` days after an ISO date. */
export function addDays(iso: string, n: number): string {
	const d = new Date(`${iso}T00:00:00Z`);
	d.setUTCDate(d.getUTCDate() + n);
	return d.toISOString().slice(0, 10);
}
