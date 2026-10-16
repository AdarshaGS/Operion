import Alert from "@mui/material/Alert";
import type { ReactNode } from "react";

// Stable identity on purpose: React only calls a ref callback again when it changes, so the alert
// takes focus once, when it appears, and not on every re-render while the user types.
const focusOnMount = (element: HTMLElement | null) => element?.focus();

/**
 * Error banner for inside a dialog. Disabling the submit button while a save runs drops keyboard
 * focus to the page body, where Escape no longer reaches the dialog; focusing the banner when the
 * error appears keeps focus inside the dialog so Escape still closes it.
 */
export function DialogErrorAlert({ children }: { children: ReactNode }) {
	return (
		<Alert severity="error" tabIndex={-1} ref={focusOnMount} role="alert">
			{children}
		</Alert>
	);
}
