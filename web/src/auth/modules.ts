/**
 * Feature (module) switches the platform owner sets per organisation (GitHub #198). The
 * backend is the source of truth and enforces them on every API call; this file only maps
 * routes to module codes so the UI can hide what the backend would refuse. Codes match the
 * backend's AppModule enum and arrive as `disabledModules` on /auth/me - a code missing
 * from that list means enabled, so an old backend (no field) leaves everything visible.
 *
 * Path prefixes, not exact paths: "/fees" covers "/fees/collect", "/fees/setup", ...
 */
const PATH_MODULES: ReadonlyArray<readonly [string, string]> = [
	["/dashboard", "DASHBOARD"],
	["/students", "STUDENTS"],
	["/admissions", "STUDENTS"],
	["/academics", "ACADEMICS"],
	["/attendance", "ATTENDANCE"],
	["/examinations", "EXAMINATIONS"],
	["/library", "LIBRARY"],
	["/learning", "LEARNING"],
	["/fees", "FEES"],
	["/transport", "TRANSPORT"],
	["/communication", "COMMUNICATION"],
	["/messaging", "MESSAGING"],
	["/inventory", "INVENTORY"],
	["/purchase", "PURCHASE"],
	["/sales", "SALES"],
	["/reports", "REPORTS"],
	["/hr", "HR"],
	["/payroll", "PAYROLL"],
];

/** The module a route belongs to, or null for core areas (settings, members, profile, ...) that can't be switched off. */
export function moduleForPath(path: string): string | null {
	const match = PATH_MODULES.find(([prefix]) => path === prefix || path.startsWith(prefix + "/"));
	return match ? match[1] : null;
}
