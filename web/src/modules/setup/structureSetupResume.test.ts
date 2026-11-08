import { describe, expect, it } from "vitest";
import { decideResume, type ResumeStep, type StepKey } from "./structureSetupResume";

const STEPS: ResumeStep[] = [
	{ key: "profile", required: true },
	{ key: "business", required: true },
	{ key: "campuses", required: false },
];

const satisfied = (...keys: StepKey[]) => new Set<StepKey>(keys);

describe("decideResume", () => {
	it("opens on the first step when nothing is done", () => {
		const result = decideResume(STEPS, satisfied(), []);
		expect(result.openIndex).toBe(0);
		expect(result.completed.size).toBe(0);
	});

	it("opens on Campuses when the required steps are satisfied but Campuses was never continued past", () => {
		const result = decideResume(STEPS, satisfied("profile", "business"), []);
		expect(result.openIndex).toBe(2);
		expect([...result.completed]).toEqual(["profile", "business"]);
	});

	it("opens on Review once Campuses was continued past as well", () => {
		const result = decideResume(STEPS, satisfied("profile", "business"), ["campuses"]);
		expect(result.openIndex).toBe(STEPS.length);
		expect([...result.completed]).toEqual(["profile", "business", "campuses"]);
	});

	it("opens on Review when the server holds every step and the data agrees", () => {
		const result = decideResume(STEPS, satisfied("profile", "business"), ["profile", "business", "campuses"]);
		expect(result.openIndex).toBe(STEPS.length);
	});

	it("lets the first unsatisfied required step win even when the server says Campuses is done", () => {
		const result = decideResume(STEPS, satisfied("profile"), ["campuses"]);
		expect(result.openIndex).toBe(1);
		expect(result.completed.has("business")).toBe(false);
		expect(result.completed.has("campuses")).toBe(true);
	});

	it("does not trust a saved required step whose data requirement is no longer met", () => {
		const result = decideResume(STEPS, satisfied(), ["profile", "business", "campuses"]);
		expect(result.openIndex).toBe(0);
		expect(result.completed.has("profile")).toBe(false);
	});

	it("ignores server keys it does not know", () => {
		const result = decideResume(STEPS, satisfied("profile", "business"), ["review", "something-else"]);
		expect(result.openIndex).toBe(2);
		expect(result.completed.size).toBe(2);
	});
});
