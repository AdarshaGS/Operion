// SEC-H3 (FIXED): SqlGuard + tenant isolation of the reporting views.
// AUTO-5 (OPEN, reporting half): omitting parameters / parameterValues must not 500.

import { expect, test } from "@playwright/test";
import { admitBlkStudent, describe, enableModules, isOk, newOrg, post, uniq, type BlkOrg } from "../../../api/blk-helpers";

function saveReport(org: BlkOrg, sqlQuery: string) {
	return post("/api/v1/reports", { name: `blk-${uniq()}`, description: "blocker spec", sqlQuery, parameters: [], columns: [] }, org.ownerToken);
}

async function runSql(org: BlkOrg, sqlQuery: string) {
	const created = await saveReport(org, sqlQuery);
	expect(isOk(created), `create: ${describe(created)}`).toBe(true);
	return post(`/api/v1/reports/${created.body.id}/run`, { parameters: {} }, org.ownerToken);
}

test.describe("SEC-H3 SqlGuard refuses session-variable and file-exfiltration SQL", () => {
	let org: BlkOrg;
	test.beforeAll(async () => {
		org = await newOrg("h3");
		await enableModules(org.organisationId, ["REPORTS"]);
	});

	const refused: [string, string][] = [
		["inline assignment of @reporting_org_id", "SELECT COUNT(*) AS c FROM (SELECT @reporting_org_id := 1) x, reporting_students"],
		["any @ user variable", "SELECT @probe AS v FROM reporting_students"],
		[":= assignment operator", "SELECT 1 AS v FROM reporting_students WHERE 1 := 1"],
		["INTO OUTFILE", "SELECT * FROM reporting_students INTO OUTFILE '/tmp/blk_h3_out.txt'"],
		["# comment outside quotes", "SELECT 1 AS v FROM reporting_students # trailing comment"],
		["SET statement", "SET @reporting_org_id = 1"],
	];

	for (const [label, sql] of refused) {
		test(`[FIXED SEC-H3] guard refuses ${label}`, async () => {
			const r = await saveReport(org, sql);
			expect(isOk(r), `guard let it through: ${describe(r)}`).toBe(false);
			expect(r.status, describe(r)).toBeGreaterThanOrEqual(400);
			expect(r.status, describe(r)).toBeLessThan(500);
			expect(typeof r.body?.error, describe(r)).toBe("string");
		});
	}

	test("[FIXED SEC-H3] a '#' inside a quoted string literal is still allowed", async () => {
		const r = await runSql(org, "SELECT '#not-a-comment' AS v FROM reporting_organisation");
		expect(isOk(r), describe(r)).toBe(true);
		expect(r.body.rows[0].v).toBe("#not-a-comment");
	});
});

test.describe("SEC-H3 reporting views only return the caller's own organisation", () => {
	test("[FIXED SEC-H3] same SELECT in two orgs returns each org's own rows only", async () => {
		const a = await newOrg("h3a");
		const b = await newOrg("h3b");
		await enableModules(a.organisationId, ["REPORTS"]);
		await enableModules(b.organisationId, ["REPORTS"]);
		await admitBlkStudent(a);
		await admitBlkStudent(b);
		await admitBlkStudent(b);

		const orgA = await runSql(a, "SELECT organisation_id FROM reporting_organisation");
		const orgB = await runSql(b, "SELECT organisation_id FROM reporting_organisation");
		expect(isOk(orgA), describe(orgA)).toBe(true);
		expect(isOk(orgB), describe(orgB)).toBe(true);
		expect(orgA.body.rows.map((row: { organisation_id: number }) => Number(row.organisation_id))).toEqual([a.organisationId]);
		expect(orgB.body.rows.map((row: { organisation_id: number }) => Number(row.organisation_id))).toEqual([b.organisationId]);

		const countA = await runSql(a, "SELECT COUNT(*) AS c FROM reporting_students");
		const countB = await runSql(b, "SELECT COUNT(*) AS c FROM reporting_students");
		expect(Number(countA.body.rows[0].c), describe(countA)).toBe(1);
		expect(Number(countB.body.rows[0].c), describe(countB)).toBe(2);
	});
});

test.describe("AUTO-5 reports: omitted parameters must not produce a 500", () => {
	let org: BlkOrg;
	test.beforeAll(async () => {
		org = await newOrg("a5r");
		await enableModules(org.organisationId, ["REPORTS"]);
	});

	test("[OPEN AUTO-5] creating a report without a `parameters` list is not a 500", async () => {
		const r = await post("/api/v1/reports", { name: `blk-${uniq()}`, description: "x", sqlQuery: "SELECT 1 AS v FROM reporting_organisation", columns: [] }, org.ownerToken);
		expect(r.status, `server error instead of 4xx/2xx: ${describe(r)}`).toBeLessThan(500);
	});

	test("[OPEN AUTO-5] creating a report without a `columns` list is not a 500", async () => {
		const r = await post("/api/v1/reports", { name: `blk-${uniq()}`, description: "x", sqlQuery: "SELECT 1 AS v FROM reporting_organisation", parameters: [] }, org.ownerToken);
		expect(r.status, `server error instead of 4xx/2xx: ${describe(r)}`).toBeLessThan(500);
	});

	test("[OPEN AUTO-5] running a report without `parameters` in the body is not a 500", async () => {
		const created = await saveReport(org, "SELECT 1 AS v FROM reporting_organisation");
		expect(isOk(created), describe(created)).toBe(true);
		const r = await post(`/api/v1/reports/${created.body.id}/run`, {}, org.ownerToken);
		expect(r.status, `server error instead of 4xx/2xx: ${describe(r)}`).toBeLessThan(500);
	});
});
