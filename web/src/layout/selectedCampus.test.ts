import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	CAMPUS_KEY,
	filterByCampus,
	readStoredCampus,
	resolveCampusSelection,
	setSelectedCampusId,
	useCampusFiltered,
	useSelectedCampusId,
	writeStoredCampus,
} from "./selectedCampus";

beforeEach(() => {
	localStorage.clear();
	act(() => setSelectedCampusId(null));
});

afterEach(() => {
	vi.resetModules();
});

describe("stored campus choice", () => {
	it("reads nothing when nothing, or garbage, is stored", () => {
		expect(readStoredCampus()).toBeNull();
		localStorage.setItem(CAMPUS_KEY, "abc");
		expect(readStoredCampus()).toBeNull();
		localStorage.setItem(CAMPUS_KEY, "0");
		expect(readStoredCampus()).toBeNull();
	});

	it("round-trips a campus id and the All choice", () => {
		writeStoredCampus(7);
		expect(localStorage.getItem(CAMPUS_KEY)).toBe("7");
		expect(readStoredCampus()).toBe(7);
		writeStoredCampus(null);
		expect(localStorage.getItem(CAMPUS_KEY)).toBe("all");
		expect(readStoredCampus()).toBe("all");
	});

	it("still reads the plain campus id an older build stored", () => {
		localStorage.setItem(CAMPUS_KEY, "2");
		expect(readStoredCampus()).toBe(2);
	});
});

describe("resolveCampusSelection", () => {
	it("keeps a stored campus that still exists", () => {
		expect(resolveCampusSelection(2, [1, 2])).toBe(2);
	});

	it("keeps All when All was stored", () => {
		expect(resolveCampusSelection("all", [1, 2])).toBeNull();
	});

	it("falls back to All when the stored campus no longer exists", () => {
		expect(resolveCampusSelection(9, [1, 2])).toBeNull();
	});

	it("defaults to the first campus when nothing is stored", () => {
		expect(resolveCampusSelection(null, [4, 5])).toBe(4);
	});

	it("keeps a single-campus organisation on its one campus whatever is stored", () => {
		expect(resolveCampusSelection(null, [4])).toBe(4);
		expect(resolveCampusSelection("all", [4])).toBe(4);
		expect(resolveCampusSelection(9, [4])).toBe(4);
	});

	it("is All when the organisation has no campuses", () => {
		expect(resolveCampusSelection(3, [])).toBeNull();
	});
});

describe("selected campus store", () => {
	it("is All (null) by default and notifies subscribers when it changes", () => {
		const { result } = renderHook(() => useSelectedCampusId());
		expect(result.current).toBeNull();
		act(() => setSelectedCampusId(3));
		expect(result.current).toBe(3);
		act(() => setSelectedCampusId(null));
		expect(result.current).toBeNull();
	});

	it("starts from the stored campus so the first render already filters", async () => {
		vi.resetModules();
		localStorage.setItem(CAMPUS_KEY, "5");
		const fresh = await import("./selectedCampus");
		const { result } = renderHook(() => fresh.useSelectedCampusId());
		expect(result.current).toBe(5);
	});

	it("starts on All when All, or a stale value, is stored", async () => {
		for (const stored of ["all", "garbage"]) {
			vi.resetModules();
			localStorage.setItem(CAMPUS_KEY, stored);
			const fresh = await import("./selectedCampus");
			const { result } = renderHook(() => fresh.useSelectedCampusId());
			expect(result.current).toBeNull();
		}
	});
});

const classes = [
	{ id: 1, campusId: 1, name: "LKG Main" },
	{ id: 2, campusId: 2, name: "LKG Branch" },
	{ id: 3, campusId: 1, name: "UKG Main" },
];

describe("filterByCampus", () => {
	it("keeps only the picked campus's items", () => {
		expect(filterByCampus(classes, 1).map((c) => c.id)).toEqual([1, 3]);
		expect(filterByCampus(classes, 2).map((c) => c.id)).toEqual([2]);
	});

	it("keeps everything under All (null)", () => {
		expect(filterByCampus(classes, null)).toBe(classes);
	});

	it("returns nothing for a campus with no items", () => {
		expect(filterByCampus(classes, 99)).toEqual([]);
	});
});

describe("useCampusFiltered", () => {
	it("follows the campus picked in the top bar", () => {
		const { result } = renderHook(() => useCampusFiltered(classes));
		expect(result.current).toHaveLength(3);
		act(() => setSelectedCampusId(2));
		expect(result.current.map((c) => c.id)).toEqual([2]);
		act(() => setSelectedCampusId(null));
		expect(result.current).toHaveLength(3);
	});
});
