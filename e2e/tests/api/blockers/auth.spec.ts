// SEC-M2 (FIXED): password reset works end to end and does not enumerate accounts.
// SEC-M3 (OPEN): public token endpoints need a rate limit.
// SEC-L1 (OPEN): login must not reveal which accounts exist.
//
// Failed-login and reset-request volumes are kept small: AuthRateLimiter is per client IP
// and shared by every spec running against the same backend.

import { expect, test } from "@playwright/test";
import { BLK_PASSWORD, describe, isOk, newOrg, newStaff, post, raw, resetTokenFromLog, uniq, type BlkOrg, type Raw } from "../../../api/blk-helpers";

const login = (slug: string, email: string, password: string) => post("/api/v1/auth/login", { organisationSlug: slug, email, password });

test.describe.serial("SEC-M2 password reset", () => {
	let org: BlkOrg;
	let knownEmail: string;
	let knownResponse: Raw;
	const newPassword = "BlkResetPw!98765";

	test.beforeAll(async () => {
		org = await newOrg("m2");
		// Login needs an active membership, so the account gets a (permission-less) role.
		const user = await newStaff(org, "m2-user", []);
		knownEmail = user.email;
	});

	test("[FIXED SEC-M2] reset works end to end: request, confirm with the token, old password dies, new one logs in", async () => {
		knownResponse = await post("/api/v1/auth/password-reset/request", { organisationSlug: org.slug, email: knownEmail });
		expect(isOk(knownResponse), describe(knownResponse)).toBe(true);

		// The token is only read from the dev log to drive the flow forward (APP_DEV_LOGAUTHTOKENS=true).
		const token = await resetTokenFromLog(org.slug);
		test.skip(token === undefined, "dev token log line not found - backend not started with APP_DEV_LOGAUTHTOKENS=true or log path differs (BLK_BACKEND_LOG)");

		const confirm = await post("/api/v1/auth/password-reset/confirm", { organisationSlug: org.slug, token, newPassword });
		expect(isOk(confirm), describe(confirm)).toBe(true);

		const fresh = await login(org.slug, knownEmail, newPassword);
		expect(fresh.status, describe(fresh)).toBe(200);
		expect(typeof fresh.body?.token).toBe("string");

		const old = await login(org.slug, knownEmail, BLK_PASSWORD);
		expect(old.status, describe(old)).toBe(401);

		const reuse = await post("/api/v1/auth/password-reset/confirm", { organisationSlug: org.slug, token, newPassword: "BlkAnotherPw!11111" });
		expect(isOk(reuse), `reset token was reusable: ${describe(reuse)}`).toBe(false);
	});

	test("[FIXED SEC-M2] reset request for an unknown email answers exactly like a known one", async () => {
		const unknown = await post("/api/v1/auth/password-reset/request", { organisationSlug: org.slug, email: `nobody-${uniq()}@${org.slug}.test` });
		expect(knownResponse, "previous test did not capture the known-email response").toBeTruthy();
		expect(unknown.status, describe(unknown)).toBe(knownResponse.status);
		expect(unknown.text).toBe(knownResponse.text);
	});
});

test.describe("SEC-M3 public token endpoints are rate limited", () => {
	const ATTEMPTS = 35;
	// Per-IP limiters are shared by every spec on this backend. The hammering goes through the
	// IPv4 loopback address so it lands in a different bucket from the localhost (IPv6) traffic
	// used by setup calls and by other specs; the limiter is keyed on the remote address only.
	const HAMMER_BASE = (process.env.E2E_API_BASE_URL ?? "http://localhost:8090").replace("localhost", "127.0.0.1");
	let org: BlkOrg;

	test.beforeAll(async () => {
		org = await newOrg("m3");
	});

	async function hammer(path: string, body: (i: number) => unknown): Promise<{ firstThrottledAt: number | null; statuses: number[] }> {
		const statuses: number[] = [];
		for (let i = 0; i < ATTEMPTS; i++) {
			const r = await raw("POST", path, { body: body(i), baseUrl: HAMMER_BASE });
			statuses.push(r.status);
			if (r.status === 429) {
				return { firstThrottledAt: i + 1, statuses };
			}
		}
		return { firstThrottledAt: null, statuses };
	}

	const cases: [string, string, (i: number) => unknown][] = [
		["claim-invite (portal)", "/api/v1/auth/claim-invite", (i) => ({ organisationSlug: org.slug, token: `bad-token-${i}-${uniq()}`, password: "BlkClaimPw!12345" })],
		["claim-staff-invite", "/api/v1/auth/claim-staff-invite", (i) => ({ organisationSlug: org.slug, token: `bad-token-${i}-${uniq()}`, password: "BlkClaimPw!12345" })],
		["password-reset confirm", "/api/v1/auth/password-reset/confirm", (i) => ({ organisationSlug: org.slug, token: `bad-token-${i}-${uniq()}`, newPassword: "BlkResetPw!12345" })],
		["refresh", "/api/v1/auth/refresh", (i) => ({ organisationSlug: org.slug, refreshToken: `bad-token-${i}-${uniq()}` })],
	];

	for (const [label, path, body] of cases) {
		test(`[OPEN SEC-M3] repeated bad ${label} attempts are eventually answered 429`, async () => {
			const { firstThrottledAt, statuses } = await hammer(path, body);
			expect(firstThrottledAt, `no 429 in ${ATTEMPTS} bad attempts; statuses seen: ${[...new Set(statuses)].join(",")}`).not.toBeNull();
		});
	}
});

test.describe("SEC-L1 login does not reveal which accounts exist", () => {
	let org: BlkOrg;
	let email: string;

	test.beforeAll(async () => {
		org = await newOrg("l1");
		email = (await newStaff(org, "l1-user", [])).email;
	});

	test("[OPEN SEC-L1] unknown email and wrong password give the same status and body", async () => {
		const unknown = await login(org.slug, `ghost-${uniq()}@${org.slug}.test`, "Wrong-Password-1!");
		const wrong = await login(org.slug, email, "Wrong-Password-1!");
		expect(unknown.status, `unknown: ${describe(unknown)} / wrong: ${describe(wrong)}`).toBe(wrong.status);
		expect(unknown.text).toBe(wrong.text);
	});

	test("[OPEN SEC-L1] unknown email and wrong password take comparable time (no skipped bcrypt)", async () => {
		const median = (xs: number[]) => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)];
		const timed = async (e: string) => {
			const start = performance.now();
			await login(org.slug, e, "Wrong-Password-2!");
			return performance.now() - start;
		};
		// 3 samples each: per-account lockout trips at 5 failures, so stay under it for the real account.
		const unknownTimes: number[] = [];
		const knownTimes: number[] = [];
		for (let i = 0; i < 3; i++) {
			unknownTimes.push(await timed(`ghost-${uniq()}@${org.slug}.test`));
			knownTimes.push(await timed(email));
		}
		const gap = median(knownTimes) - median(unknownTimes);
		expect(gap, `known-account median ${median(knownTimes).toFixed(0)}ms vs unknown ${median(unknownTimes).toFixed(0)}ms (a bcrypt-sized gap reveals account existence)`).toBeLessThan(40);
	});
});

// raw is re-exported for ad-hoc debugging of this file only.
void raw;
