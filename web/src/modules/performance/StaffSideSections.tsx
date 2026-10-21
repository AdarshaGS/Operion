import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Step from "@mui/material/Step";
import StepLabel from "@mui/material/StepLabel";
import Stepper from "@mui/material/Stepper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import AccountTreeOutlinedIcon from "@mui/icons-material/AccountTreeOutlined";
import BadgeOutlinedIcon from "@mui/icons-material/BadgeOutlined";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import type { Appraisal, StaffInfo } from "../../api/performance";
import { EmptyNote, SectionCard } from "../../components/DashboardKit";
import { colors } from "../../theme";
import { ReviewStatusChip } from "./ReviewStatusChip";
import { formatDate, formatPercent, formatPeriod, orNotSet, REVIEW_STATUS_HINT } from "./reviewFormat";

function Field({ label, children }: { label: string; children: React.ReactNode }) {
	return (
		<Box sx={{ minWidth: 0 }}>
			<Typography variant="caption" color="text.secondary" component="dt">{label}</Typography>
			<Typography variant="body2" component="dd" sx={{ m: 0, fontWeight: 600, overflowWrap: "anywhere" }}>{children}</Typography>
		</Box>
	);
}

export function StaffInformationSection({ staff, appraisal }: { staff: StaffInfo; appraisal: Appraisal }) {
	return (
		<SectionCard title="Staff information" icon={<BadgeOutlinedIcon fontSize="small" />} tint="blue">
			<Box component="dl" sx={{ m: 0, display: "grid", gap: 2, gridTemplateColumns: "repeat(2, minmax(0, 1fr))" }}>
				<Field label="Staff name">{staff.name}</Field>
				<Field label="Employee ID">{staff.employeeCode ?? "Not set"}</Field>
				<Field label="Designation">{orNotSet(staff.designation)}</Field>
				<Field label="Department">{orNotSet(staff.department)}</Field>
				<Field label="Appraisal cycle">{appraisal.cycle.name}</Field>
				<Field label="Review period">{formatPeriod(appraisal.cycle.startDate, appraisal.cycle.endDate)}</Field>
				<Field label="Appraisal status"><ReviewStatusChip status={staff.reviewStatus} /></Field>
				<Field label="Manager rating">{staff.managerRating === null ? "—" : `${staff.managerRating.toFixed(1)} / 5`}</Field>
			</Box>
		</SectionCard>
	);
}

export function PresenceSection({ staff, appraisal }: { staff: StaffInfo; appraisal: Appraisal }) {
	const target = staff.presenceTarget;
	const met = target !== null && staff.presencePercent !== null ? staff.presencePercent >= target : null;
	return (
		<SectionCard title="Staff presence" icon={<EventAvailableIcon fontSize="small" />} tint="green">
			{staff.presencePercent === null ? (
				<EmptyNote>No attendance has been recorded for {staff.name} between {formatDate(appraisal.cycle.startDate)} and {formatDate(appraisal.cycle.endDate)}.</EmptyNote>
			) : (
				<Stack spacing={1}>
					<Stack direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap", rowGap: 0.5 }}>
						<Typography sx={{ fontSize: "1.9rem", fontWeight: 800, lineHeight: 1.1, color: colors.ink, fontVariantNumeric: "tabular-nums" }}>
							{formatPercent(staff.presencePercent)}
						</Typography>
						{met !== null && <Chip size="small" variant="outlined" color={met ? "success" : "warning"} label={met ? "Target met" : "Below target"} />}
					</Stack>
					<Typography variant="body2" color="text.secondary">
						Based on {staff.presenceSample} recorded {staff.presenceSample === 1 ? "day" : "days"} in the review period
						{target !== null ? ` · target ${formatPercent(target)}` : " · no target configured"}.
					</Typography>
				</Stack>
			)}
			<Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 1.5, mb: 0 }}>
				Presence is one signal, not a verdict on performance. It reaches the overall score only if the Presence metric is switched on under Metrics.
			</Typography>
		</SectionCard>
	);
}

export interface WorkflowSectionProps {
	staff: StaffInfo;
	appraisal: Appraisal;
	canReview: boolean;
	canManage: boolean;
	onRateNow: () => void;
	onCloseCycle: () => void;
}

/** The cycle model has two steps for an appraisal - a manager rating, then the cycle closing - so that is all this shows. */
export function WorkflowSection({ staff, appraisal, canReview, canManage, onRateNow, onCloseCycle }: WorkflowSectionProps) {
	const open = appraisal.cycle.status === "OPEN";
	const reviewed = staff.reviewStatus === "REVIEWED" || staff.reviewStatus === "FINALIZED";
	const selfDone = appraisal.feedback.some((f) => f.source === "SELF");
	const activeStep = !reviewed ? 0 : open ? 1 : 2;

	let pending: string;
	if (!open) {
		pending = reviewed ? "Nothing pending. This appraisal is finalized." : "Nothing can be done: the cycle closed before a manager rating was recorded.";
	} else if (!reviewed) {
		pending = canReview ? "Pending: record a manager rating." : "Pending: waiting for a reviewer to record a manager rating.";
	} else {
		pending = canManage ? "Pending: close the cycle to finalize every appraisal in it." : "Pending: the cycle is still open; ratings can be revised until it is closed.";
	}

	return (
		<SectionCard title="Review workflow" icon={<AccountTreeOutlinedIcon fontSize="small" />} tint="violet">
			<Stack spacing={2}>
				<Stepper activeStep={activeStep} orientation="vertical" sx={{ "& .MuiStepLabel-label": { fontSize: "0.85rem" } }}>
					<Step completed={reviewed}><StepLabel>Manager rating recorded</StepLabel></Step>
					<Step completed={!open && reviewed}><StepLabel>Cycle closed — appraisal finalized</StepLabel></Step>
				</Stepper>
				<Stack spacing={0.5}>
					<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
						<Typography variant="body2" color="text.secondary">Status</Typography>
						<ReviewStatusChip status={staff.reviewStatus} />
					</Stack>
					<Typography variant="body2" color="text.secondary">{REVIEW_STATUS_HINT[staff.reviewStatus]}</Typography>
					<Typography variant="body2" sx={{ fontWeight: 600 }}>{pending}</Typography>
					<Typography variant="caption" color="text.secondary">Self-appraisal: {selfDone ? "submitted" : "not submitted"} (optional, does not change the status).</Typography>
				</Stack>
				{open && (canReview || canManage) && (
					<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", rowGap: 1 }}>
						{canReview && <Button size="small" variant={reviewed ? "outlined" : "contained"} onClick={onRateNow}>{reviewed ? "Revise rating" : "Rate now"}</Button>}
						{canManage && <Button size="small" color="warning" variant="outlined" onClick={onCloseCycle}>Close cycle</Button>}
					</Stack>
				)}
			</Stack>
		</SectionCard>
	);
}
