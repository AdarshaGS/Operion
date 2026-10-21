import { api } from "./client";

export interface CalendarNonWorkingDay {
	date: string;
	kind: "HOLIDAY" | "WEEKLY_OFF";
	label: string;
}

export interface CalendarDaysResponse {
	from: string;
	to: string;
	workingDayCount: number;
	nonWorkingDays: CalendarNonWorkingDay[];
}

/** Which days in [from, to] are not working days (holidays from the working calendar plus the
 * weekly off), and how many working days are left. Not permission-gated, so the staff portal
 * can use it too. campusId omitted = organisation-wide days only. */
export function getCalendarDays(from: string, to: string, campusId?: number | null): Promise<CalendarDaysResponse> {
	const query = new URLSearchParams({ from, to });
	if (campusId != null) query.set("campusId", String(campusId));
	return api.get<CalendarDaysResponse>(`/api/v1/calendar/days?${query.toString()}`);
}
