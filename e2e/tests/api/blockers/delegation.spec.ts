// FIXED regressions: SEC-H1 (membership grant), SEC-H2 (role create/edit), SEC-M1 (owner email),
// SEC-M6 (person directory). Each test provisions what it needs and shares nothing but the org.

import { expect, test } from "@playwright/test";
import { describe, get, isOk, newOrg, newRole, newStaff, newUser, post, put, raw, uniq, type BlkOrg } from "../../../api/blk-helpers";

test.describe("SEC-H1 membership grant cannot exceed the caller's own permissions", () => {
	let org: BlkOrg;
	test.beforeAll(async () => {
		org = await newOrg("h1");
	});

	function grant(token: string, target: { userId: number; personId: number }, roleId: number) {
		return post(
			"/api/v1/memberships",
			{ userId: target.userId, personId: target.personId, roleId, campusId: org.campusId, joiningDate: "2026-06-01" },
			token,
		);
	}

	test("[FIXED SEC-H1] MEMBERSHIP_MANAGE holder cannot grant a role holding permissions they lack (403)", async () => {
		const granter = await newStaff(org, "h1-granter", ["MEMBERSHIP_MANAGE", "MEMBERSHIP_VIEW"]);
		const big = await newRole(org, "h1-big", ["FEE_COLLECT", "FEE_VIEW"]);
		const target = await newUser(org, "h1-target");
		const r = await grant(granter.token, target, big.id);
		expect(r.status, describe(r)).toBe(403);
	});

	test("[FIXED SEC-H1] MEMBERSHIP_MANAGE holder cannot grant THEMSELVES the all-functions role (403)", async () => {
		const granter = await newStaff(org, "h1-self", ["MEMBERSHIP_MANAGE", "MEMBERSHIP_VIEW"]);
		const admin = await newRole(org, "h1-admin", ["ALL_FUNCTIONS"]);
		const r = await grant(granter.token, granter, admin.id);
		expect(r.status, describe(r)).toBe(403);
	});

	test("[FIXED SEC-H1] MEMBERSHIP_MANAGE holder can grant a role within their own permissions", async () => {
		const granter = await newStaff(org, "h1-sub", ["MEMBERSHIP_MANAGE", "MEMBERSHIP_VIEW"]);
		const small = await newRole(org, "h1-small", ["MEMBERSHIP_VIEW"]);
		const target = await newUser(org, "h1-target-small");
		const r = await grant(granter.token, target, small.id);
		expect(isOk(r), describe(r)).toBe(true);
	});

	test("[FIXED SEC-H1] Owner can grant any role", async () => {
		const big = await newRole(org, "h1-big-owner", ["FEE_COLLECT", "FEE_VIEW", "ALL_FUNCTIONS"]);
		const target = await newUser(org, "h1-target-owner");
		const r = await grant(org.ownerToken, target, big.id);
		expect(isOk(r), describe(r)).toBe(true);
	});

	test("[FIXED SEC-H1] ALL_FUNCTIONS holder can grant any role", async () => {
		const admin = await newStaff(org, "h1-allfn", ["ALL_FUNCTIONS"]);
		const big = await newRole(org, "h1-big-allfn", ["FEE_COLLECT", "FEE_VIEW"]);
		const target = await newUser(org, "h1-target-allfn");
		const r = await grant(admin.token, target, big.id);
		expect(isOk(r), describe(r)).toBe(true);
	});
});

test.describe("SEC-H2 role create/edit capped at the caller's own permissions", () => {
	let org: BlkOrg;
	test.beforeAll(async () => {
		org = await newOrg("h2");
	});

	test("[FIXED SEC-H2] ROLE_MANAGE holder cannot create a role with permissions they lack (403)", async () => {
		const manager = await newStaff(org, "h2-mgr", ["ROLE_MANAGE", "STUDENT_VIEW"]);
		const r = await post("/api/v1/roles", { name: `h2-over-${uniq()}`, description: "x", permissionCodes: ["FEE_COLLECT"], portal: false }, manager.token);
		expect(r.status, describe(r)).toBe(403);
	});

	test("[FIXED SEC-H2] ROLE_MANAGE holder cannot create a role with ALL_FUNCTIONS (403)", async () => {
		const manager = await newStaff(org, "h2-mgr-all", ["ROLE_MANAGE", "STUDENT_VIEW"]);
		const r = await post("/api/v1/roles", { name: `h2-all-${uniq()}`, description: "x", permissionCodes: ["ALL_FUNCTIONS"], portal: false }, manager.token);
		expect(r.status, describe(r)).toBe(403);
	});

	test("[FIXED SEC-H2] ROLE_MANAGE holder can create a role within their own permissions", async () => {
		const manager = await newStaff(org, "h2-mgr-ok", ["ROLE_MANAGE", "STUDENT_VIEW"]);
		const r = await post("/api/v1/roles", { name: `h2-ok-${uniq()}`, description: "x", permissionCodes: ["STUDENT_VIEW"], portal: false }, manager.token);
		expect(isOk(r), describe(r)).toBe(true);
	});

	test("[FIXED SEC-H2] ROLE_MANAGE holder cannot add permissions they lack to an existing role (403)", async () => {
		const manager = await newStaff(org, "h2-mgr-edit", ["ROLE_MANAGE", "STUDENT_VIEW"]);
		const role = await newRole(org, "h2-edit", ["STUDENT_VIEW"]);
		const feeCollect = await post(`/api/v1/roles/${role.id}/permissions`, { permissionCodes: ["STUDENT_VIEW", "FEE_COLLECT"] }, manager.token);
		expect(feeCollect.status, describe(feeCollect)).toBe(403);
		const allFunctions = await post(`/api/v1/roles/${role.id}/permissions`, { permissionCodes: ["STUDENT_VIEW", "ALL_FUNCTIONS"] }, manager.token);
		expect(allFunctions.status, describe(allFunctions)).toBe(403);
	});

	test("[FIXED SEC-H2] ROLE_MANAGE holder can edit a role staying within their own permissions", async () => {
		const manager = await newStaff(org, "h2-mgr-edit-ok", ["ROLE_MANAGE", "STUDENT_VIEW"]);
		const role = await newRole(org, "h2-edit-ok", ["STUDENT_VIEW"]);
		const r = await post(`/api/v1/roles/${role.id}/permissions`, { permissionCodes: ["STUDENT_VIEW", "ROLE_MANAGE"] }, manager.token);
		expect(isOk(r), describe(r)).toBe(true);
	});
});

test.describe("SEC-M1 only the Owner may change the Owner's login email", () => {
	let org: BlkOrg;
	test.beforeAll(async () => {
		org = await newOrg("m1");
	});

	test("[FIXED SEC-M1] MEMBERSHIP_MANAGE holder cannot change the Owner's email, and it is unchanged afterwards", async () => {
		const manager = await newStaff(org, "m1-mgr", ["MEMBERSHIP_MANAGE", "MEMBERSHIP_VIEW"]);
		const hijack = `hijack-${uniq()}@attacker.test`;
		const r = await put(`/api/v1/users/${org.ownerUserId}`, { email: hijack }, manager.token);
		expect(isOk(r), `a non-Owner changed the Owner's email: ${describe(r)}`).toBe(false);
		expect(r.status, describe(r)).toBe(403);
		const after = await get(`/api/v1/users/${org.ownerUserId}`, org.ownerToken);
		expect(after.body?.email, describe(after)).toBe(org.ownerEmail);
	});

	test("[FIXED SEC-M1] Owner can change their own email", async () => {
		const ownOrg = await newOrg("m1o");
		const next = `owner-new-${uniq()}@${ownOrg.slug}.test`;
		const r = await put(`/api/v1/users/${ownOrg.ownerUserId}`, { email: next }, ownOrg.ownerToken);
		expect(isOk(r), describe(r)).toBe(true);
		expect(r.body?.email, describe(r)).toBe(next);
	});
});

test.describe("SEC-M6 person directory hides contact details from non-people-viewing roles", () => {
	let org: BlkOrg;
	const phone = "9876501234";
	let email: string;
	let personId: number;

	test.beforeAll(async () => {
		org = await newOrg("m6");
		email = `m6-subject-${uniq()}@${org.slug}.test`;
		const created = await post(
			"/api/v1/persons",
			{ firstName: "Directory", lastName: "Subject", gender: "FEMALE", dateOfBirth: "2014-02-02", phone, email, address: "12 Secret Lane" },
			org.ownerToken,
		);
		expect(isOk(created), describe(created)).toBe(true);
		personId = created.body.id;
	});

	test("[FIXED SEC-M6] a role with no people-viewing permission sees name only (list and by id)", async () => {
		const driver = await newStaff(org, "m6-driver", ["FEE_VIEW"]);
		const list = await get("/api/v1/persons", driver.token);
		expect(list.status, describe(list)).toBe(200);
		const entry = (list.body as { id: number }[]).find((p) => p.id === personId) as Record<string, unknown> | undefined;
		expect(entry, "person missing from the picker list").toBeTruthy();
		expect(entry!.firstName).toBe("Directory");
		expect([entry!.phone, entry!.email, entry!.address, entry!.dateOfBirth], JSON.stringify(entry)).toEqual([null, null, null, null]);

		const one = await get(`/api/v1/persons/${personId}`, driver.token);
		expect(one.status, describe(one)).toBe(200);
		expect([one.body.phone, one.body.email, one.body.address, one.body.dateOfBirth], describe(one)).toEqual([null, null, null, null]);
	});

	test("[FIXED SEC-M6] a STUDENT_VIEW role sees contact details", async () => {
		const registrar = await newStaff(org, "m6-registrar", ["STUDENT_VIEW"]);
		const list = await get("/api/v1/persons", registrar.token);
		expect(list.status, describe(list)).toBe(200);
		const entry = (list.body as { id: number; phone: string; email: string }[]).find((p) => p.id === personId);
		expect(entry, "person missing from list").toBeTruthy();
		expect(entry!.phone).toBe(phone);
		expect(entry!.email).toBe(email);
	});

	test("[FIXED SEC-M6] a role with no permissions at all gets no contact details", async () => {
		const nobody = await newStaff(org, "m6-nobody", []);
		const r = await raw("GET", "/api/v1/persons", { token: nobody.token });
		expect(r.status, describe(r)).toBe(200);
		const entry = (r.body as { id: number; phone: unknown; email: unknown }[]).find((p) => p.id === personId);
		expect(entry?.phone ?? null).toBeNull();
		expect(entry?.email ?? null).toBeNull();
	});
});
