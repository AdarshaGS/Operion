import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { getScorecard, type Scorecard, type ScorecardTeacher } from "../../api/performance";
import { defaultFrom, isoDate } from "./kpiFormat";
import { ScorecardTable } from "./ScorecardTable";

/** Metric-by-metric view of every teacher over any date range, independent of cycles. Selecting a teacher opens their appraisal page. */
export function ScorecardPanel({ cycleId }: { cycleId: number | null }) {
	const navigate = useNavigate();
	const [from, setFrom] = useState(defaultFrom);
	const [to, setTo] = useState(() => isoDate(new Date()));
	const [scorecard, setScorecard] = useState<Scorecard | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		if (!from || !to || to < from) {
			return;
		}
		setError(null);
		getScorecard(from, to)
			.then(setScorecard)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the scorecard"));
	}, [from, to]);

	return (
		<Stack spacing={2}>
			<Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
				<TextField label="From" type="date" size="small" value={from} onChange={(e) => setFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
				<TextField label="To" type="date" size="small" value={to} onChange={(e) => setTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
			</Stack>
			{to < from && <Alert severity="warning">The end date cannot be before the start date.</Alert>}
			{error && <Alert severity="error">{error}</Alert>}
			{scorecard && scorecard.metrics.length === 0 && (
				<Alert severity="info">No metrics are being tracked yet. Choose some on the Metrics tab.</Alert>
			)}
			{scorecard && scorecard.metrics.length > 0 && <ScorecardTable scorecard={scorecard} onSelect={(teacher: ScorecardTeacher) => navigate(`/performance/staff/${teacher.personId}${cycleId === null ? "" : `?cycle=${cycleId}`}`)} />}
			<Typography variant="caption" color="text.secondary">
				A dash means the teacher has no records for that metric in this period; it is left out of the overall score rather than counted as zero. Select a teacher to open their appraisal.
			</Typography>
		</Stack>
	);
}
