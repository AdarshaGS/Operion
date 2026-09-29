import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { listPeriods, listTeacherTimetable, type PeriodResponse, type TimetableEntryResponse } from "../../api/timetable";
import { TimetableGrid } from "./TimetableGrid";

/** Read-only weekly grid of one teacher's periods across every section they're booked
 * into (#215). Reusable by the teacher portal's own-timetable tile. */
export function TeacherTimetablePanel({ personId }: { personId: number }) {
	const [periods, setPeriods] = useState<PeriodResponse[]>([]);
	const [entries, setEntries] = useState<TimetableEntryResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loaded, setLoaded] = useState(false);

	useEffect(() => {
		setLoaded(false);
		Promise.all([listPeriods(), listTeacherTimetable(personId)])
			.then(([fetchedPeriods, fetchedEntries]) => {
				setPeriods(fetchedPeriods);
				setEntries(fetchedEntries);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load timetable"))
			.finally(() => setLoaded(true));
	}, [personId]);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Timetable</Typography>
				{error && <Alert severity="error">{error}</Alert>}
				{loaded && !error && periods.length === 0 && <Alert severity="info">No periods are configured yet.</Alert>}
				{loaded && !error && periods.length > 0 && entries.length === 0 && (
					<Alert severity="info">This teacher has no timetable slots yet.</Alert>
				)}
				{periods.length > 0 && entries.length > 0 && (
					<TimetableGrid
						periods={periods}
						entries={entries}
						renderEntry={(entry) => (
							<>
								<Typography variant="body2" sx={{ fontWeight: 600 }}>
									{entry.subjectName}
								</Typography>
								<Typography variant="caption" color="text.secondary">
									{entry.className} {entry.sectionName}
								</Typography>
							</>
						)}
					/>
				)}
			</Stack>
		</Paper>
	);
}
