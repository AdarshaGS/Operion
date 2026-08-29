// Shared browser-console / network-failure watchdog for the long-form role flows in
// tests/flows/. Attach once per page via trackDiagnostics(page), keep working, then call
// assertClean() at checkpoints. Only real failures fail the test: deliberate 401/403/404
// assertions (RBAC boundary checks) are expected traffic in these flows and are not flagged -
// only connection-level failures and 5xx responses count as "failed network requests", per
// the ticket's wording, and only console.error (not warn/log) counts as a console error.
import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";

// Known-benign dev-mode noise that isn't a real bug - extend as new false positives turn up.
// "Failed to load resource" is Chromium's own synthetic console.error echo of ANY non-2xx
// fetch/XHR response (401/403/404 included) - it duplicates response-status tracking below,
// which already applies the right bar (5xx / connection failure only, since a deliberate
// 401/403/404 is expected traffic in RBAC-boundary and "not found yet" checks throughout
// these flows). Without this filter every intentional non-2xx assertion would also register
// as a false "console error".
const IGNORED_CONSOLE_PATTERNS: RegExp[] = [/Download the React DevTools/i, /^Failed to load resource:/];

export interface Diagnostics {
	assertClean(context?: string): void;
}

export function trackDiagnostics(page: Page): Diagnostics {
	const consoleErrors: string[] = [];
	const failedRequests: string[] = [];

	page.on("console", (msg) => {
		if (msg.type() !== "error") return;
		const text = msg.text();
		if (IGNORED_CONSOLE_PATTERNS.some((pattern) => pattern.test(text))) return;
		consoleErrors.push(text);
	});

	// Uncaught JS exceptions - always a real bug, never HTTP-status noise.
	page.on("pageerror", (error) => {
		consoleErrors.push(`Uncaught exception: ${error.message}`);
	});

	page.on("requestfailed", (request) => {
		failedRequests.push(`${request.method()} ${request.url()} — ${request.failure()?.errorText ?? "failed"}`);
	});

	page.on("response", (response) => {
		if (response.status() >= 500) {
			failedRequests.push(`${response.status()} ${response.request().method()} ${response.url()}`);
		}
	});

	return {
		assertClean(context?: string) {
			const label = context ? ` (${context})` : "";
			expect(consoleErrors, `Unexpected browser console errors${label}:\n${consoleErrors.join("\n")}`).toEqual([]);
			expect(failedRequests, `Unexpected failed/5xx network requests${label}:\n${failedRequests.join("\n")}`).toEqual([]);
		},
	};
}
