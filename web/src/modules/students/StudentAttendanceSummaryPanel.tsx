import { useEffect, useMemo, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import BarChartIcon from "@mui/icons-material/BarChart";
import ViewListIcon from "@mui/icons-material/ViewList";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis } from "recharts";
import { ApiError } from "../../api/client";
import {
	getMonthlySummary,
	getStudentAttendanceHistory,
	type MonthlyAttendanceSummaryResponse,
	type StudentAttendanceResponse,
} from "../../api/attendance";
import { colors } from "../../theme";

const MONTH_NAMES = [
	"January", "February", "March", "April", "May", "June",
	"July", "August", "September", "October", "November", "December",
];

function percentageColor(percentage: number): "success" | "warning" | "error" {
	if (percentage >= 75) return "success";
	if (percentage >= 50) return "warning";
	return "error";
}

const STATUS_STYLE: Record<string, { label: string; color: string }> = {
	PRESENT: { label: "Present", color: colors.ok },
	LATE: { label: "Late", color: colors.warn },
	HALF_DAY: { label: "Half-day", color: colors.info },
	ABSENT: { label: "Absent", color: colors.bad },
	LEAVE: { label: "Leave", color: colors.accent },
};

function pad(value: number): string {
	return String(value).padStart(2, "0");
}

/** One bar per calendar day of the month; days with no attendance mark (weekends, holidays) stay empty. */
function buildDailyData(year: number, month: number, entries: StudentAttendanceResponse[]) {
	const byDate = new Map(entries.map((entry) => [entry.attendanceDate, entry.attendanceStatus]));
	const daysInMonth = new Date(year, month, 0).getDate();
	return Array.from({ length: daysInMonth }, (_, index) => {
		const day = index + 1;
		const status = byDate.get(`${year}-${pad(month)}-${pad(day)}`) ?? null;
		return { day, status, marked: status ? 1 : 0 };
	});
}

export function StudentAttendanceSummaryPanel({ enrollmentId }: { enrollmentId: number }) {
	const now = new Date();
	const [year, setYear] = useState(now.getFullYear());
	const [month, setMonth] = useState(now.getMonth() + 1);
	const [summary, setSummary] = useState<MonthlyAttendanceSummaryResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [view, setView] = useState<"list" | "graph">("list");
	const [history, setHistory] = useState<StudentAttendanceResponse[] | null>(null);
	const [historyError, setHistoryError] = useState<string | null>(null);

	useEffect(() => {
		let cancelled = false;
		setError(null);
		getMonthlySummary(enrollmentId, year, month)
			.then((result) => {
				if (!cancelled) setSummary(result);
			})
			.catch((err) => {
				if (!cancelled) setError(err instanceof ApiError ? err.message : "Failed to load attendance summary");
			});
		return () => {
			cancelled = true;
		};
	}, [enrollmentId, year, month]);

	// Per-day rows are only needed for the graph, so they are fetched lazily when it is opened.
	useEffect(() => {
		if (view !== "graph") return;
		let cancelled = false;
		setHistory(null);
		setHistoryError(null);
		const lastDay = new Date(year, month, 0).getDate();
		getStudentAttendanceHistory(enrollmentId, `${year}-${pad(month)}-01`, `${year}-${pad(month)}-${pad(lastDay)}`)
			.then((rows) => {
				if (!cancelled) setHistory(rows);
			})
			.catch((err) => {
				if (!cancelled) setHistoryError(err instanceof ApiError ? err.message : "Failed to load daily attendance");
			});
		return () => {
			cancelled = true;
		};
	}, [view, enrollmentId, year, month]);

	const dailyData = useMemo(() => (history ? buildDailyData(year, month, history) : []), [history, year, month]);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="subtitle1">Attendance</Typography>
					<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
						<ToggleButtonGroup
							exclusive
							size="small"
							value={view}
							onChange={(_, next) => next && setView(next)}
							aria-label="Attendance view"
						>
							<ToggleButton value="list" aria-label="List view">
								<ViewListIcon fontSize="small" />
							</ToggleButton>
							<ToggleButton value="graph" aria-label="Graph view">
								<BarChartIcon fontSize="small" />
							</ToggleButton>
						</ToggleButtonGroup>
						<TextField select size="small" label="Month" value={month} onChange={(e) => setMonth(Number(e.target.value))}>
							{MONTH_NAMES.map((name, index) => (
								<MenuItem key={name} value={index + 1}>
									{name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							size="small"
							label="Year"
							type="number"
							value={year}
							onChange={(e) => setYear(Number(e.target.value))}
							sx={{ width: 100 }}
						/>
					</Stack>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{summary && view === "list" && (
					<Stack direction="row" spacing={3} sx={{ alignItems: "center", flexWrap: "wrap" }}>
						<Chip label={`${summary.percentage}% present`} color={percentageColor(summary.percentage)} />
						<Typography variant="body2" color="text.secondary">
							Present {summary.presentCount} · Late {summary.lateCount} · Half-day {summary.halfDayCount} · Absent{" "}
							{summary.absentCount} · Leave {summary.leaveCount} · {summary.totalMarkedDays} days marked
						</Typography>
					</Stack>
				)}

				{view === "graph" && (
					<Stack spacing={1.5}>
						{summary && (
							<Chip
								label={`${summary.percentage}% present · ${summary.totalMarkedDays} days marked`}
								color={percentageColor(summary.percentage)}
								sx={{ alignSelf: "flex-start" }}
							/>
						)}
						{historyError && <Alert severity="error">{historyError}</Alert>}
						{!historyError && !history && <Typography variant="body2" color="text.secondary">Loading…</Typography>}
						{history && history.length === 0 && (
							<Typography variant="body2" color="text.secondary">No attendance marked this month.</Typography>
						)}
						{history && history.length > 0 && (
							<>
								<Box sx={{ height: 200 }}>
									<ResponsiveContainer width="100%" height="100%">
										<BarChart data={dailyData} margin={{ top: 8 }} barCategoryGap={2}>
											<CartesianGrid strokeDasharray="3 3" vertical={false} stroke={colors.rule} />
											<XAxis dataKey="day" tickLine={false} axisLine={false} interval={0} tick={{ fontSize: 11 }} />
											<YAxis hide domain={[0, 1]} />
											<RechartsTooltip
												cursor={{ fill: "transparent" }}
												formatter={(_value, _name, item) => STATUS_STYLE[item.payload.status]?.label ?? item.payload.status}
												labelFormatter={(day) => `${MONTH_NAMES[month - 1]} ${day}`}
											/>
											<Bar dataKey="marked" name="Status" radius={[4, 4, 0, 0]}>
												{dailyData.map((item) => (
													<Cell key={item.day} fill={item.status ? STATUS_STYLE[item.status]?.color ?? colors.ruleStrong : "transparent"} />
												))}
											</Bar>
										</BarChart>
									</ResponsiveContainer>
								</Box>
								<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap", rowGap: 0.5 }}>
									{Object.entries(STATUS_STYLE).map(([key, style]) => (
										<Stack key={key} direction="row" spacing={0.75} sx={{ alignItems: "center" }}>
											<Box sx={{ width: 10, height: 10, borderRadius: "3px", bgcolor: style.color }} />
											<Typography variant="caption" color="text.secondary">{style.label}</Typography>
										</Stack>
									))}
								</Stack>
							</>
						)}
					</Stack>
				)}
			</Stack>
		</Paper>
	);
}
