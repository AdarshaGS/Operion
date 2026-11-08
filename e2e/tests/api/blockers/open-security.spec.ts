// OPEN security items: SEC-M4, SEC-M5, SEC-M7, SEC-L2, SEC-H5.
// Every test asserts the DESIRED behaviour, so a failure here is a reproduced, still-open bug.

import { expect, test } from "@playwright/test";
import {
	BLK_PASSWORD,
	PLATFORM_ADMIN_PASSWORD,
	TINY_PNG,
	admitBlkStudent,
	describe,
	enableModules,
	get,
	isOk,
	mysql,
	newGuardian,
	newOrg,
	newPlatformAdmin,
	post,
	raw,
	seedAcademics,
	sharedPlatformAdmin,
	uniq,
	type BlkOrg,
} from "../../../api/blk-helpers";

test.describe("SEC-M4 anonymous organisation creation cannot choose a billing plan", () => {
	test("[OPEN SEC-M4] a client-supplied planId is rejected or ignored (no subscription to that plan appears)", async () => {
		const admin = await sharedPlatformAdmin();
		const plan = await post("/api/v1/platform/plans", { code: `BLK${uniq()}`.toUpperCase().slice(0, 30), name: `Blk plan ${uniq()}`, pricePerStudentPerYear: 1 }, admin.token);
		expect(isOk(plan), describe(plan)).toBe(true);

		const slug = `blk-m4-${uniq()}`;
		// No Authorization header: this is the public sign-up endpoint.
		const created = await post("/api/v1/organisations", {
			name: "Blk m4",
			legalName: "Blk m4 Pvt Ltd",
			slug,
			adminEmail: `owner@${slug}.test`,
			adminPassword: BLK_PASSWORD,
			planId: plan.body.id,
			planStartDate: "2026-06-01",
		});
		if (!isOk(created)) {
			expect(created.status, `rejection should be a 4xx: ${describe(created)}`).toBeLessThan(500);
			return; // rejected outright: desired behaviour
		}
		const subs = await get(`/api/v1/platform/organisations/${created.body.id}/subscriptions`, admin.token);
		expect(subs.status, describe(subs)).toBe(200);
		const onChosenPlan = (subs.body as { planId: number }[]).filter((s) => s.planId === plan.body.id);
		expect(onChosenPlan, `anonymous caller put the new school on plan ${plan.body.id}: ${describe(subs)}`).toHaveLength(0);
	});
});

test.describe("SEC-M5 uploaded files require authentication", () => {
	test("[OPEN SEC-M5] GET /uploads/<file> without a token is 401 or 403", async () => {
		const org = await newOrg("m5");
		const form = new FormData();
		form.append("file", new Blob([new Uint8Array(TINY_PNG)], { type: "image/png" }), "blk-m5.png");
		const upload = await raw("POST", "/api/v1/assets", { token: org.ownerToken, form });
		expect(isOk(upload), `upload setup failed: ${describe(upload)}`).toBe(true);
		const url: string = upload.body.url;
		expect(url, describe(upload)).toMatch(/^\/uploads\//);

		const authed = await raw("GET", url, { token: org.ownerToken });
		const anonymous = await raw("GET", url);
		expect([401, 403], `anonymous GET ${url} returned ${anonymous.status} (authenticated returned ${authed.status})`).toContain(anonymous.status);
	});
});

test.describe("SEC-M7 a guardian cannot open a direct thread with another child's guardian", () => {
	test("[OPEN SEC-M7] guardian A POST /messaging/threads/direct/{guardian B person} is refused", async () => {
		const org = await newOrg("m7");
		await enableModules(org.organisationId, ["MESSAGING", "STUDENTS"]);
		const childA = await admitBlkStudent(org);
		const childB = await admitBlkStudent(org);
		const guardianA = await newGuardian(org, "A", childA.studentId);
		const guardianB = await newGuardian(org, "B", childB.studentId);

		const r = await post(`/api/v1/messaging/threads/direct/${guardianB.personId}`, undefined, guardianA.userToken);
		expect(r.status, `guardian A opened a DM with an unrelated guardian: ${describe(r)}`).toBeGreaterThanOrEqual(400);
		expect(r.status, describe(r)).toBeLessThan(500);
	});
});

test.describe("SEC-L2 invalid request bodies are 400, not 500 or 2xx", () => {
	let org: BlkOrg;
	test.beforeAll(async () => {
		org = await newOrg("l2");
	});

	const expect400 = (r: { status: number; text: string }) => expect(r.status, `HTTP ${r.status} ${r.text.slice(0, 200)}`).toBe(400);

	const bodies: [string, string, unknown][] = [
		["students", "/api/v1/students", {}],
		["students", "/api/v1/students", { personId: "not-a-number", admissionDate: "not-a-date" }],
		["students", "/api/v1/students", "{ this is not json"],
		["fee payments", "/api/v1/fees/payments", {}],
		["fee payments", "/api/v1/fees/payments", { academicYearId: "abc", amount: "abc", paymentDate: "not-a-date", allocations: "nope" }],
		["fee payments", "/api/v1/fees/payments", "{ this is not json"],
		["roles", "/api/v1/roles", {}],
		["roles", "/api/v1/roles", { name: 12345, permissionCodes: "not-a-list" }],
		["roles", "/api/v1/roles", "{ this is not json"],
		["memberships", "/api/v1/memberships", {}],
		["memberships", "/api/v1/memberships", { userId: "x", personId: "y", roleId: "z", joiningDate: "not-a-date" }],
		["memberships", "/api/v1/memberships", "{ this is not json"],
	];

	for (const [label, path, body] of bodies) {
		const shown = typeof body === "string" ? body : JSON.stringify(body);
		test(`[OPEN SEC-L2] POST ${label} with ${shown.slice(0, 60)} gives 400`, async () => {
			expect400(await post(path, body, org.ownerToken));
		});
	}

	test("[OPEN SEC-L2] POST fee payment with a negative amount gives 400", async () => {
		const academics = await seedAcademics(org);
		expect400(
			await post(
				"/api/v1/fees/payments",
				{ academicYearId: academics.academicYearId, amount: -50, paymentMethod: "CASH", paymentDate: "2026-06-02", allocations: [] },
				org.ownerToken,
			),
		);
	});
});

test.describe("SEC-H5 platform admin sign-in lockout and deactivation", () => {
	const platformLogin = (email: string, password: string) => post("/api/v1/platform/auth/login", { email, password });

	test("[OPEN SEC-H5] after repeated wrong passwords the correct password is refused (lockout)", async () => {
		const admin = await newPlatformAdmin("blk-h5-lock");
		for (let i = 0; i < 6; i++) {
			const bad = await platformLogin(admin.email, `Wrong-${i}-${uniq()}`);
			expect(bad.status, describe(bad)).toBe(401);
		}
		const correct = await platformLogin(admin.email, PLATFORM_ADMIN_PASSWORD);
		expect(correct.status, `account still accepts the correct password after 6 failures: ${describe(correct)}`).not.toBe(200);
	});

	test("[OPEN SEC-H5] an already-issued token stops working once the admin is made INACTIVE", async () => {
		const admin = await newPlatformAdmin("blk-h5-off");
		const before = await get("/api/v1/platform/plans", admin.token);
		expect(before.status, `token should work while ACTIVE: ${describe(before)}`).toBe(200);

		mysql(`UPDATE platform_admins SET status = 'INACTIVE' WHERE id = ${admin.id}`);
		const after = await get("/api/v1/platform/plans", admin.token);
		expect([401, 403], `deactivated admin's token still works: ${describe(after)}`).toContain(after.status);
	});
});
