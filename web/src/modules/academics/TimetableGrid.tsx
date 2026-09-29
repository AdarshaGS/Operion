import type { ReactNode } from "react";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import type { PeriodResponse, TimetableEntryResponse } from "../../api/timetable";

/** Mon-Sat by default; a Sunday column only appears if an entry actually lands on it. */
const BASE_DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];

function dayLabel(day: string): string {
	return day.charAt(0) + day.slice(1, 3).toLowerCase();
}

function timeLabel(time: string): string {
	return time.slice(0, 5);
}

interface TimetableGridProps {
	periods: PeriodResponse[];
	entries: TimetableEntryResponse[];
	/** Renders one filled cell; the section view shows subject+teacher, the teacher view class+subject. */
	renderEntry: (entry: TimetableEntryResponse) => ReactNode;
	/** Optional - makes empty and filled cells clickable (the builder, #213). Absent = read-only view. */
	onCellClick?: (day: string, period: PeriodResponse, entry: TimetableEntryResponse | undefined) => void;
}

/** Shared weekly grid (periods down, weekdays across) behind the section timetable (#213/#214)
 * and teacher timetable (#215) - and the portal tiles that reuse it later. Read-only unless
 * onCellClick is supplied. */
export function TimetableGrid({ periods, entries, renderEntry, onCellClick }: TimetableGridProps) {
	const days = entries.some((entry) => entry.dayOfWeek === "SUNDAY") ? [...BASE_DAYS, "SUNDAY"] : BASE_DAYS;
	const bySlot = new Map(entries.map((entry) => [`${entry.dayOfWeek}:${entry.periodId}`, entry]));

	return (
		<TableContainer component={Paper} variant="outlined">
			<Table size="small" sx={{ tableLayout: "fixed", minWidth: 720 }}>
				<TableHead>
					<TableRow>
						<TableCell sx={{ width: 120 }}>Period</TableCell>
						{days.map((day) => (
							<TableCell key={day}>{dayLabel(day)}</TableCell>
						))}
					</TableRow>
				</TableHead>
				<TableBody>
					{periods.map((period) => (
						<TableRow key={period.id}>
							<TableCell>
								<Typography variant="body2" sx={{ fontWeight: 600 }}>
									{period.label}
								</Typography>
								<Typography variant="caption" color="text.secondary" sx={{ fontVariantNumeric: "tabular-nums" }}>
									{timeLabel(period.startTime)}–{timeLabel(period.endTime)}
								</Typography>
							</TableCell>
							{period.breakSlot ? (
								<TableCell colSpan={days.length} align="center" sx={{ bgcolor: "action.hover", color: "text.secondary" }}>
									{period.label}
								</TableCell>
							) : (
								days.map((day) => {
									const entry = bySlot.get(`${day}:${period.id}`);
									return (
										<TableCell
											key={day}
											onClick={onCellClick ? () => onCellClick(day, period, entry) : undefined}
											sx={onCellClick ? { cursor: "pointer", "&:hover": { bgcolor: "action.hover" } } : undefined}
										>
											{entry ? renderEntry(entry) : <Box sx={{ color: "text.disabled" }}>{onCellClick ? "+" : "—"}</Box>}
										</TableCell>
									);
								})
							)}
						</TableRow>
					))}
				</TableBody>
			</Table>
		</TableContainer>
	);
}
