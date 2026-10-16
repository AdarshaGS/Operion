import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import { dayLabel, slotKey, timeLabel, toMinutes, type TimetableViewProps } from "./timetableViewShared";

const PX_PER_MINUTE = 1.4;
const AXIS_WIDTH = 56;

/** Week-calendar layout: a time axis down the side and each period drawn as a block sized and
 * positioned by its real start/end time, so uneven period lengths and breaks read at a glance. */
export function TimetableCalendarView({ periods, entries, days, renderEntry, onCellClick }: TimetableViewProps) {
	if (periods.length === 0) return null;
	const bySlot = new Map(entries.map((entry) => [slotKey(entry.dayOfWeek, entry.periodId), entry]));
	const dayStart = Math.floor(Math.min(...periods.map((p) => toMinutes(p.startTime))) / 60) * 60;
	const dayEnd = Math.ceil(Math.max(...periods.map((p) => toMinutes(p.endTime))) / 60) * 60;
	const height = (dayEnd - dayStart) * PX_PER_MINUTE;
	const hours: number[] = [];
	for (let minute = dayStart; minute <= dayEnd; minute += 60) hours.push(minute);

	const top = (time: string) => (toMinutes(time) - dayStart) * PX_PER_MINUTE;
	const blockHeight = (period: { startTime: string; endTime: string }) =>
		(toMinutes(period.endTime) - toMinutes(period.startTime)) * PX_PER_MINUTE;

	return (
		<Paper variant="outlined" sx={{ overflowX: "auto" }}>
			<Box sx={{ minWidth: 720 }}>
				<Box sx={{ display: "grid", gridTemplateColumns: `${AXIS_WIDTH}px repeat(${days.length}, 1fr)`, borderBottom: 1, borderColor: "divider" }}>
					<Box />
					{days.map((day) => (
						<Typography key={day} variant="subtitle2" align="center" sx={{ py: 1 }}>
							{dayLabel(day)}
						</Typography>
					))}
				</Box>
				<Box sx={{ display: "grid", gridTemplateColumns: `${AXIS_WIDTH}px repeat(${days.length}, 1fr)` }}>
					<Box sx={{ position: "relative", height }}>
						{hours.map((minute) => (
							<Typography
								key={minute}
								variant="caption"
								color="text.secondary"
								sx={{ position: "absolute", right: 8, top: (minute - dayStart) * PX_PER_MINUTE - 8, fontVariantNumeric: "tabular-nums" }}
							>
								{String(minute / 60).padStart(2, "0")}:00
							</Typography>
						))}
					</Box>
					{days.map((day) => (
						<Box key={day} sx={{ position: "relative", height, borderLeft: 1, borderColor: "divider" }}>
							{hours.map((minute) => (
								<Box
									key={minute}
									sx={{ position: "absolute", left: 0, right: 0, top: (minute - dayStart) * PX_PER_MINUTE, borderTop: 1, borderColor: "divider", opacity: 0.5 }}
								/>
							))}
							{periods.map((period) => {
								const position = { position: "absolute" as const, left: 3, right: 3, top: top(period.startTime) + 1, height: blockHeight(period) - 2 };
								if (period.breakSlot) {
									return (
										<Box
											key={period.id}
											sx={{ ...position, bgcolor: "action.hover", color: "text.secondary", display: "flex", alignItems: "center", justifyContent: "center", borderRadius: 1, typography: "caption" }}
										>
											{period.label}
										</Box>
									);
								}
								const entry = bySlot.get(slotKey(day, period.id));
								if (!entry && !onCellClick) return null;
								return (
									<Box
										key={period.id}
										title={`${period.label} ${timeLabel(period.startTime)}–${timeLabel(period.endTime)}`}
										onClick={onCellClick ? () => onCellClick(day, period, entry) : undefined}
										sx={{
											...position,
											px: 1,
											py: 0.5,
											overflow: "hidden",
											borderRadius: 1,
											cursor: onCellClick ? "pointer" : "default",
											...(entry
												? { bgcolor: "primary.main", color: "primary.contrastText", "& .MuiTypography-root": { color: "inherit" } }
												: { border: 1, borderStyle: "dashed", borderColor: "divider", color: "text.disabled", display: "flex", alignItems: "center", justifyContent: "center", "&:hover": { bgcolor: "action.hover" } }),
										}}
									>
										{entry ? renderEntry(entry) : "+"}
									</Box>
								);
							})}
						</Box>
					))}
				</Box>
			</Box>
		</Paper>
	);
}
