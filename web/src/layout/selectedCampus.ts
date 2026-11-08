import { useMemo, useSyncExternalStore } from "react";

/** The campus picked in the top bar. A tiny shared store (not React context) so any screen can
 * read it without extra provider wiring; the top-bar picker is the only writer, and the choice
 * persists per-browser like the picker's own convenience state.
 *
 * `null` means "All campuses" - no filtering. A number is that campus, and screens show only its data. */
export const CAMPUS_KEY = "operion.selectedCampusId";

/** What the "All campuses" choice is stored as; an older build only ever stored a campus id. */
const ALL_STORED = "all";

type StoredCampus = number | typeof ALL_STORED | null;

/** The persisted choice: a campus id, "all", or null when nothing (usable) is stored. */
export function readStoredCampus(): StoredCampus {
	try {
		const raw = localStorage.getItem(CAMPUS_KEY);
		if (raw === ALL_STORED) return ALL_STORED;
		const id = raw ? Number(raw) : NaN;
		return Number.isFinite(id) && id > 0 ? id : null;
	} catch {
		return null;
	}
}

export function writeStoredCampus(id: number | null) {
	try {
		localStorage.setItem(CAMPUS_KEY, id === null ? ALL_STORED : String(id));
	} catch {
		// per-viewer convenience only - fine to no-op when storage is unavailable
	}
}

/** Picks the campus to start on once the organisation's campuses are known. A single-campus organisation
 * has nothing to choose between, so it is always on its one campus (as before the "All" option existed).
 * Otherwise a stored choice wins - "all", or a campus that still exists; a stored campus that has since
 * been deleted falls back to All, and with nothing stored the first campus is the default. */
export function resolveCampusSelection(stored: StoredCampus, campusIds: number[]): number | null {
	if (campusIds.length <= 1) return campusIds[0] ?? null;
	if (stored === ALL_STORED) return null;
	if (stored === null) return campusIds[0];
	return campusIds.includes(stored) ? stored : null;
}

// Starts from the stored campus so the first screens already filter correctly; the picker re-resolves
// it against the real campus list as soon as that loads (and corrects a stale id).
const storedAtStart = readStoredCampus();
let selectedCampusId: number | null = typeof storedAtStart === "number" ? storedAtStart : null;
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

/** null = All campuses (also the value before the picker has loaded the campuses) - callers then show everything. */
export function useSelectedCampusId(): number | null {
	return useSyncExternalStore(subscribe, () => selectedCampusId);
}

/** Strict campus filter: a picked campus keeps only its own items; null (All) keeps everything. */
export function filterByCampus<T extends { campusId: number }>(items: T[], campusId: number | null): T[] {
	return campusId === null ? items : items.filter((item) => item.campusId === campusId);
}

/** `items` limited to the campus picked in the top bar (all of them under "All campuses"). */
export function useCampusFiltered<T extends { campusId: number }>(items: T[]): T[] {
	const campusId = useSelectedCampusId();
	return useMemo(() => filterByCampus(items, campusId), [items, campusId]);
}
