import { describe, expect, it } from "vitest";
import type { SetupChecklist } from "../../api/dashboard";
import { isSetupComplete } from "./SetupProgress";

const NOTHING_DONE: SetupChecklist = {
	rolesConfigured: false,
	membersAdded: false,
	academicSetupConfigured: false,
	studentsAdded: false,
	feesConfigured: false,
	attendanceStarted: false,
	institutionProfileConfigured: false,
	academicYearSet: false,
	classesAndSectionsCreated: false,
	subjectsMapped: false,
	admissionsCompleted: false,
	studentRecordsExist: false,
	examCreated: false,
	marksApproved: false,
	libraryStocked: false,
	academicYearClosed: false,
};

/** Every counted onboarding step done; year closure (the "later" step) deliberately not. */
const ONBOARDING_DONE: SetupChecklist = {
	...NOTHING_DONE,
	institutionProfileConfigured: true,
	academicYearSet: true,
	membersAdded: true,
	classesAndSectionsCreated: true,
	subjectsMapped: true,
	admissionsCompleted: true,
	studentRecordsExist: true,
	attendanceStarted: true,
	feesConfigured: true,
	examCreated: true,
	marksApproved: true,
	libraryStocked: true,
};

const allEnabled = () => true;

describe("isSetupComplete", () => {
	it("is not complete on a fresh institution", () => {
		expect(isSetupComplete(NOTHING_DONE, allEnabled)).toBe(false);
	});

	it("is complete once every counted step is done, even though year closure cannot be", () => {
		expect(isSetupComplete(ONBOARDING_DONE, allEnabled)).toBe(true);
	});

	it("stays open while any onboarding step is missing", () => {
		expect(isSetupComplete({ ...ONBOARDING_DONE, marksApproved: false }, allEnabled)).toBe(false);
	});

	it("stays open until team members have been added", () => {
		expect(isSetupComplete({ ...ONBOARDING_DONE, membersAdded: false }, allEnabled)).toBe(false);
	});

	it("ignores steps whose module is switched off", () => {
		const withoutLibrary = (path: string) => path !== "/library";
		expect(isSetupComplete({ ...ONBOARDING_DONE, libraryStocked: false }, withoutLibrary)).toBe(true);
	});

	it("is complete when every module is off - nothing is left to guide through", () => {
		const noModules = () => false;
		expect(isSetupComplete(NOTHING_DONE, noModules)).toBe(true);
	});
});
