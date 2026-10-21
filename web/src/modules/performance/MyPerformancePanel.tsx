import { useCallback, useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import {
	addMyGoal,
	getMyAppraisal,
	getMyScorecard,
	listMyCycles,
	submitSelfAppraisal,
	type Appraisal,
	type AppraisalCycle,
	type Scorecard,
} from "../../api/performance";
import { AppraisalView } from "./AppraisalView";
import { defaultFrom, isoDate } from "./kpiFormat";
import { ScorecardTable } from "./ScorecardTable";

/** A teacher's own scorecard and appraisals. Shown only when the organisation lets teachers see it. */
export function MyPerformancePanel() {
	const [from, setFrom] = useState(defaultFrom);
	const [to, setTo] = useState(() => isoDate(new Date()));
	const [scorecard, setScorecard] = useState<Scorecard | null>(null);
	const [cycles, setCycles] = useState<AppraisalCycle[]>([]);
	const [cycleId, setCycleId] = useState<number | "">("");
	const [appraisal, setAppraisal] = useState<Appraisal | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		if (!from || !to || to < from) {
			return;
		}
		getMyScorecard(from, to)
			.then(setScorecard)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your scorecard"));
	}, [from, to]);

	useEffect(() => {
		listMyCycles()
			.then((list) => {
				setCycles(list);
				setCycleId(list.find((c) => c.status === "OPEN")?.id ?? list[0]?.id ?? "");
			})
			.catch(() => setCycles([]));
	}, []);

	const load = useCallback(() => {
		if (cycleId === "") {
			return Promise.resolve();
		}
		return getMyAppraisal(cycleId)
			.then(setAppraisal)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your appraisal"));
	}, [cycleId]);

	useEffect(() => {
		setAppraisal(null);
		void load();
	}, [load]);

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error">{error}</Alert>}
			<Typography variant="h6">My scorecard</Typography>
			<Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
				<TextField label="From" type="date" size="small" value={from} onChange={(e) => setFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
				<TextField label="To" type="date" size="small" value={to} onChange={(e) => setTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
			</Stack>
			{scorecard && scorecard.metrics.length === 0 && <Alert severity="info">Your organisation is not tracking any metrics yet.</Alert>}
			{scorecard && scorecard.metrics.length > 0 && <ScorecardTable scorecard={scorecard} />}

			<Typography variant="h6">My appraisal</Typography>
			{cycles.length === 0 && <Typography variant="body2" color="text.secondary">There are no appraisal cycles yet.</Typography>}
			{cycles.length > 0 && (
				<TextField select size="small" label="Cycle" value={cycleId} onChange={(e) => setCycleId(Number(e.target.value))} sx={{ maxWidth: 320 }}>
					{cycles.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
				</TextField>
			)}
			{appraisal && cycleId !== "" && (
				<AppraisalView
					appraisal={appraisal}
					sources={[]}
					canRate
					canAddGoal
					canMarkGoals={false}
					onRate={(input) => submitSelfAppraisal(cycleId, input).then(load)}
					onAddGoal={(title, description) => addMyGoal(cycleId, title, description).then(load)}
					onMarkGoal={() => Promise.resolve()}
				/>
			)}
		</Stack>
	);
}
