import type { ReactNode } from "react";
import type { PeriodResponse, TimetableEntryResponse } from "../../api/timetable";

/** Mon-Sat by default; a Sunday column only appears if an entry actually lands on it. */
export const BASE_DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];

export interface TimetableViewProps {
	periods: PeriodResponse[];
	entries: TimetableEntryResponse[];
	days: string[];
	renderEntry: (entry: TimetableEntryResponse) => ReactNode;
	onCellClick?: (day: string, period: PeriodResponse, entry: TimetableEntryResponse | undefined) => void;
}

export function dayLabel(day: string): string {
	return day.charAt(0) + day.slice(1, 3).toLowerCase();
}

export function dayLongLabel(day: string): string {
	return day.charAt(0) + day.slice(1).toLowerCase();
}

export function timeLabel(time: string): string {
	return time.slice(0, 5);
}

export function toMinutes(time: string): number {
	const [hours, minutes] = time.split(":").map(Number);
	return hours * 60 + minutes;
}

export function slotKey(day: string, periodId: number): string {
	return `${day}:${periodId}`;
}

/** Today's weekday in the same MONDAY..SUNDAY form the API uses. */
export function todayDay(): string {
	return ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"][new Date().getDay()];
}
