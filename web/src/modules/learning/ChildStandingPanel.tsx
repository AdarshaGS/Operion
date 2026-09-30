import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { getChildStanding, type ChildStanding, type LeaderboardTrack } from "../../api/learning";

const TRACKS: { track: LeaderboardTrack; label: string; unit: (score: number) => string }[] = [
	{ track: "QUIZ", label: "Quizzes", unit: (s) => `${s.toFixed(1)}% average` },
	{ track: "ACADEMIC", label: "Academic assessments", unit: (s) => `${s.toFixed(1)}% of marks` },
	{ track: "REWARD", label: "Rewards", unit: (s) => `${s} points` },
];

/** A child's own rank in their section per track, and the rewards they've been granted (#231). Other students are never named. */
export function ChildStandingPanel({ studentId }: { studentId: number }) {
	const [standing, setStanding] = useState<ChildStanding | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getChildStanding(studentId)
			.then(setStanding)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load standings"));
	}, [studentId]);

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error">{error}</Alert>}
			{standing && (
				<>
					<Paper sx={{ p: 2 }}>
						<Stack spacing={1}>
							{TRACKS.map(({ track, label, unit }) => {
								const s = standing.standings[track];
								return (
									<Stack key={track} direction="row" sx={{ justifyContent: "space-between" }}>
										<Typography variant="subtitle2">{label}</Typography>
										<Typography variant="body2">
											{s.rank == null || s.score == null ? "Not ranked yet" : `#${s.rank} of ${s.of} · ${unit(s.score)}`}
										</Typography>
									</Stack>
								);
							})}
						</Stack>
					</Paper>
					<Paper sx={{ p: 2 }}>
						<Typography variant="subtitle1" sx={{ mb: 1 }}>Rewards · {standing.rewardPoints} points</Typography>
						{standing.rewards.length === 0 && <Typography variant="body2" color="text.secondary">No rewards granted yet.</Typography>}
						{standing.rewards.map((r) => (
							<Typography key={r.id} variant="body2">
								{r.title} (+{r.points}){r.reason ? ` - ${r.reason}` : ""} <Typography component="span" variant="caption" color="text.secondary">from {r.grantedByName}</Typography>
							</Typography>
						))}
					</Paper>
				</>
			)}
		</Stack>
	);
}
