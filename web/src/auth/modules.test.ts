import { describe, expect, it } from "vitest";
import { moduleForPath } from "./modules";

describe("moduleForPath", () => {
	it("maps a module route and its sub-routes to the module code", () => {
		expect(moduleForPath("/fees")).toBe("FEES");
		expect(moduleForPath("/fees/collect")).toBe("FEES");
		expect(moduleForPath("/admissions/applicants")).toBe("STUDENTS");
		expect(moduleForPath("/dashboard")).toBe("DASHBOARD");
	});

	it("returns null for core routes that can never be switched off", () => {
		expect(moduleForPath("/settings/roles")).toBeNull();
		expect(moduleForPath("/members")).toBeNull();
		expect(moduleForPath("/profile")).toBeNull();
	});

	it("does not match a path that merely shares a prefix", () => {
		expect(moduleForPath("/feesx")).toBeNull();
		expect(moduleForPath("/hrx")).toBeNull();
	});
});
