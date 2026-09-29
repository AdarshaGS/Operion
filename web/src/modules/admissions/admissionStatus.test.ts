import { describe, expect, it } from "vitest";
import { allowedActions, docLabel, publicFormUrl, statusLabel } from "./admissionStatus";

describe("admissionStatus", () => {
	it("maps backend statuses to UI labels", () => {
		expect(statusLabel("INQUIRY")).toBe("Pending");
		expect(statusLabel("UNDER_REVIEW")).toBe("Under Review");
		expect(statusLabel("CONVERTED")).toBe("Approved");
		expect(statusLabel("REJECTED")).toBe("Rejected");
		expect(statusLabel("WHATEVER")).toBe("WHATEVER");
	});

	it("gates actions by status", () => {
		expect(allowedActions("INQUIRY")).toEqual({ startReview: true, approve: true, reject: true });
		expect(allowedActions("UNDER_REVIEW")).toEqual({ startReview: false, approve: true, reject: true });
		expect(allowedActions("CONVERTED")).toEqual({ startReview: false, approve: false, reject: false });
	});

	it("builds the public link and doc labels", () => {
		expect(publicFormUrl("https://x.io", " my school ")).toBe("https://x.io/apply/my%20school");
		expect(docLabel("PHOTO")).toBe("Photo");
	});
});
