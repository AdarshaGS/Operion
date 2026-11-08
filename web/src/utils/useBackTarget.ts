import { useLocation } from "react-router-dom";

/** Router state the Dashboard's setup card attaches when it opens a setup screen. It lives in that one
 * history entry only - it is not stored anywhere, so the next navigation (or a plain visit to the same
 * screen from the sidebar) carries nothing and the screen's normal Back target applies again. */
export const FROM_DASHBOARD_STATE = { from: "dashboard" } as const;

/** Where a screen's Back / Exit control should go. A screen opened from the Dashboard setup card always
 * returns to /dashboard - never to another place in the setup flow - otherwise to `defaultPath`.
 * `fromDashboard` lets the control relabel itself ("Back to dashboard"). */
export function useBackTarget(defaultPath: string): { path: string; fromDashboard: boolean } {
	const state = useLocation().state as { from?: string } | null;
	const fromDashboard = state?.from === FROM_DASHBOARD_STATE.from;
	return { path: fromDashboard ? "/dashboard" : defaultPath, fromDashboard };
}
