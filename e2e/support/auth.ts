// Reads a role fixture's bearer token straight out of the storageState global-setup wrote
// for it (see global-setup.ts's tenantStorageState) - for specs that need to hit the
// backend directly (request context, not the UI) with a specific role's credentials.
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { AUTH_DIR } from "../env";
import type { FixtureRoleName } from "../fixtures/roles";

export function tokenFor(role: Exclude<FixtureRoleName, "platformAdmin">): string {
	const state = JSON.parse(readFileSync(join(AUTH_DIR, `${role}.json`), "utf-8"));
	const raw = state.origins[0].localStorage.find((entry: { name: string }) => entry.name === "operion.session").value;
	return JSON.parse(raw).token;
}
