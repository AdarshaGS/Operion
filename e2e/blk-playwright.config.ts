// Runs only tests/api/blockers without global-setup.ts. The blocker specs provision their own
// organisations, so the shared seed (and its guardian claim-invite call, which counts against
// the per-IP claim limiter) is not needed. Usage:
//   npx playwright test -c blk-playwright.config.ts --reporter=list
import { defineConfig } from "@playwright/test";

export default defineConfig({
	testDir: "./tests/api/blockers",
	fullyParallel: true,
	retries: 0,
	reporter: [["list"]],
	outputDir: process.env.BLK_OUTPUT_DIR ?? "./test-results-blk",
	use: { trace: "off" },
});
