import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { ApiError } from "../api/client";
import type { PortalCover, PortalTimetableResponse } from "../api/timetable";
import { TimetableGrid } from "../modules/academics/TimetableGrid";
import { timeLabel } from "../modules/academics/timetableViewShared";
import { printArea } from "../utils/printArea";

function addDays(iso: string, days: number): string {
	const date = new Date(`${iso}T00:00:00`);
	date.setDate(date.getDate() + days);
	return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function coverText(cover: PortalCover): string {
	const s = cover.substitution;
	switch (cover.role) {
		case "COVERING":
			return `You are covering ${s.absentTeacherName}`;
		case "COVERED":
			return `${s.substituteTeacherName} is covering for you`;
		default:
			return `${s.substituteTeacherName} instead of ${s.absentTeacherName}`;
	}
}

/** A published week, read-only, with that week's one-off cover (substitutions) listed under the weekly pattern.
 * Shared by the teacher's own timetable and a guardian's view of a child's section. */
export function PortalTimetableView({
	title,
	backTo,
	load,
	showTeacher,
	showClass,
}: {
	title: string;
	backTo: string;
	load: (date?: string) => Promise<PortalTimetableResponse>;
	showTeacher: boolean;
	showClass: boolean;
}) {
	const [week, setWeek] = useState<PortalTimetableResponse | null>(null);
	const [date, setDate] = useState<string | undefined>(undefined);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		setError(null);
		load(date)
			.then(setWeek)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the timetable"));
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [date]);

	const coverByEntry = new Map<number, PortalCover[]>();
	for (const cover of week?.covers ?? []) {
		coverByEntry.set(cover.substitution.entryId, [...(coverByEntry.get(cover.substitution.entryId) ?? []), cover]);
	}

	return (
		<Stack spacing={2}>
			<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
				<Typography variant="h5">{title}</Typography>
				<Button size="small" component={RouterLink} to={backTo}>
					Back to dashboard
				</Button>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{week && (
				<>
					<Stack direction="row" spacing={1} className="no-print" sx={{ alignItems: "center", flexWrap: "wrap" }}>
						<Button size="small" onClick={() => setDate(addDays(week.weekStart, -7))}>
							Previous week
						</Button>
						<Button size="small" onClick={() => setDate(undefined)}>
							This week
						</Button>
						<Button size="small" onClick={() => setDate(addDays(week.weekStart, 7))}>
							Next week
						</Button>
						<Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
							Week of {week.weekStart}
						</Typography>
						{week.entries.length > 0 && (
							<Button size="small" onClick={printArea}>
								Print
							</Button>
						)}
					</Stack>

					{!week.published && (
						<Alert severity="info">The timetable for this class has not been published yet. Check back once the school releases it.</Alert>
					)}
					{week.published && week.entries.length === 0 && <Alert severity="info">No timetable slots yet.</Alert>}

					{week.entries.length > 0 && (
						<Paper sx={{ p: 2 }} className="print-area">
							<TimetableGrid
								periods={week.periods}
								entries={week.entries}
								renderEntry={(entry) => (
									<>
										<Typography variant="body2" sx={{ fontWeight: 600 }}>
											{entry.subjectName}
										</Typography>
										{showTeacher && (
											<Typography variant="caption" color="text.secondary" component="div">
												{entry.teacherName}
											</Typography>
										)}
										{showClass && (
											<Typography variant="caption" color="text.secondary" component="div">
												{entry.className} {entry.sectionName}
											</Typography>
										)}
										{entry.roomName && (
											<Typography variant="caption" color="text.secondary" component="div">
												{entry.roomName}
											</Typography>
										)}
										{(coverByEntry.get(entry.id) ?? []).map((cover) => (
											<Chip
												key={cover.substitution.id}
												size="small"
												color="warning"
												sx={{ mt: 0.5 }}
												label={`${new Date(`${cover.substitution.date}T00:00:00`).toLocaleDateString("en-US", { weekday: "short" })} cover`}
											/>
										))}
									</>
								)}
							/>
						</Paper>
					)}

					{week.covers.length > 0 && (
						<Box>
							<Typography variant="subtitle1" component="div" sx={{ mb: 1 }}>
								Cover this week
							</Typography>
							<TableContainer component={Paper} variant="outlined">
								<Table size="small" aria-label="Cover this week">
									<TableHead>
										<TableRow>
											<TableCell>Date</TableCell>
											<TableCell>Period</TableCell>
											<TableCell>Class</TableCell>
											<TableCell>Subject</TableCell>
											<TableCell>Cover</TableCell>
										</TableRow>
									</TableHead>
									<TableBody>
										{week.covers.map((cover) => (
											<TableRow key={cover.substitution.id}>
												<TableCell>{cover.substitution.date}</TableCell>
												<TableCell>
													{cover.substitution.periodLabel} ({timeLabel(cover.substitution.startTime)}–{timeLabel(cover.substitution.endTime)})
												</TableCell>
												<TableCell>
													{cover.substitution.className} {cover.substitution.sectionName}
												</TableCell>
												<TableCell>{cover.substitution.subjectName}</TableCell>
												<TableCell>{coverText(cover)}</TableCell>
											</TableRow>
										))}
									</TableBody>
								</Table>
							</TableContainer>
						</Box>
					)}
				</>
			)}
		</Stack>
	);
}
