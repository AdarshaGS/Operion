// API-only config for the playbook specs: same as playwright.config.ts but without
// globalSetup. The playbook specs build their own organisations, so they need none of the
// shared .auth fixtures, and global-setup's parent-invite claim is rate limited
// (20 / 10 min / address) which other concurrent runs can exhaust.
import { defineConfig } from "@playwright/test";

export default defineConfig({
	testDir: "./tests",
	fullyParallel: true,
	reporter: [["list"]],
	projects: [{ name: "api", testMatch: ["api/playbook/**"] }],
});
