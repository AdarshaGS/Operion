/** Backend ApplicantStatus values -> the labels/filters the Admissions queue shows.
 * Pending=INQUIRY, Under Review=UNDER_REVIEW, Approved=CONVERTED, Rejected=REJECTED. */
export type ApplicantStatusCode = "INQUIRY" | "UNDER_REVIEW" | "CONVERTED" | "REJECTED";
export type StatusFilter = "ALL" | ApplicantStatusCode;

export const STATUS_FILTERS: StatusFilter[] = ["ALL", "INQUIRY", "UNDER_REVIEW", "CONVERTED", "REJECTED"];

const LABELS: Record<StatusFilter, string> = {
	ALL: "All",
	INQUIRY: "Pending",
	UNDER_REVIEW: "Under Review",
	CONVERTED: "Approved",
	REJECTED: "Rejected",
};

const COLORS: Record<StatusFilter, "default" | "info" | "warning" | "success" | "error"> = {
	ALL: "default",
	INQUIRY: "info",
	UNDER_REVIEW: "warning",
	CONVERTED: "success",
	REJECTED: "error",
};

export function statusLabel(status: string): string {
	return LABELS[status as StatusFilter] ?? status;
}

export function statusColor(status: string): "default" | "info" | "warning" | "success" | "error" {
	return COLORS[status as StatusFilter] ?? "default";
}

/** Which row actions apply: Start review only from Pending; Approve/Reject while open. */
export function allowedActions(status: string): { startReview: boolean; approve: boolean; reject: boolean } {
	const open = status === "INQUIRY" || status === "UNDER_REVIEW";
	return { startReview: status === "INQUIRY", approve: open, reject: open };
}

export const DOC_TYPES = ["BIRTH_CERTIFICATE", "PREVIOUS_MARKSHEET", "TRANSFER_CERTIFICATE", "ADDRESS_PROOF", "PHOTO"] as const;

const DOC_LABELS: Record<string, string> = {
	BIRTH_CERTIFICATE: "Birth certificate",
	PREVIOUS_MARKSHEET: "Previous marksheet",
	TRANSFER_CERTIFICATE: "Transfer certificate",
	ADDRESS_PROOF: "Address proof",
	PHOTO: "Photo",
};

export function docLabel(docType: string): string {
	return DOC_LABELS[docType] ?? docType;
}

/** Public form URL for an org slug. */
export function publicFormUrl(origin: string, slug: string): string {
	return `${origin}/apply/${encodeURIComponent(slug.trim())}`;
}
