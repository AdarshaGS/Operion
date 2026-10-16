import { useState } from "react";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Typography from "@mui/material/Typography";
import { dayLabel, slotKey, timeLabel, todayDay, type TimetableViewProps } from "./timetableViewShared";

/** One day at a time as a vertical agenda - the phone-friendly view. Opens on today when the
 * school runs that day, otherwise the first day. */
export function TimetableDayView({ periods, entries, days, renderEntry, onCellClick }: TimetableViewProps) {
	const [picked, setPicked] = useState(() => (days.includes(todayDay()) ? todayDay() : days[0]));
	const day = days.includes(picked) ? picked : days[0];
	const bySlot = new Map(entries.map((entry) => [slotKey(entry.dayOfWeek, entry.periodId), entry]));

	return (
		<Paper variant="outlined">
			<Tabs value={day} onChange={(_, value) => setPicked(value)} variant="scrollable" scrollButtons="auto" sx={{ borderBottom: 1, borderColor: "divider" }}>
				{days.map((d) => (
					<Tab key={d} value={d} label={dayLabel(d)} />
				))}
			</Tabs>
			<Stack divider={<Box sx={{ borderTop: 1, borderColor: "divider" }} />}>
				{periods.map((period) => {
					const entry = bySlot.get(slotKey(day, period.id));
					return (
						<Box key={period.id} sx={{ display: "flex", gap: 2, p: 1.5, alignItems: "center", bgcolor: period.breakSlot ? "action.hover" : undefined }}>
							<Box sx={{ width: 96, flexShrink: 0 }}>
								<Typography variant="body2" sx={{ fontWeight: 600 }}>
									{period.label}
								</Typography>
								<Typography variant="caption" color="text.secondary" sx={{ fontVariantNumeric: "tabular-nums" }}>
									{timeLabel(period.startTime)}–{timeLabel(period.endTime)}
								</Typography>
							</Box>
							{period.breakSlot ? (
								<Typography variant="body2" color="text.secondary">
									Break
								</Typography>
							) : (
								<Box
									onClick={onCellClick ? () => onCellClick(day, period, entry) : undefined}
									sx={{ flex: 1, minHeight: 40, display: "flex", alignItems: "center", cursor: onCellClick ? "pointer" : "default" }}
								>
									{entry ? <Box>{renderEntry(entry)}</Box> : <Typography color="text.disabled">{onCellClick ? "+ Add" : "Free"}</Typography>}
								</Box>
							)}
						</Box>
					);
				})}
			</Stack>
		</Paper>
	);
}
