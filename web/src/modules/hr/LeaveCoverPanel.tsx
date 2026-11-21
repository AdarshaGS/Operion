import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { listSubstitutionNeeds, type SubstitutionNeedResponse } from "../../api/timetable";
import { useCan } from "../../auth/useCan";
import { colors } from "../../theme";
import { dayLongLabel, timeLabel } from "../academics/timetableViewShared";

/** The periods a leave takes its teacher out of, and whether each already has cover. Read-only information beside the
 * leave: it never gates, hides or delays Approve/Reject. personId is the teacher's person (not staff profile) id; it is
 * null while the staff profile has not been resolved to a person yet. */
export function LeaveCoverPanel({
	personId,
	startDate,
	endDate,
	numberOfDays,
}: {
	personId: number | null;
	startDate: string;
	endDate: string;
	numberOfDays: number;
}) {
	const canView = useCan("TIMETABLE_VIEW");
	const canManage = useCan("TIMETABLE_MANAGE");

	const [needs, setNeeds] = useState<SubstitutionNeedResponse[] | null>(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		if (!canView || personId === null) return;
		let cancelled = false;
		setLoading(true);
		setError(null);
		listSubstitutionNeeds(personId, startDate, endDate)
			.then((result) => {
				if (!cancelled) setNeeds(result);
			})
			.catch((err) => {
				if (!cancelled) {
					setNeeds(null);
					setError(err instanceof ApiError ? err.message : "Failed to load the timetable cover");
				}
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [canView, personId, startDate, endDate]);

	if (!canView) {
		return (
			<Typography variant="body2" sx={{ color: colors.inkSoft }}>
				Timetable cover isn't visible to your role
			</Typography>
		);
	}

	// The backend treats a half-day as a full day, so the list may include periods the person is actually present for.
	const halfDay = numberOfDays < 1 || numberOfDays % 1 !== 0;
	const uncovered = (needs ?? []).filter((need) => !need.substitution).length;
	const assignCoverPath = `/academics?${new URLSearchParams({ tab: "substitutions", teacher: String(personId), from: startDate, to: endDate }).toString()}`;

	return (
		<Stack spacing={1.5} aria-label="Timetable cover">
			<Typography variant="subtitle2">Timetable cover</Typography>
			{halfDay && (
				<Typography variant="caption" sx={{ color: colors.inkSoft }}>
					Half-day leave: check which periods are missed
				</Typography>
			)}
			{loading && <CircularProgress size={20} aria-label="Loading timetable cover" />}
			{error && <Alert severity="error">{error}</Alert>}
			{personId === null && !error && <Alert severity="info">Loading the staff member's details.</Alert>}
			{!loading && !error && needs !== null && needs.length === 0 && <Alert severity="info">No timetabled periods</Alert>}

			{needs !== null && needs.length > 0 && (
				<TableContainer>
					<Table size="small" aria-label="Periods missed during leave">
						<TableHead>
							<TableRow>
								<TableCell>Date</TableCell>
								<TableCell>Class</TableCell>
								<TableCell>Subject</TableCell>
								<TableCell>Period</TableCell>
								<TableCell>Cover</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{needs.map((need) => (
								<TableRow key={`${need.entryId}:${need.date}`}>
									<TableCell>
										{dayLongLabel(need.dayOfWeek)} {need.date}
									</TableCell>
									<TableCell>
										{need.className} {need.sectionName}
									</TableCell>
									<TableCell>{need.subjectName}</TableCell>
									<TableCell>
										{need.periodLabel} ({timeLabel(need.startTime)}–{timeLabel(need.endTime)})
									</TableCell>
									<TableCell>
										{need.substitution ? (
											<Typography variant="body2">Covered by {need.substitution.substituteTeacherName}</Typography>
										) : (
											<Typography variant="body2" sx={{ color: colors.bad, fontWeight: 600 }}>
												Uncovered
											</Typography>
										)}
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			)}

			{canManage && personId !== null && uncovered > 0 && (
				<div>
					<Button component={RouterLink} to={assignCoverPath} size="small" variant="outlined">
						Assign cover
					</Button>
				</div>
			)}
		</Stack>
	);
}
