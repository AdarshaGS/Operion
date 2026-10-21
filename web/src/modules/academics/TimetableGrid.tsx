import { useState, type ReactNode } from "react";
import CalendarViewWeekIcon from "@mui/icons-material/CalendarViewWeek";
import TableChartIcon from "@mui/icons-material/TableChart";
import TodayIcon from "@mui/icons-material/Today";
import ViewListIcon from "@mui/icons-material/ViewList";
import Stack from "@mui/material/Stack";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import type { PeriodResponse, TimetableEntryResponse } from "../../api/timetable";
import { TimetableCalendarView } from "./TimetableCalendarView";
import { TimetableDayView } from "./TimetableDayView";
import { TimetableListView } from "./TimetableListView";
import { TimetableTableView } from "./TimetableTableView";
import { BASE_DAYS } from "./timetableViewShared";

type ViewMode = "table" | "calendar" | "day" | "list";

const VIEW_STORAGE_KEY = "operion.timetable.view";
const VIEW_MODES: ViewMode[] = ["table", "calendar", "day", "list"];

function loadViewMode(): ViewMode {
	try {
		const stored = localStorage.getItem(VIEW_STORAGE_KEY);
		if (VIEW_MODES.includes(stored as ViewMode)) return stored as ViewMode;
	} catch {
		// storage blocked - fall through to the default
	}
	return "table";
}

interface TimetableGridProps {
	periods: PeriodResponse[];
	entries: TimetableEntryResponse[];
	/** Renders one filled cell; the section view shows subject+teacher, the teacher view class+subject. */
	renderEntry: (entry: TimetableEntryResponse) => ReactNode;
	/** Optional - makes empty and filled cells clickable (the builder, #213). Absent = read-only view. */
	onCellClick?: (day: string, period: PeriodResponse, entry: TimetableEntryResponse | undefined) => void;
	/** Forces the Sunday column on - the builder needs it to place the first Sunday slot. */
	showSunday?: boolean;
}

/** Shared weekly timetable behind the section timetable (#213/#214) and teacher timetable
 * (#215) - and the portal tiles that reuse it later. The viewer picks the layout (table,
 * week calendar, single day, or list) and the choice is remembered per browser. Every layout
 * honours onCellClick, so the builder works in all of them. */
export function TimetableGrid({ periods, entries, renderEntry, onCellClick, showSunday }: TimetableGridProps) {
	const [mode, setMode] = useState<ViewMode>(loadViewMode);
	const days = showSunday || entries.some((entry) => entry.dayOfWeek === "SUNDAY") ? [...BASE_DAYS, "SUNDAY"] : BASE_DAYS;
	// One school day reads in ordinal order; periods drawn from several school days (a teacher who teaches two class groups) read by the clock.
	const mixedDays = new Set(periods.map((p) => p.templateId)).size > 1;
	const ordered = [...periods].sort((a, b) => (mixedDays ? a.startTime.localeCompare(b.startTime) || a.ordinal - b.ordinal : a.ordinal - b.ordinal));
	const viewProps = { periods: ordered, entries, days, renderEntry, onCellClick };

	function handleModeChange(next: ViewMode | null) {
		if (!next) return;
		setMode(next);
		try {
			localStorage.setItem(VIEW_STORAGE_KEY, next);
		} catch {
			// storage blocked - the choice just won't persist
		}
	}

	return (
		<Stack spacing={1.5}>
			<ToggleButtonGroup size="small" exclusive value={mode} onChange={(_, next) => handleModeChange(next)} aria-label="Timetable layout" sx={{ alignSelf: "flex-start" }}>
				<ToggleButton value="table">
					<TableChartIcon fontSize="small" sx={{ mr: 0.5 }} /> Table
				</ToggleButton>
				<ToggleButton value="calendar">
					<CalendarViewWeekIcon fontSize="small" sx={{ mr: 0.5 }} /> Calendar
				</ToggleButton>
				<ToggleButton value="day">
					<TodayIcon fontSize="small" sx={{ mr: 0.5 }} /> Day
				</ToggleButton>
				<ToggleButton value="list">
					<ViewListIcon fontSize="small" sx={{ mr: 0.5 }} /> List
				</ToggleButton>
			</ToggleButtonGroup>
			{mode === "table" && <TimetableTableView {...viewProps} />}
			{mode === "calendar" && <TimetableCalendarView {...viewProps} />}
			{mode === "day" && <TimetableDayView {...viewProps} />}
			{mode === "list" && <TimetableListView {...viewProps} />}
		</Stack>
	);
}
