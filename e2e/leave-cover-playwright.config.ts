// Runs only tests/flows/leave-cover.spec.ts (leave cover: uncovered periods, auto-suggest, leave inbox,
// permissions, edge cases). It reuses global-setup.ts - a fresh organisation, the role fixtures and the
// academic seed - so the stack it points at must be the usual isolated one. Usage (ports are examples):
//   E2E_API_BASE_URL=http://localhost:8092 E2E_FRONTEND_BASE_URL=http://localhost:5192 \
//     npx playwright test -c leave-cover-playwright.config.ts
// Screenshots land in test-results/leave-cover/. outputDir is a different folder on purpose: Playwright
// empties its outputDir at the start of each run, which would otherwise delete those screenshots.
import { defineConfig, devices } from "@playwright/test";
import { FRONTEND_BASE_URL } from "./env";

export default defineConfig({
	testDir: "./tests",
	testMatch: ["flows/leave-cover.spec.ts"],
	fullyParallel: false,
	workers: 1,
	retries: 0,
	timeout: 180_000,
	reporter: [["list"]],
	globalSetup: "./global-setup.ts",
	outputDir: "./test-results/leave-cover-run",
	use: {
		...devices["Desktop Chrome"],
		baseURL: FRONTEND_BASE_URL,
		trace: "retain-on-failure",
		screenshot: "only-on-failure",
	},
});
