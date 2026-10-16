import { useSyncExternalStore } from "react";

/** The campus picked in the top bar. A tiny shared store (not React context) so any screen can
 * read it without extra provider wiring; the top-bar picker is the only writer, and the choice
 * persists per-browser like the picker's own convenience state. */
export const CAMPUS_KEY = "operion.selectedCampusId";

let selectedCampusId: number | null = null;
const listeners = new Set<() => void>();

export function setSelectedCampusId(id: number | null) {
	if (id === selectedCampusId) return;
	selectedCampusId = id;
	listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
	listeners.add(listener);
	return () => {
		listeners.delete(listener);
	};
}

/** null until the picker has loaded the organisation's campuses - callers should then show everything rather than nothing. */
export function useSelectedCampusId(): number | null {
	return useSyncExternalStore(subscribe, () => selectedCampusId);
}
