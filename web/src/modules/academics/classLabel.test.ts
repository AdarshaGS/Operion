import { describe, expect, it } from "vitest";
import type { SchoolClassResponse } from "../../api/schoolClasses";
import { formatClassLabel } from "./classLabel";

const klass = (overrides: Partial<SchoolClassResponse> = {}): SchoolClassResponse => ({
	id: 5,
	academicYearId: 1,
	campusId: 2,
	campusName: "Peresandra Branch",
	gradeLevelId: 9,
	displayName: null,
	status: "ACTIVE",
	...overrides,
});

describe("formatClassLabel", () => {
	it("appends the campus when the organisation has more than one campus", () => {
		expect(formatClassLabel(klass(), "LKG", true)).toBe("LKG - Peresandra Branch");
	});

	it("keeps the plain name for a single-campus organisation", () => {
		expect(formatClassLabel(klass(), "LKG", false)).toBe("LKG");
	});

	it("prefers the display name over the grade level name", () => {
		expect(formatClassLabel(klass({ displayName: "Little Stars" }), "LKG", true)).toBe("Little Stars - Peresandra Branch");
		expect(formatClassLabel(klass({ displayName: "Little Stars" }), "LKG", false)).toBe("Little Stars");
	});

	it("falls back to the class id when neither name is known", () => {
		expect(formatClassLabel(klass(), undefined, false)).toBe("Class #5");
		expect(formatClassLabel(klass(), undefined, true)).toBe("Class #5 - Peresandra Branch");
	});

	it("adds no suffix when the campus name is missing", () => {
		expect(formatClassLabel(klass({ campusName: "" }), "LKG", true)).toBe("LKG");
	});
});
