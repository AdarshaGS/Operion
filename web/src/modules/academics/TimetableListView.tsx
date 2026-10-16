import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { dayLongLabel, slotKey, timeLabel, type TimetableViewProps } from "./timetableViewShared";

/** Compact agenda of the whole week, one block per day. Read-only viewers see only the periods
 * that are actually taught; with editing on, free periods are listed too so they can be filled. */
export function TimetableListView({ periods, entries, days, renderEntry, onCellClick }: TimetableViewProps) {
	const bySlot = new Map(entries.map((entry) => [slotKey(entry.dayOfWeek, entry.periodId), entry]));
	const teachable = periods.filter((period) => !period.breakSlot);

	return (
		<Stack spacing={2}>
			{days.map((day) => {
				const rows = teachable
					.map((period) => ({ period, entry: bySlot.get(slotKey(day, period.id)) }))
					.filter((row) => row.entry || onCellClick);
				return (
					<Paper key={day} variant="outlined" sx={{ p: 1.5 }}>
						<Typography variant="subtitle2" sx={{ mb: 1 }}>
							{dayLongLabel(day)}
						</Typography>
						{rows.length === 0 ? (
							<Typography variant="body2" color="text.disabled">
								No classes
							</Typography>
						) : (
							<Stack spacing={1}>
								{rows.map(({ period, entry }) => (
									<Box
										key={period.id}
										onClick={onCellClick ? () => onCellClick(day, period, entry) : undefined}
										sx={{ display: "flex", gap: 2, alignItems: "center", cursor: onCellClick ? "pointer" : "default", "&:hover": onCellClick ? { bgcolor: "action.hover" } : undefined }}
									>
										<Typography variant="caption" color="text.secondary" sx={{ width: 96, flexShrink: 0, fontVariantNumeric: "tabular-nums" }}>
											{timeLabel(period.startTime)}–{timeLabel(period.endTime)}
										</Typography>
										{entry ? <Box>{renderEntry(entry)}</Box> : <Typography color="text.disabled">+ Add ({period.label})</Typography>}
									</Box>
								))}
							</Stack>
						)}
					</Paper>
				);
			})}
		</Stack>
	);
}
