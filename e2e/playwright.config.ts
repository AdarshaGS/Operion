import { defineConfig, devices } from "@playwright/test";
import { FRONTEND_BASE_URL } from "./env";

const PORT = 5183;

export default defineConfig({
	testDir: "./tests",
	fullyParallel: true,
	forbidOnly: !!process.env.CI,
	retries: process.env.CI ? 1 : 0,
	reporter: [["html", { open: "never" }]],
	globalSetup: "./global-setup.ts",
	use: {
		baseURL: FRONTEND_BASE_URL,
		trace: "retain-on-failure",
		screenshot: "only-on-failure",
	},
	// No webServer block: the e2e backend (:8090) and this frontend instance (:5183) are
	// started separately (see e2e/README.md) rather than by Playwright, since the backend
	// is a Java process outside Playwright's process-spawning model.
	projects: [
		{
			name: "owner",
			testMatch: ["rbac/**", "tenant/**"],
			use: { ...devices["Desktop Chrome"], storageState: "./.auth/owner.json" },
		},
		// No storageState - auth flows (login, password reset, invite claims) start
		// logged out by design; LoginPage redirects away immediately if isAuthenticated.
		// Every tests/flows/*.spec.ts also runs unauthenticated: the ticket's "Log in as X"
		// is step 1 of each journey, so every flow drives the real login form itself rather
		// than starting from a pre-authenticated storageState (unlike every other project
		// below, where login is deliberately NOT re-tested per role - see tests/auth/).
		// institution-owner-flow.spec.ts additionally provisions its own fresh organisation
		// and builds its own browser context (see that spec) rather than reusing the shared
		// global-setup org, since it specifically exercises the setup checklist from zero -
		// something the shared org (already seeded by global-setup) can't demonstrate.
		{
			name: "unauthenticated",
			testMatch: ["auth/**", "flows/*.spec.ts"],
			use: { ...devices["Desktop Chrome"] },
		},
		// The four limited-permission projects below only run tests/rbac/** (which is
		// data-driven per role) - tests/tenant/** assumes full write access and is owner-only
		// by default. A spec that is specifically about one limited role's scoped behavior
		// belongs in tests/rbac/ (see api-enforcement.spec.ts) rather than tests/tenant/.
		{
			name: "allFunctionsAdmin",
			testMatch: ["rbac/**"],
			use: { ...devices["Desktop Chrome"], storageState: "./.auth/allFunctionsAdmin.json" },
		},
		{
			name: "readOnlyStaff",
			testMatch: ["rbac/**"],
			use: { ...devices["Desktop Chrome"], storageState: "./.auth/readOnlyStaff.json" },
		},
		{
			name: "feesCollector",
			testMatch: ["rbac/**"],
			use: { ...devices["Desktop Chrome"], storageState: "./.auth/feesCollector.json" },
		},
		{
			name: "teacher",
			testMatch: ["rbac/**"],
			use: { ...devices["Desktop Chrome"], storageState: "./.auth/teacher.json" },
		},
		{
			name: "noPermissions",
			testMatch: ["rbac/**"],
			use: { ...devices["Desktop Chrome"], storageState: "./.auth/noPermissions.json" },
		},
		{
			name: "guardian",
			testMatch: ["parent-portal/**"],
			use: { ...devices["Desktop Chrome"], storageState: "./.auth/guardian.json" },
		},
		{
			name: "platformAdmin",
			testMatch: ["platform-admin/**"],
			use: { ...devices["Desktop Chrome"], storageState: "./.auth/platformAdmin.json" },
		},
	],
});
