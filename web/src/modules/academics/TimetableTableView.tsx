import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { dayLabel, slotKey, timeLabel, type TimetableViewProps } from "./timetableViewShared";

/** Classic weekly table: periods down, weekdays across. */
export function TimetableTableView({ periods, entries, days, renderEntry, onCellClick }: TimetableViewProps) {
	const bySlot = new Map(entries.map((entry) => [slotKey(entry.dayOfWeek, entry.periodId), entry]));

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
									const entry = bySlot.get(slotKey(day, period.id));
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
