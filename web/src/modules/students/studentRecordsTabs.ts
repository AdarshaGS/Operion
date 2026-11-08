import { useMemo } from "react";
import { useAuth } from "../../auth/AuthContext";

export type RecordsTab = "documents" | "transfers" | "certificates" | "history";

export const TAB_LABELS: Record<RecordsTab, string> = {
	documents: "Documents",
	transfers: "Campus transfers",
	certificates: "Certificates",
	history: "History",
};

/** Mirrors StudentCertificatesPanel's own view check (that panel renders nothing without one of these). */
const CERTIFICATE_VIEW_PERMISSIONS = ["CERTIFICATE_VIEW", "CERTIFICATE_REQUEST", "CERTIFICATE_APPROVE", "CERTIFICATE_ISSUE"];

/** The records tabs the caller can see, in display order. Documents, transfers and history have no
 * client-side gate (the backend enforces each); certificates needs one of the view permissions. */
export function useAvailableRecordsTabs(): RecordsTab[] {
	const { hasAnyPermission } = useAuth();
	const canViewCertificates = hasAnyPermission(CERTIFICATE_VIEW_PERMISSIONS);
	return useMemo<RecordsTab[]>(
		() => ["documents", "transfers", ...(canViewCertificates ? (["certificates"] as const) : []), "history"],
		[canViewCertificates],
	);
}

export function isRecordsTab(value: string | null): value is RecordsTab {
	return value !== null && value in TAB_LABELS;
}
