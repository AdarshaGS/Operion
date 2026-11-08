// AUTO-1..AUTO-5 (audit-log half) and INV-1 / #354 from docs/stabilisation/BUG_LIST.md.
// Every test asserts the DESIRED behaviour; a failure is a reproduced, still-open bug.

import { expect, test } from "@playwright/test";
import { api } from "../../../api/client";
import { createPerson } from "../../../api/organisations";
import { inviteUser } from "../../../api/roles";
import {
	admitBlkStudent,
	describe,
	get,
	isOk,
	newOrg,
	post,
	seedAcademics,
	seedInvoices,
	sharedPlatformAdmin,
	uniq,
} from "../../../api/blk-helpers";

test.describe("AUTO-1 module gate covers the Performance endpoints", () => {
	test("[OPEN AUTO-1] GET /performance/metrics is not 200 while the PERFORMANCE module is off", async () => {
		const org = await newOrg("a1");
		const admin = await sharedPlatformAdmin();
		await api.put(`/api/v1/platform/organisations/${org.organisationId}/modules/PERFORMANCE`, { enabled: false }, admin.token);

		const r = await get("/api/v1/performance/metrics", org.ownerToken);
		expect([403, 404], `module is off but the endpoint answered: ${describe(r)}`).toContain(r.status);
	});
});

test.describe("AUTO-2 resending a staff invite", () => {
	test("[OPEN AUTO-2] POST /users/{id}/resend-invite for a PENDING invited user succeeds", async () => {
		const org = await newOrg("a2");
		const email = `a2-${uniq()}@${org.slug}.test`;
		await createPerson(org.ownerToken, { firstName: "Blk", lastName: "Pending", email });
		const invite = await inviteUser(org.ownerToken, email);

		const r = await post(`/api/v1/users/${invite.userId}/resend-invite`, undefined, org.ownerToken);
		expect(isOk(r), `resend failed for user ${invite.userId}: ${describe(r)}`).toBe(true);
	});
});

test.describe("AUTO-3 / AUTO-4 input validation", () => {
	test("[OPEN AUTO-3] a fee payment dated 2999-01-01 is rejected (a normal one is accepted)", async () => {
		const org = await newOrg("a3");
		const academics = await seedAcademics(org);
		const student = await admitBlkStudent(org, { academics });
		const { invoiceIds } = await seedInvoices(org, academics, student.enrollmentId!);
		const today = new Date().toISOString().slice(0, 10);
		const pay = (invoiceId: number, paymentDate: string) =>
			post(
				"/api/v1/fees/payments",
				{ academicYearId: academics.academicYearId, amount: 1000, paymentMethod: "CASH", paymentDate, allocations: [{ invoiceId, amount: 1000 }] },
				org.ownerToken,
			);

		const control = await pay(invoiceIds[1], today);
		expect(isOk(control), `control payment dated today should succeed (test setup): ${describe(control)}`).toBe(true);

		const future = await pay(invoiceIds[0], "2999-01-01");
		expect(isOk(future), `payment dated 2999-01-01 was accepted: ${describe(future)}`).toBe(false);
		expect(future.status, describe(future)).toBeGreaterThanOrEqual(400);
		expect(future.status, describe(future)).toBeLessThan(500);
	});

	test("[OPEN AUTO-4] grade-level stage GALACTIC gives 400 (a real stage is accepted)", async () => {
		const org = await newOrg("a4");
		const ok = await post("/api/v1/grade-levels", { name: `Blk Ok ${uniq()}`, sequenceOrder: 1, stage: "PRIMARY" }, org.ownerToken);
		expect(isOk(ok), `control with a real stage should succeed (test setup): ${describe(ok)}`).toBe(true);

		const r = await post("/api/v1/grade-levels", { name: `Blk Galactic ${uniq()}`, sequenceOrder: 2, stage: "GALACTIC" }, org.ownerToken);
		expect(r.status, `unknown stage accepted or failed oddly: ${describe(r)}`).toBe(400);
	});
});

test.describe("AUTO-5 audit-log list with a bad sort", () => {
	test("[OPEN AUTO-5] GET /audit-logs?sort=<unknown field> gives 400, not 500", async () => {
		const org = await newOrg("a5a");
		const control = await get("/api/v1/audit-logs", org.ownerToken);
		expect(control.status, `control without sort should be 200 (test setup): ${describe(control)}`).toBe(200);

		const r = await get("/api/v1/audit-logs?sort=bogusField,asc", org.ownerToken);
		expect(r.status, describe(r)).toBe(400);
	});
});

test.describe("INV-1 / #354 the student id is unique per school, not globally", () => {
	test("[OPEN INV-1] two fresh orgs each admit their first student (auto-generated number) without a 409/500", async () => {
		const [a, b] = [await newOrg("inv1a"), await newOrg("inv1b")];
		const admit = async (org: typeof a) => {
			const person = await createPerson(org.ownerToken, { firstName: "Blk", lastName: `Inv${uniq()}`, dateOfBirth: "2015-04-10", gender: "FEMALE" });
			return post("/api/v1/students", { personId: person.id, admissionDate: "2026-06-01" }, org.ownerToken);
		};
		const first = await admit(a);
		const second = await admit(b);
		expect(isOk(first), `first org: ${describe(first)}`).toBe(true);
		expect(isOk(second), `second org (same year, same sequence): ${describe(second)}`).toBe(true);
	});

	test("[OPEN INV-1] two fresh orgs can admit a student with the same explicit admission number", async () => {
		const [a, b] = [await newOrg("inv1c"), await newOrg("inv1d")];
		const admissionNumber = `BLK-${uniq()}`;
		const admit = async (org: typeof a) => {
			const person = await createPerson(org.ownerToken, { firstName: "Blk", lastName: `Inv${uniq()}`, dateOfBirth: "2015-04-10", gender: "FEMALE" });
			return post("/api/v1/students", { personId: person.id, admissionNumber, admissionDate: "2026-06-01" }, org.ownerToken);
		};
		const first = await admit(a);
		const second = await admit(b);
		expect(isOk(first), `first org: ${describe(first)}`).toBe(true);
		expect(isOk(second), `second org reused the number: ${describe(second)}`).toBe(true);
	});
});
