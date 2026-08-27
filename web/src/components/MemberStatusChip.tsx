import Chip from "@mui/material/Chip";

/** Same "one visual language per status family" pattern as StudentFeesPanel's
 * ASSIGNMENT_STATUS_COLOR/INVOICE_STATUS_COLOR - kept here rather than duplicated since
 * this one's shared across Settings > Users, User detail, and HR > Staff detail. */
const MEMBER_STATUS_LABEL: Record<string, string> = {
	INVITED: "Invited",
	ACTIVE: "Active",
	INACTIVE: "Inactive",
};

const MEMBER_STATUS_COLOR: Record<string, "info" | "success" | "default"> = {
	INVITED: "info",
	ACTIVE: "success",
	INACTIVE: "default",
};

export function MemberStatusChip({ status }: { status: string }) {
	return <Chip label={MEMBER_STATUS_LABEL[status] ?? status} color={MEMBER_STATUS_COLOR[status] ?? "default"} size="small" />;
}
