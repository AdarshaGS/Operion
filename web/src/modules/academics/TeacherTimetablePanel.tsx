import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { downloadTeacherTimetableCsv, listAllPeriods, listTeacherTimetable, type PeriodResponse, type TimetableEntryResponse } from "../../api/timetable";
import { printArea } from "../../utils/printArea";
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
		Promise.all([listAllPeriods(), listTeacherTimetable(personId)])
			.then(([fetchedPeriods, fetchedEntries]) => {
				// Only the school days this teacher actually has slots on (the default day when they have none).
				const dayOf = new Map(fetchedPeriods.map((p) => [p.id, p.templateId]));
				const days = new Set(fetchedEntries.length === 0 ? [null] : fetchedEntries.map((e) => dayOf.get(e.periodId) ?? null));
				setPeriods(fetchedPeriods.filter((p) => days.has(p.templateId)));
				setEntries(fetchedEntries);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load timetable"))
			.finally(() => setLoaded(true));
	}, [personId]);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Timetable</Typography>
					{entries.length > 0 && (
						<Stack direction="row" spacing={1} className="no-print">
							<Button size="small" onClick={() => downloadTeacherTimetableCsv(personId).catch((err) => setError(err instanceof ApiError ? err.message : "Failed to export"))}>
								Export CSV
							</Button>
							<Button size="small" onClick={printArea}>
								Print
							</Button>
						</Stack>
					)}
				</Box>
				{error && <Alert severity="error">{error}</Alert>}
				{loaded && !error && periods.length === 0 && <Alert severity="info">No periods are configured yet.</Alert>}
				{loaded && !error && periods.length > 0 && entries.length === 0 && (
					<Alert severity="info">This teacher has no timetable slots yet.</Alert>
				)}
				{periods.length > 0 && entries.length > 0 && (
					<Box className="print-area">
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
					</Box>
				)}
			</Stack>
		</Paper>
	);
}
