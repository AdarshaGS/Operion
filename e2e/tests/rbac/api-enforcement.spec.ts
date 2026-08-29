// Complements nav-visibility.spec.ts: the sidebar only reflects literal permission-code
// membership (Can.tsx/AppLayout's hasAnyPermission), which is UX sugar - the real
// boundary is PermissionInterceptor on the backend. allFunctionsAdmin is the sharpest
// example of the gap: its role holds only the ALL_FUNCTIONS bypass code, so the UI
// shows Students/Fees/etc. as disabled, yet the backend accepts the real API calls.
// This spec hits the backend directly (via request context, not the UI) to prove that
// bypass is real, and that noPermissions is genuinely blocked (not just hidden).

import { expect, test } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { API_BASE_URL } from "../../api/client";
import { AUTH_DIR } from "../../env";

function tokenFor(role: string): string {
	const state = JSON.parse(readFileSync(join(AUTH_DIR, `${role}.json`), "utf-8"));
	const raw = state.origins[0].localStorage.find((entry: { name: string }) => entry.name === "operion.session").value;
	return JSON.parse(raw).token;
}

test("allFunctionsAdmin's ALL_FUNCTIONS role is hidden in the UI but genuinely bypasses the backend", async ({ request }) => {
	const response = await request.get(`${API_BASE_URL}/api/v1/students`, {
		headers: { Authorization: `Bearer ${tokenFor("allFunctionsAdmin")}` },
	});
	expect(response.status()).toBe(200);
});

test("noPermissions is blocked by the backend itself, not just hidden by the UI", async ({ request }) => {
	const response = await request.get(`${API_BASE_URL}/api/v1/students`, {
		headers: { Authorization: `Bearer ${tokenFor("noPermissions")}` },
	});
	expect(response.status()).toBe(403);
});

test("feesCollector can collect fees but is blocked from HR, matching its tight single-module bundle", async ({ request }) => {
	const token = tokenFor("feesCollector");
	const feesResponse = await request.get(`${API_BASE_URL}/api/v1/fees/categories`, { headers: { Authorization: `Bearer ${token}` } });
	expect(feesResponse.status()).toBe(200);

	const hrResponse = await request.get(`${API_BASE_URL}/api/v1/hr/staff`, { headers: { Authorization: `Bearer ${token}` } });
	expect(hrResponse.status()).toBe(403);
});
