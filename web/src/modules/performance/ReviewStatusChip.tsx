import Chip from "@mui/material/Chip";
import Tooltip from "@mui/material/Tooltip";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import HourglassEmptyIcon from "@mui/icons-material/HourglassEmpty";
import type { ReviewStatus } from "../../api/performance";
import { REVIEW_STATUS_HINT, REVIEW_STATUS_LABEL } from "./reviewFormat";

const COLOR: Record<ReviewStatus, "warning" | "success" | "default"> = {
	PENDING: "warning",
	REVIEWED: "success",
	FINALIZED: "success",
	NOT_REVIEWED: "default",
};

/** One visual language for review progress: amber = waiting on a review, green = done (solid once the cycle has closed). */
export function ReviewStatusChip({ status }: { status: ReviewStatus }) {
	const done = status === "REVIEWED" || status === "FINALIZED";
	return (
		<Tooltip title={REVIEW_STATUS_HINT[status]}>
			<Chip
				size="small"
				color={COLOR[status]}
				variant={status === "FINALIZED" ? "filled" : "outlined"}
				icon={done ? <CheckCircleIcon /> : status === "PENDING" ? <HourglassEmptyIcon /> : undefined}
				label={REVIEW_STATUS_LABEL[status]}
			/>
		</Tooltip>
	);
}
