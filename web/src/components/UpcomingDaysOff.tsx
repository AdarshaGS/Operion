import { useEffect, useState } from "react";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Typography from "@mui/material/Typography";
import { getCalendarDays, type CalendarNonWorkingDay } from "../api/calendar";
import { formatCalendarDate } from "./LeaveDaysHint";

function isoDate(date: Date): string {
	const pad = (n: number) => String(n).padStart(2, "0");
	return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** The next few weeks' holidays, so staff can tell at a glance which days are leave and which
 * are already days off. Weekly offs are left out - they're the normal week, not news. */
export function UpcomingDaysOff({ campusId, days = 60 }: { campusId?: number | null; days?: number }) {
	const [holidays, setHolidays] = useState<CalendarNonWorkingDay[] | null>(null);

	useEffect(() => {
		const from = new Date();
		const to = new Date();
		to.setDate(to.getDate() + days);
		getCalendarDays(isoDate(from), isoDate(to), campusId)
			.then((response) => setHolidays(response.nonWorkingDays.filter((day) => day.kind === "HOLIDAY")))
			.catch(() => setHolidays(null));
	}, [campusId, days]);

	if (!holidays) return null;
	return (
		<Box>
			<Typography variant="subtitle2" gutterBottom>
				Upcoming holidays (next {days} days)
			</Typography>
			{holidays.length === 0 ? (
				<Typography variant="body2" color="text.secondary">
					No holidays declared in this period.
				</Typography>
			) : (
				<Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
					{holidays.map((day) => (
						<Chip key={day.date} size="small" variant="outlined" label={`${formatCalendarDate(day.date)} · ${day.label}`} />
					))}
				</Box>
			)}
		</Box>
	);
}
