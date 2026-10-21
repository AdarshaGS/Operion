import { useEffect, useRef, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import LinearProgress from "@mui/material/LinearProgress";
import MenuItem from "@mui/material/MenuItem";
import Rating from "@mui/material/Rating";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import AssessmentOutlinedIcon from "@mui/icons-material/AssessmentOutlined";
import { ApiError } from "../../api/client";
import type { Appraisal, Feedback, FeedbackInput, FeedbackSource } from "../../api/performance";
import { EmptyNote, SectionCard } from "../../components/DashboardKit";
import { colors } from "../../theme";
import { formatScore, formatValue, targetLabel } from "./kpiFormat";
import { formatDate } from "./reviewFormat";

const SOURCE_LABEL: Record<FeedbackSource, string> = { SELF: "Self-appraisal", MANAGER: "Manager", PEER: "Peer" };
const RATING_WORDS = ["", "Needs significant improvement", "Below expectations", "Meets expectations", "Exceeds expectations", "Outstanding"];

export interface EvaluationSectionProps {
	appraisal: Appraisal;
	canRate: boolean;
	onRate: (input: FeedbackInput) => Promise<unknown>;
	/** Lets the workflow card jump here. */
	formId: string;
}

/**
 * The scored part of an appraisal: the metrics the organisation tracks (each with its weight and target), then the
 * reviewers' 1-5 ratings. The metric scores are calculated from records and are not editable; only the ratings are entered.
 */
export function EvaluationSection({ appraisal, canRate, onRate, formId }: EvaluationSectionProps) {
	const open = appraisal.cycle.status === "OPEN";
	// Held here so it survives the reload that follows a save, and is cleared when another cycle or person is shown.
	const [saved, setSaved] = useState(false);
	const row = appraisal.scorecard.teachers[0] ?? null;
	const staffId = row?.personId;
	useEffect(() => setSaved(false), [appraisal.cycle.id, staffId]);
	const metrics = appraisal.scorecard.metrics;
	const totalWeight = metrics.reduce((sum, m) => sum + m.weight, 0);

	return (
		<SectionCard title="Performance evaluation" icon={<AssessmentOutlinedIcon fontSize="small" />} tint="violet">
			<Stack spacing={2.5}>
				<Stack direction="row" spacing={2} sx={{ alignItems: "center", flexWrap: "wrap", rowGap: 1 }}>
					<Box>
						<Typography variant="caption" color="text.secondary">Overall score</Typography>
						<Typography sx={{ fontSize: "2.2rem", fontWeight: 800, lineHeight: 1.1, color: colors.ink, fontVariantNumeric: "tabular-nums" }}>
							{row ? formatScore(row.overallScore) : "—"}
						</Typography>
					</Box>
					<Tooltip title={open
						? "The cycle is still open, so this score is provisional and moves as new records are added."
						: "The cycle is closed. The score is calculated from the records currently held for the review period."}>
						<Chip size="small" variant="outlined" color={open ? "warning" : "default"} label={open ? "Provisional" : "Cycle closed"} />
					</Tooltip>
				</Stack>

				{metrics.length === 0 ? (
					<Alert severity="info" variant="outlined">No metrics are being tracked, so there is no calculated score. Ratings below still apply.</Alert>
				) : !row ? (
					<Alert severity="info" variant="outlined">No scorecard is available: this person has no active class assignment.</Alert>
				) : (
					<TableContainer>
						<Table size="small" aria-label="Evaluation criteria" sx={{ minWidth: 540 }}>
							<TableHead>
								<TableRow>
									<TableCell>Criterion</TableCell>
									<TableCell align="right">Weight</TableCell>
									<TableCell align="right">Result</TableCell>
									<TableCell sx={{ minWidth: 150 }}>Score</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{metrics.map((m, i) => {
									const cell = row.cells[i];
									const share = totalWeight === 0 ? 0 : Math.round((m.weight / totalWeight) * 100);
									return (
										<TableRow key={m.metric}>
											<TableCell>
												<Typography variant="body2" sx={{ fontWeight: 600 }}>{m.label}</Typography>
												<Typography variant="caption" color="text.secondary" component="div">{m.description}</Typography>
												<Typography variant="caption" color="text.secondary">{targetLabel(m)}</Typography>
											</TableCell>
											<TableCell align="right">{share}%</TableCell>
											<TableCell align="right" sx={{ fontVariantNumeric: "tabular-nums" }}>
												{formatValue(cell.value, m.unit)}
												{cell.previous !== null && (
													<Typography component="div" variant="caption" color="text.secondary">was {formatValue(cell.previous, m.unit)}</Typography>
												)}
												<Typography component="div" variant="caption" color="text.secondary" sx={{ whiteSpace: "nowrap" }}>{cell.sample ? `${cell.sample} records` : "No records"}</Typography>
											</TableCell>
											<TableCell>
												{cell.score === null ? (
													<Typography variant="caption" color="text.secondary">Not scored — left out of the overall score</Typography>
												) : (
													<Stack spacing={0.5}>
														<Typography variant="caption" sx={{ fontVariantNumeric: "tabular-nums" }}>{formatScore(cell.score)}</Typography>
														<LinearProgress variant="determinate" value={Math.min(100, cell.score)} sx={{ height: 5, borderRadius: 3 }} aria-label={`${m.label} score`} />
													</Stack>
												)}
											</TableCell>
										</TableRow>
									);
								})}
							</TableBody>
						</Table>
					</TableContainer>
				)}
				{metrics.length > 0 && (
					<Typography variant="caption" color="text.secondary">
						The overall score is the weighted average of the criteria that have a score. A criterion with no records is left out, never counted as zero.
					</Typography>
				)}

				<Divider />
				<Typography variant="subtitle1" component="h3" sx={{ fontWeight: 700 }}>Reviewer ratings</Typography>
				{appraisal.feedback.length === 0 ? (
					<EmptyNote>No ratings have been recorded for this cycle yet.</EmptyNote>
				) : (
					<Stack spacing={1.5} divider={<Divider flexItem />}>
						{appraisal.feedback.map((f) => <RatingRow key={f.id} feedback={f} />)}
					</Stack>
				)}

				{canRate && open && <RatingForm key={`${appraisal.cycle.id}:${staffId}`} feedback={appraisal.feedback.find((f) => f.mine) ?? null} onRate={onRate} formId={formId} saved={saved} onSavedChange={setSaved} />}
				{canRate && !open && (
					<Alert severity="info" variant="outlined">This cycle is closed, so ratings can no longer be added or changed.</Alert>
				)}
				{!canRate && open && (
					<Typography variant="caption" color="text.secondary">You can view ratings but do not have permission to record them.</Typography>
				)}
			</Stack>
		</SectionCard>
	);
}

function RatingRow({ feedback }: { feedback: Feedback }) {
	return (
		<Stack spacing={0.5}>
			<Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap", rowGap: 0.5 }}>
				<Chip size="small" label={SOURCE_LABEL[feedback.source]} variant={feedback.source === "MANAGER" ? "filled" : "outlined"} color={feedback.source === "MANAGER" ? "primary" : "default"} />
				{feedback.reviewerName && <Typography variant="body2" sx={{ fontWeight: 600 }}>{feedback.reviewerName}{feedback.mine ? " (you)" : ""}</Typography>}
				<Rating value={feedback.rating} readOnly size="small" aria-label={`Rated ${feedback.rating} out of 5`} />
				<Typography variant="body2" color="text.secondary">{feedback.rating}/5 · {RATING_WORDS[feedback.rating]}</Typography>
				<Typography variant="caption" color="text.secondary" sx={{ ml: "auto !important" }}>{formatDate(feedback.updatedAt)}</Typography>
			</Stack>
			{feedback.comment ? (
				<Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>{feedback.comment}</Typography>
			) : (
				<Typography variant="body2" color="text.secondary">No comment.</Typography>
			)}
		</Stack>
	);
}

/** One rating per reviewer per cycle: saving again replaces your earlier rating, so the form opens pre-filled with it. */
function RatingForm({ feedback, onRate, formId, saved, onSavedChange }: { feedback: Feedback | null; onRate: (input: FeedbackInput) => Promise<unknown>; formId: string; saved: boolean; onSavedChange: (saved: boolean) => void }) {
	const [source, setSource] = useState<FeedbackSource>(feedback && feedback.source !== "SELF" ? feedback.source : "MANAGER");
	const [rating, setRating] = useState<number | null>(feedback?.rating ?? null);
	const [comment, setComment] = useState(feedback?.comment ?? "");
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const mounted = useRef(false);

	// Editing after a save clears the confirmation; the remount that follows a save must not.
	useEffect(() => {
		if (mounted.current) {
			onSavedChange(false);
		}
		mounted.current = true;
	}, [source, rating, comment, onSavedChange]);

	const unchanged = feedback !== null && feedback.source === source && feedback.rating === rating && (feedback.comment ?? "") === comment.trim();

	function save() {
		if (rating === null || saving) {
			return;
		}
		setSaving(true);
		setError(null);
		onRate({ source, rating, comment })
			.then(() => onSavedChange(true))
			.catch((err) => setError(err instanceof ApiError ? err.message : "The rating could not be saved"))
			.finally(() => setSaving(false));
	}

	return (
		<Box id={formId} component="form" onSubmit={(e) => { e.preventDefault(); save(); }}
			sx={{ p: 2, borderRadius: 2, border: `1px solid ${colors.rule}`, backgroundColor: colors.paperSunken }}>
			<Stack spacing={2}>
				<Typography variant="subtitle2" component="h4">{feedback ? "Update your rating" : "Add your rating"}</Typography>
				{error && <Alert severity="error">{error}</Alert>}
				{saved && <Alert severity="success">Rating saved.</Alert>}
				<Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ alignItems: { sm: "center" } }}>
					<TextField select size="small" label="Rating as" value={source} onChange={(e) => setSource(e.target.value as FeedbackSource)} sx={{ minWidth: 150 }}>
						<MenuItem value="MANAGER">Manager</MenuItem>
						<MenuItem value="PEER">Peer</MenuItem>
					</TextField>
					<Stack direction="row" sx={{ alignItems: "center", flexWrap: "wrap", columnGap: 1, rowGap: 0.5 }}>
						<Rating name={`${formId}-rating`} value={rating} onChange={(_e, value) => setRating(value)} />
						<Typography variant="body2" color="text.secondary" sx={{ minWidth: { sm: 190 } }}>{rating ? RATING_WORDS[rating] : "Choose 1 to 5"}</Typography>
					</Stack>
				</Stack>
				<TextField label="Comments" multiline minRows={2} fullWidth size="small" value={comment} onChange={(e) => setComment(e.target.value)}
					helperText="Visible to administrators. The staff member sees manager comments only once the cycle closes, and never who gave a peer rating." />
				<Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
					<Button type="submit" variant="contained" disabled={rating === null || saving || unchanged}>
						{saving ? "Saving…" : feedback ? "Update rating" : "Save rating"}
					</Button>
				</Stack>
			</Stack>
		</Box>
	);
}
