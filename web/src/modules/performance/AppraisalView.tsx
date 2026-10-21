import { useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import type { Appraisal, FeedbackInput, FeedbackSource, Goal, GoalStatus } from "../../api/performance";
import { ScorecardTable } from "./ScorecardTable";

const GOAL_STATUSES: GoalStatus[] = ["OPEN", "ACHIEVED", "PARTLY_ACHIEVED", "MISSED"];
const STATUS_LABEL: Record<GoalStatus, string> = { OPEN: "Open", ACHIEVED: "Achieved", PARTLY_ACHIEVED: "Partly achieved", MISSED: "Missed" };
const SOURCE_LABEL: Record<FeedbackSource, string> = { SELF: "Self-appraisal", MANAGER: "Manager", PEER: "Peer" };

export interface AppraisalViewProps {
	appraisal: Appraisal;
	/** Sources the viewer may rate as; empty means the viewer rates only themself (a self-appraisal). */
	sources: FeedbackSource[];
	canRate: boolean;
	canAddGoal: boolean;
	canMarkGoals: boolean;
	onRate: (input: FeedbackInput) => Promise<unknown>;
	onAddGoal: (title: string, description: string) => Promise<unknown>;
	onMarkGoal: (goal: Goal, status: GoalStatus) => Promise<unknown>;
}

/** One teacher's appraisal for one cycle: the scorecard over the cycle's dates, ratings, and goals. Shared by the administrator dialog and the teacher's own page. */
export function AppraisalView({ appraisal, sources, canRate, canAddGoal, canMarkGoals, onRate, onAddGoal, onMarkGoal }: AppraisalViewProps) {
	const open = appraisal.cycle.status === "OPEN";
	const [source, setSource] = useState<FeedbackSource>(sources[0] ?? "SELF");
	const [rating, setRating] = useState("3");
	const [comment, setComment] = useState("");
	const [goalTitle, setGoalTitle] = useState("");
	const [goalDescription, setGoalDescription] = useState("");
	const [error, setError] = useState<string | null>(null);

	function run(action: () => Promise<unknown>, after?: () => void) {
		setError(null);
		action()
			.then(() => after?.())
			.catch((err) => setError(err instanceof ApiError ? err.message : "That did not work"));
	}

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error">{error}</Alert>}
			<Typography component="div" variant="body2" color="text.secondary">
				{appraisal.cycle.startDate} to {appraisal.cycle.endDate} <Chip size="small" label={appraisal.cycle.status === "OPEN" ? "Open" : "Closed"} sx={{ ml: 1 }} />
			</Typography>
			{appraisal.scorecard.metrics.length > 0 ? (
				<ScorecardTable scorecard={appraisal.scorecard} />
			) : (
				<Alert severity="info">No metrics are being tracked, so there is no scorecard for this cycle.</Alert>
			)}

			<Divider />
			<Typography variant="subtitle1">Ratings</Typography>
			{appraisal.feedback.length === 0 && <Typography variant="body2" color="text.secondary">No ratings to show yet.</Typography>}
			{appraisal.feedback.map((f) => (
				<Typography key={f.id} variant="body2">
					<strong>{SOURCE_LABEL[f.source]}{f.reviewerName ? ` · ${f.reviewerName}` : ""}:</strong> {f.rating}/5{f.comment ? ` — ${f.comment}` : ""}
				</Typography>
			))}
			{canRate && open && (
				<Stack direction="row" spacing={1} sx={{ alignItems: "flex-start", flexWrap: "wrap" }}>
					{sources.length > 0 && (
						<TextField select size="small" label="Rating as" value={source} onChange={(e) => setSource(e.target.value as FeedbackSource)} sx={{ minWidth: 130 }}>
							{sources.map((s) => <MenuItem key={s} value={s}>{SOURCE_LABEL[s]}</MenuItem>)}
						</TextField>
					)}
					<TextField select size="small" label="Rating" value={rating} onChange={(e) => setRating(e.target.value)} sx={{ minWidth: 90 }}>
						{[1, 2, 3, 4, 5].map((n) => <MenuItem key={n} value={String(n)}>{n}</MenuItem>)}
					</TextField>
					<TextField size="small" label="Comment" value={comment} onChange={(e) => setComment(e.target.value)} sx={{ flexGrow: 1, minWidth: 200 }} />
					<Button variant="contained" onClick={() => run(() => onRate({ source: sources.length > 0 ? source : undefined, rating: Number(rating), comment }), () => setComment(""))}>
						{sources.length > 0 ? "Save rating" : "Save self-appraisal"}
					</Button>
				</Stack>
			)}

			<Divider />
			<Typography variant="subtitle1">Goals</Typography>
			{appraisal.goals.length === 0 && <Typography variant="body2" color="text.secondary">No goals set.</Typography>}
			{appraisal.goals.map((g) => (
				<Stack key={g.id} role="group" aria-label={g.title} direction="row" spacing={2} sx={{ alignItems: "center" }}>
					<Typography variant="body2" sx={{ flexGrow: 1 }}>
						<strong>{g.title}</strong>{g.description ? ` — ${g.description}` : ""}
					</Typography>
					{canMarkGoals ? (
						<TextField select size="small" value={g.status} onChange={(e) => run(() => onMarkGoal(g, e.target.value as GoalStatus))} sx={{ minWidth: 160 }}>
							{GOAL_STATUSES.map((s) => <MenuItem key={s} value={s}>{STATUS_LABEL[s]}</MenuItem>)}
						</TextField>
					) : (
						<Chip size="small" label={STATUS_LABEL[g.status]} />
					)}
				</Stack>
			))}
			{canAddGoal && open && (
				<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
					<TextField size="small" label="Goal" value={goalTitle} onChange={(e) => setGoalTitle(e.target.value)} sx={{ minWidth: 220 }} />
					<TextField size="small" label="Details" value={goalDescription} onChange={(e) => setGoalDescription(e.target.value)} sx={{ flexGrow: 1, minWidth: 200 }} />
					<Button variant="outlined" onClick={() => run(() => onAddGoal(goalTitle, goalDescription), () => { setGoalTitle(""); setGoalDescription(""); })}>
						Add goal
					</Button>
				</Stack>
			)}
		</Stack>
	);
}
