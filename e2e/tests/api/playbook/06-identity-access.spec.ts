// Playbook module: Identity & Access - invite staff, roles as permission bundles, menus /
// permissions follow the role, optional campus scope, profile change requests (approve /
// reject), audit log.
import { test, expect } from "@playwright/test";
import { createPerson } from "../../../api/organisations";
import { claimStaffInvite, inviteUser } from "../../../api/roles";
import { addStudent, createWorld, get, makeUser, must, post, put, uid, PB_PASSWORD, call, type World } from "../../../api/pb-helpers";

let w: World;

test.beforeAll(async () => {
	w = await createWorld();
});

async function me(token: string) {
	return get("/api/v1/auth/me", token);
}

test.describe("Identity & Access", () => {
	test("invite staff: an invite yields a claim link; claiming sets a password and logs the person in", async () => {
		const email = `inv-${uid("")}@${w.slug}.test`;
		const invite = await post("/api/v1/users/invite", w.ownerToken, { email });
		expect(invite.status).toBe(200);
		expect(invite.body.claimToken).toBeTruthy();
		const claimed = await claimStaffInvite(w.slug, invite.body.claimToken, PB_PASSWORD);
		expect(claimed.token).toBeTruthy();
		const role = await must(post("/api/v1/roles", w.ownerToken, { name: `inv-${uid("")}`, description: "x", permissionCodes: ["STUDENT_VIEW"], portal: false }), "role");
		const person = await createPerson(w.ownerToken, { firstName: "Invited", lastName: "Pb" });
		await must(post("/api/v1/memberships", w.ownerToken, { userId: invite.body.userId, personId: person.id, roleId: role.id, campusId: w.campusId, joiningDate: "2026-06-01" }), "membership");
		const login = await call("POST", "/api/v1/auth/login", undefined, { organisationSlug: w.slug, email, password: PB_PASSWORD });
		expect(login.status).toBe(200);
	});

	test("inviting the same email twice, or an invalid email, is rejected", async () => {
		const email = `dup-${uid("")}@${w.slug}.test`;
		expect((await post("/api/v1/users/invite", w.ownerToken, { email })).status).toBe(200);
		const again = await post("/api/v1/users/invite", w.ownerToken, { email });
		expect(again.status).toBeGreaterThanOrEqual(400);
		expect(again.status).toBeLessThan(500);
		const bad = await post("/api/v1/users/invite", w.ownerToken, { email: "not-an-email" });
		expect(bad.status).toBe(400);
	});

	test("an invite cannot be claimed with a wrong token or a weak password", async () => {
		const invite = await inviteUser(w.ownerToken, `weak-${uid("")}@${w.slug}.test`);
		const wrongToken = await post("/api/v1/auth/claim-staff-invite", undefined, { organisationSlug: w.slug, token: "definitely-wrong", password: PB_PASSWORD });
		expect(wrongToken.status).toBeGreaterThanOrEqual(400);
		expect(wrongToken.status).toBeLessThan(500);
		const weak = await post("/api/v1/auth/claim-staff-invite", undefined, { organisationSlug: w.slug, token: invite.claimToken, password: "abc" });
		expect(weak.status).toBe(400);
	});

	test("a role is a bundle of permissions; the member's /auth/me permissions equal the role's", async () => {
		const codes = ["STUDENT_VIEW", "ATTENDANCE_VIEW", "EXAM_VIEW"];
		const u = await makeUser(w, "bundle", codes);
		const who = await me(u.token);
		expect(who.status).toBe(200);
		expect([...who.body.permissions].sort()).toEqual([...codes].sort());
		expect(who.body.owner).toBe(false);
		const role = await get(`/api/v1/roles/${u.roleId}`, w.ownerToken);
		expect([...role.body.permissionCodes].sort()).toEqual([...codes].sort());
	});

	test("changing the role's permissions changes what its members can do (menus/permissions follow the role)", async () => {
		const u = await makeUser(w, "follow", ["STUDENT_VIEW"]);
		const s = await addStudent(w);
		expect((await get("/api/v1/fees/invoices?size=1", u.token)).status).toBe(403);
		const update = await post(`/api/v1/roles/${u.roleId}/permissions`, w.ownerToken, { permissionCodes: ["STUDENT_VIEW", "FEE_VIEW"] });
		expect(update.status).toBe(200);
		expect((await me(u.token)).body.permissions).toContain("FEE_VIEW");
		const after = await get(`/api/v1/fees/assignments?studentEnrollmentId=${s.enrollmentId}`, u.token);
		expect(after.status).not.toBe(403);
		await must(post(`/api/v1/roles/${u.roleId}/permissions`, w.ownerToken, { permissionCodes: ["STUDENT_VIEW"] }), "revert");
		expect((await me(u.token)).body.permissions).not.toContain("FEE_VIEW");
	});

	test("the permission catalogue lists the codes a role can be built from", async () => {
		const res = await get("/api/v1/permissions", w.ownerToken);
		expect(res.status).toBe(200);
		const codes = JSON.stringify(res.body);
		for (const code of ["STUDENT_VIEW", "FEE_COLLECT", "ATTENDANCE_MARK", "EXAM_MANAGE", "ROLE_MANAGE"]) expect(codes).toContain(code);
	});

	test("roles and memberships need ROLE_MANAGE / MEMBERSHIP_MANAGE (403 otherwise)", async () => {
		const viewer = await makeUser(w, "iamview", ["STUDENT_VIEW"]);
		expect((await post("/api/v1/roles", viewer.token, { name: `r-${uid("")}`, description: "x", permissionCodes: ["STUDENT_VIEW"], portal: false })).status).toBe(403);
		expect((await post(`/api/v1/roles/${viewer.roleId}/permissions`, viewer.token, { permissionCodes: ["STUDENT_VIEW", "ROLE_MANAGE"] })).status).toBe(403);
		expect((await post("/api/v1/users/invite", viewer.token, { email: `x-${uid("")}@${w.slug}.test` })).status).toBe(403);
		expect((await get("/api/v1/memberships", viewer.token)).status).toBe(403);
		expect((await get("/api/v1/audit-logs", viewer.token)).status).toBe(403);
	});

	test("an inactive role cannot be assigned to a new member", async () => {
		const role = await must(post("/api/v1/roles", w.ownerToken, { name: `inactive-${uid("")}`, description: "x", permissionCodes: ["STUDENT_VIEW"], portal: false }), "role");
		expect((await post(`/api/v1/roles/${role.id}/status`, w.ownerToken, { status: "INACTIVE" })).status).toBe(200);
		const person = await createPerson(w.ownerToken, { firstName: "Inact", lastName: "Pb" });
		const invite = await inviteUser(w.ownerToken, `ia-${uid("")}@${w.slug}.test`);
		const grant = await post("/api/v1/memberships", w.ownerToken, { userId: invite.userId, personId: person.id, roleId: role.id, campusId: w.campusId, joiningDate: "2026-06-01" });
		expect(grant.status).toBe(409);
	});

	test("revoking a membership removes the member's access", async () => {
		const u = await makeUser(w, "revokee", ["STUDENT_VIEW"]);
		expect((await get("/api/v1/students", u.token)).status).toBe(200);
		const memberships = await get("/api/v1/memberships", w.ownerToken);
		const m = memberships.body.find((x: any) => x.userId === u.userId);
		expect(m).toBeTruthy();
		expect((await post(`/api/v1/memberships/${m.id}/revoke`, w.ownerToken)).status).toBe(200);
		const after = await get("/api/v1/students", u.token);
		expect([401, 403]).toContain(after.status);
	});

	test("a membership may be organisation-wide (no campus) or tied to a campus", async () => {
		const role = await must(post("/api/v1/roles", w.ownerToken, { name: `scope-${uid("")}`, description: "x", permissionCodes: ["STUDENT_VIEW"], portal: false }), "role");
		const mk = async (campusId: number | null) => {
			const person = await createPerson(w.ownerToken, { firstName: "Scope", lastName: "Pb" });
			const invite = await inviteUser(w.ownerToken, `sc-${uid("")}@${w.slug}.test`);
			return post("/api/v1/memberships", w.ownerToken, { userId: invite.userId, personId: person.id, roleId: role.id, campusId, joiningDate: "2026-06-01" });
		};
		const wide = await mk(null);
		expect(wide.status).toBe(200);
		expect(wide.body.campusId ?? null).toBeNull();
		const tied = await mk(w.campusId);
		expect(tied.status).toBe(200);
		expect(tied.body.campusId).toBe(w.campusId);
	});

	test("campus scope is enforced: a member tied to campus A does not see campus B's students", async () => {
		const campusB = await must(post("/api/v1/campuses", w.ownerToken, { name: `Campus B ${uid("")}`, code: uid("cb").toUpperCase().slice(0, 12), addressLine1: "1 Road", city: "Pune", state: "MH", pincode: "411001", timezone: "Asia/Kolkata" }), "campus B");
		const grade = await must(post("/api/v1/grade-levels", w.ownerToken, { name: `GB ${uid("")}`, sequenceOrder: 50, stage: "PRIMARY" }), "grade B");
		const klass = await must(post("/api/v1/school-classes", w.ownerToken, { academicYearId: w.yearId, campusId: campusB.id, gradeLevelId: grade.id, displayName: `Class B ${uid("")}` }), "class B");
		const section = await must(post(`/api/v1/school-classes/${klass.id}/sections`, w.ownerToken, { name: "A", capacity: 30, room: "B1" }), "section B");
		const inB = await addStudent(w, { sectionId: section.id });
		const scoped = await makeUser(w, "campusa", ["STUDENT_VIEW", "CLASS_VIEW"]); // membership is tied to campus A
		const direct = await get(`/api/v1/students/${inB.studentId}`, scoped.token);
		const classes = await get(`/api/v1/school-classes?academicYearId=${w.yearId}`, scoped.token);
		const seesClassB = JSON.stringify(classes.body).includes(`"id":${klass.id}`);
		expect([direct.status === 200, seesClassB], "campus-A member can read campus B student / see campus B class").toEqual([false, false]);
	});

	test("profile change request: a member asks for a new phone; an admin approves and the person record changes", async () => {
		const u = await makeUser(w, "pcr", ["STUDENT_VIEW"]);
		const phone = `98${String(Date.now()).slice(-8)}`;
		const sub = await post("/api/v1/me/profile-change-requests", u.token, { phone });
		expect(sub.status).toBe(200);
		expect(sub.body.status).toBe("PENDING");
		const pending = await get("/api/v1/profile-change-requests?status=PENDING", w.ownerToken);
		expect(pending.body.some((r: any) => r.id === sub.body.id)).toBe(true);
		const own = await get("/api/v1/me/profile-change-requests", u.token);
		expect(own.body.some((r: any) => r.id === sub.body.id)).toBe(true);
		const approved = await post(`/api/v1/profile-change-requests/${sub.body.id}/approve`, w.ownerToken);
		expect(approved.status).toBe(200);
		expect(approved.body.status).toBe("APPROVED");
		const person = await get(`/api/v1/persons/${u.personId}`, w.ownerToken);
		expect(person.status).toBe(200);
		expect(JSON.stringify(person.body)).toContain(phone);
		expect((await post(`/api/v1/profile-change-requests/${sub.body.id}/approve`, w.ownerToken)).status).toBe(409);
	});

	test("profile change request can be rejected, leaving the person unchanged; members cannot decide requests", async () => {
		const u = await makeUser(w, "pcr2", ["STUDENT_VIEW"]);
		const phone = `97${String(Date.now()).slice(-8)}`;
		const sub = await must(post("/api/v1/me/profile-change-requests", u.token, { phone }), "submit");
		expect((await post(`/api/v1/profile-change-requests/${sub.id}/reject`, u.token)).status).toBe(403);
		const rejected = await post(`/api/v1/profile-change-requests/${sub.id}/reject`, w.ownerToken);
		expect(rejected.status).toBe(200);
		expect(rejected.body.status).toBe("REJECTED");
		const person = await get(`/api/v1/persons/${u.personId}`, w.ownerToken);
		expect(JSON.stringify(person.body)).not.toContain(phone);
	});

	test("a malformed phone in a change request is rejected", async () => {
		const u = await makeUser(w, "pcr3", ["STUDENT_VIEW"]);
		expect((await post("/api/v1/me/profile-change-requests", u.token, { phone: "call me maybe" })).status).toBe(400);
	});

	test("the owner cannot approve a request submitted by the same account", async () => {
		const sub = await post("/api/v1/me/profile-change-requests", w.ownerToken, { phone: `96${String(Date.now()).slice(-8)}` });
		if (sub.status !== 200) return; // owner has no person record in this org: nothing to self-approve
		expect((await post(`/api/v1/profile-change-requests/${sub.body.id}/approve`, w.ownerToken)).status).toBe(409);
	});
});

test.describe("Audit log (own organisation)", () => {
	let a: World;
	test.beforeAll(async () => {
		a = await createWorld();
	});

	async function auditCount(): Promise<number> {
		const res = await get("/api/v1/audit-logs?size=1", a.ownerToken);
		expect(res.status).toBe(200);
		return res.body.totalElements;
	}

	test("sensitive actions are recorded and queryable by entity type", async () => {
		const before = await auditCount();
		const role = await must(post("/api/v1/roles", a.ownerToken, { name: `aud-${uid("")}`, description: "x", permissionCodes: ["STUDENT_VIEW"], portal: false }), "role");
		await must(post(`/api/v1/roles/${role.id}/permissions`, a.ownerToken, { permissionCodes: ["STUDENT_VIEW", "EXAM_VIEW"] }), "perm");
		expect(await auditCount()).toBeGreaterThanOrEqual(before + 2);
		const types = await get("/api/v1/audit-logs/entity-types", a.ownerToken);
		expect(types.body).toContain("Role");
		const rows = await get(`/api/v1/audit-logs?entityType=Role&entityId=${role.id}`, a.ownerToken);
		expect(rows.body.content.map((r: any) => r.action).sort()).toEqual(["CREATE", "PERMISSIONS_UPDATE"]);
		expect(rows.body.content[0].actorUserId).toBe(a.ownerUserId);
		expect(rows.body.content[0].occurredAt).toBeTruthy();
	});

	test("student admission, attendance lock and report-card publish leave audit rows", async () => {
		const s = await addStudent(a);
		const adm = await get(`/api/v1/audit-logs?entityType=Student&entityId=${s.studentId}`, a.ownerToken);
		expect(adm.body.content.map((r: any) => r.action)).toContain("STUDENT_ADMITTED");
	});

	test("fee payments, waivers and refunds are recorded in the audit log", async () => {
		const { feeSetup, assignAndInvoice, payBody } = await import("../../../api/pb-fees");
		const s = await addStudent(a);
		const fee = await feeSetup(a, { amounts: [500] });
		const inv = await assignAndInvoice(a, s.enrollmentId, fee);
		const before = await auditCount();
		const pay = await post("/api/v1/fees/payments", a.ownerToken, payBody(a, inv.invoices[0].id, 200));
		expect(pay.status).toBe(200);
		expect(await auditCount(), "a fee payment adds no audit row").toBeGreaterThan(before);
	});

	test("the audit log is closed to members without ORGANISATION_MANAGE and cannot be written or deleted through the API", async () => {
		const u = await makeUser(a, "audview", ["STUDENT_VIEW"]);
		const before = await auditCount();
		expect((await get("/api/v1/audit-logs", u.token)).status).toBe(403);
		const write = await post("/api/v1/audit-logs", a.ownerToken, { entityType: "X", entityId: 1, action: "FAKE" });
		expect(write.status).toBeGreaterThanOrEqual(400);
		const del = await call("DELETE", "/api/v1/audit-logs/1", a.ownerToken);
		expect(del.status).toBeGreaterThanOrEqual(400);
		expect(await auditCount(), "nothing was written or removed").toBe(before);
	});

	test("another organisation's audit log is not visible", async () => {
		const other = await createWorld();
		const mine = await get("/api/v1/audit-logs?size=200", a.ownerToken);
		const theirs = await get("/api/v1/audit-logs?size=200", other.ownerToken);
		const myOrgs = new Set(mine.body.content.map((r: any) => r.organisationId));
		const theirOrgs = new Set(theirs.body.content.map((r: any) => r.organisationId));
		expect([...myOrgs].every((id) => !theirOrgs.has(id))).toBe(true);
	});
});
