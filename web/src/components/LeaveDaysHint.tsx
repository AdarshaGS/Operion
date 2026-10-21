import { useEffect, useRef, useState } from "react";
import Alert from "@mui/material/Alert";
import { getCalendarDays, type CalendarDaysResponse } from "../api/calendar";

interface LeaveDaysHintProps {
	startDate: string;
	endDate: string;
	campusId?: number | null;
	/** Called with the working-day count whenever the range changes, so the form can pre-fill
	 * "number of days" (the user can still lower it for a half day). */
	onWorkingDays?: (count: number) => void;
}

export function formatCalendarDate(date: string): string {
	return new Date(`${date}T00:00:00`).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
}

/** Tells the person raising a leave request which days in their range are holidays or weekly
 * offs and so are not leave - the server counts and validates the same way. */
export function LeaveDaysHint({ startDate, endDate, campusId, onWorkingDays }: LeaveDaysHintProps) {
	const [days, setDays] = useState<CalendarDaysResponse | null>(null);
	const onWorkingDaysRef = useRef(onWorkingDays);
	onWorkingDaysRef.current = onWorkingDays;

	useEffect(() => {
		if (!startDate || !endDate || endDate < startDate) {
			setDays(null);
			return;
		}
		let cancelled = false;
		getCalendarDays(startDate, endDate, campusId)
			.then((response) => {
				if (cancelled) return;
				setDays(response);
				onWorkingDaysRef.current?.(response.workingDayCount);
			})
			.catch(() => {
				if (!cancelled) setDays(null); // a hint only - the server still validates on submit
			});
		return () => {
			cancelled = true;
		};
	}, [startDate, endDate, campusId]);

	if (!days) return null;
	const off = days.nonWorkingDays;
	if (days.workingDayCount === 0) {
		return <Alert severity="warning">Every day in this range is a holiday or weekly off, so no leave is needed.</Alert>;
	}
	return (
		<Alert severity={off.length > 0 ? "info" : "success"}>
			{days.workingDayCount} working day{days.workingDayCount === 1 ? "" : "s"} in this range.
			{off.length > 0 &&
				` Not counted: ${off
					.map((day) => `${formatCalendarDate(day.date)} (${day.kind === "HOLIDAY" ? day.label : "weekly off"})`)
					.join(", ")}.`}
		</Alert>
	);
}
