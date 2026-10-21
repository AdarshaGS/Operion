import { useEffect, useMemo, useState, type ReactNode } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import LinearProgress from "@mui/material/LinearProgress";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutlined";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import GroupsIcon from "@mui/icons-material/Groups";
import PersonOffIcon from "@mui/icons-material/PersonOff";
import PeopleAltIcon from "@mui/icons-material/PeopleAlt";
import ScheduleIcon from "@mui/icons-material/Schedule";
import BarChartIcon from "@mui/icons-material/BarChart";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis } from "recharts";
import { ApiError } from "../../api/client";
import { getAttendanceOverview, type AttendanceActivity, type AttendanceClassRow, type AttendanceOverviewResponse } from "../../api/attendanceOverview";
import { Can } from "../../auth/Can";
import { KpiTile, SectionCard } from "../../components/DashboardKit";
import { colors } from "../../theme";

export interface RegisterTarget {
	schoolClassId: number;
	sectionId: number;
	date: string;
}

function todayIso(): string {
	const now = new Date();
	return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

/** Same green/amber/red bands the mock uses: 90+ healthy, 80-89 watch, below 80 needs attention. */
function rateColor(percent: number): string {
	return percent >= 90 ? colors.ok : percent >= 80 ? colors.warn : colors.bad;
}


const ACTIVITY_ICON: Record<AttendanceActivity["kind"], { icon: ReactNode; color: string; bg: string }> = {
	RECORDED: { icon: <CheckCircleIcon fontSize="small" />, color: colors.ok, bg: colors.tintGreen },
	PENDING: { icon: <ErrorOutlineIcon fontSize="small" />, color: colors.bad, bg: colors.tintPink },
	STAFF: { icon: <PeopleAltIcon fontSize="small" />, color: colors.accent, bg: colors.tintViolet },
};

interface Props {
	onOpenRegister: (target: RegisterTarget) => void;
	onMarkAttendance: () => void;
}

/** Attendance overview tab - one day's picture across every section: enrolment, present/absent, which classes still owe a
 * register, a per-class table that opens the class register, a by-class chart and the day's activity. */
export function AttendanceOverviewPanel({ onOpenRegister, onMarkAttendance }: Props) {
	const [date, setDate] = useState(todayIso());
	const [overview, setOverview] = useState<AttendanceOverviewResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [classId, setClassId] = useState("");
	const [sectionName, setSectionName] = useState("");
	const [status, setStatus] = useState("");
	const [page, setPage] = useState(0);
	const [rowsPerPage, setRowsPerPage] = useState(10);

	useEffect(() => {
		setOverview(null);
		getAttendanceOverview(date)
			.then((result) => {
				setOverview(result);
				setError(null);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load attendance overview"));
	}, [date]);

	const classOptions = useMemo(() => {
		const seen = new Map<number, string>();
		(overview?.classes ?? []).forEach((row) => seen.set(row.schoolClassId, row.className));
		return [...seen.entries()];
	}, [overview]);
	const sectionOptions = useMemo(() => [...new Set((overview?.classes ?? []).map((row) => row.sectionName))].sort(), [overview]);

	const filtered = useMemo<AttendanceClassRow[]>(
		() =>
			(overview?.classes ?? []).filter(
				(row) => (!classId || row.schoolClassId === Number(classId)) && (!sectionName || row.sectionName === sectionName) && (!status || row.status === status),
			),
		[overview, classId, sectionName, status],
	);
	const pageRows = filtered.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);
	const hasFilters = Boolean(classId || sectionName || status || date !== todayIso());
	const chartData = (overview?.classes ?? []).filter((row) => row.marked > 0).map((row) => ({ label: row.classLabel, rate: row.ratePercent }));

	function clearFilters() {
		setClassId("");
		setSectionName("");
		setStatus("");
		setDate(todayIso());
		setPage(0);
	}

	return (
		<Stack spacing={3}>
			{error && <Alert severity="error">{error}</Alert>}

			<Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr", lg: "repeat(4, 1fr)" } }}>
				<KpiTile icon={<GroupsIcon />} tint="violet" label="Students Enrolled" value={overview?.studentsEnrolled ?? "—"} />
				<KpiTile icon={<EventAvailableIcon />} tint="green" label="Present" value={overview?.present ?? "—"} detail={overview && overview.late > 0 ? `${overview.late} late` : undefined} />
				<KpiTile icon={<PersonOffIcon />} tint="pink" label="Absent" value={overview?.absent ?? "—"} />
				<KpiTile
					icon={<ScheduleIcon />}
					tint="orange"
					label="Attendance Not Recorded"
					value={overview ? `${overview.classesNotRecorded} ${overview.classesNotRecorded === 1 ? "class" : "classes"}` : "—"}
					detail={overview ? `of ${overview.classesTotal}` : undefined}
				/>
			</Box>

			<Paper sx={{ p: 2.5 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 2, flexWrap: "wrap" }}>
						<Typography variant="h6">Class Attendance</Typography>
						<Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
							<Typography variant="body2" sx={{ color: colors.inkSoft }}>
								{filtered.length === 0 ? "No classes" : `Showing ${page * rowsPerPage + 1}–${Math.min((page + 1) * rowsPerPage, filtered.length)} of ${filtered.length} classes`}
							</Typography>
							<Can anyOf={["ATTENDANCE_MARK"]}>
								<Button variant="contained" startIcon={<EventAvailableIcon />} onClick={onMarkAttendance}>
									Mark Attendance
								</Button>
							</Can>
						</Stack>
					</Box>

					<Stack direction="row" spacing={1.5} sx={{ flexWrap: "wrap", rowGap: 1.5 }}>
						<TextField
							size="small"
							type="date"
							label="Date"
							value={date}
							onChange={(e) => {
								if (e.target.value) setDate(e.target.value);
								setPage(0);
							}}
							slotProps={{ inputLabel: { shrink: true } }}
						/>
						<TextField select size="small" label="Class" value={classId} onChange={(e) => { setClassId(e.target.value); setPage(0); }} sx={{ minWidth: 150 }}>
							<MenuItem value="">All Classes</MenuItem>
							{classOptions.map(([id, name]) => (
								<MenuItem key={id} value={id}>
									{name}
								</MenuItem>
							))}
						</TextField>
						<TextField select size="small" label="Section" value={sectionName} onChange={(e) => { setSectionName(e.target.value); setPage(0); }} sx={{ minWidth: 150 }}>
							<MenuItem value="">All Sections</MenuItem>
							{sectionOptions.map((name) => (
								<MenuItem key={name} value={name}>
									{name}
								</MenuItem>
							))}
						</TextField>
						<TextField select size="small" label="Status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} sx={{ minWidth: 150 }}>
							<MenuItem value="">All Status</MenuItem>
							<MenuItem value="RECORDED">Recorded</MenuItem>
							<MenuItem value="PENDING">Pending</MenuItem>
						</TextField>
						<Button color="inherit" variant="outlined" onClick={clearFilters} disabled={!hasFilters}>
							Clear
						</Button>
					</Stack>

					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Class &amp; Section</TableCell>
									<TableCell>Class Teacher</TableCell>
									<TableCell align="center">Present</TableCell>
									<TableCell align="center">Absent</TableCell>
									<TableCell align="center">Late</TableCell>
									<TableCell sx={{ minWidth: 180 }}>Attendance Rate</TableCell>
									<TableCell>Status</TableCell>
									<TableCell align="right">Action</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{pageRows.map((row) => {
									const recorded = row.status === "RECORDED";
									return (
										<TableRow key={row.sectionId} hover>
											<TableCell sx={{ fontWeight: 700 }}>{row.classLabel}</TableCell>
											<TableCell>{row.teacher ?? "—"}</TableCell>
											<TableCell align="center">{recorded ? row.present : "—"}</TableCell>
											<TableCell align="center" sx={{ color: recorded && row.absent > 0 ? colors.bad : undefined, fontWeight: 600 }}>
												{recorded ? row.absent : "—"}
											</TableCell>
											<TableCell align="center" sx={{ color: recorded && row.late > 0 ? colors.warn : undefined, fontWeight: 600 }}>
												{recorded ? row.late : "—"}
											</TableCell>
											<TableCell>
												{recorded && row.marked > 0 ? (
													<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
														<Typography variant="body2" sx={{ width: 40, fontVariantNumeric: "tabular-nums" }}>
															{row.ratePercent}%
														</Typography>
														<LinearProgress
															variant="determinate"
															value={row.ratePercent}
															sx={{ flexGrow: 1, height: 8, borderRadius: 4, backgroundColor: colors.paperSunken, "& .MuiLinearProgress-bar": { borderRadius: 4, backgroundColor: rateColor(row.ratePercent) } }}
														/>
													</Stack>
												) : (
													<Typography variant="body2" sx={{ color: colors.inkFaint }}>
														—
													</Typography>
												)}
											</TableCell>
											<TableCell>
												<Chip
													size="small"
													label={recorded ? "Recorded" : "Pending"}
													sx={{ fontWeight: 600, color: recorded ? colors.ok : colors.warn, backgroundColor: recorded ? colors.tintGreen : colors.tintOrange }}
												/>
											</TableCell>
											<TableCell align="right">
												<Button
													size="small"
													variant={recorded ? "outlined" : "contained"}
													color={recorded ? "inherit" : "primary"}
													onClick={() => onOpenRegister({ schoolClassId: row.schoolClassId, sectionId: row.sectionId, date })}
												>
													{recorded ? "View" : "Mark Now"}
												</Button>
											</TableCell>
										</TableRow>
									);
								})}
								{overview && pageRows.length === 0 && (
									<TableRow>
										<TableCell colSpan={8} align="center" sx={{ py: 5, color: colors.inkSoft }}>
											{overview.classes.length === 0 ? "No sections have enrolled students yet." : "No classes match these filters."}
										</TableCell>
									</TableRow>
								)}
							</TableBody>
						</Table>
					</TableContainer>
					<TablePagination
						component="div"
						count={filtered.length}
						page={page}
						onPageChange={(_, next) => setPage(next)}
						rowsPerPage={rowsPerPage}
						onRowsPerPageChange={(e) => {
							setRowsPerPage(Number(e.target.value));
							setPage(0);
						}}
						rowsPerPageOptions={[10, 25, 50]}
					/>
				</Stack>
			</Paper>

			<Box sx={{ display: "grid", gap: 2.5, gridTemplateColumns: { xs: "1fr", lg: "1.2fr 1fr" } }}>
				<SectionCard title="Attendance by Class" icon={<BarChartIcon fontSize="small" />} tint="violet">
					<Box sx={{ height: 260 }}>
						{chartData.length > 0 ? (
							<ResponsiveContainer width="100%" height="100%">
								<BarChart data={chartData} margin={{ top: 16 }}>
									<CartesianGrid strokeDasharray="3 3" vertical={false} stroke={colors.rule} />
									<XAxis dataKey="label" tickLine={false} axisLine={false} interval={0} tick={{ fontSize: 12 }} />
									<YAxis domain={[0, 100]} ticks={[0, 25, 50, 75, 100]} tickFormatter={(value) => `${value}%`} tickLine={false} axisLine={false} width={44} />
									<RechartsTooltip formatter={(value) => `${value}%`} />
									<Bar dataKey="rate" name="Attendance rate" radius={[6, 6, 0, 0]}>
										{chartData.map((item) => (
											<Cell key={item.label} fill={rateColor(item.rate)} />
										))}
									</Bar>
								</BarChart>
							</ResponsiveContainer>
						) : (
							<Box sx={{ height: "100%", display: "flex", alignItems: "center", justifyContent: "center" }}>
								<Typography variant="body2" sx={{ color: colors.inkFaint }}>
									{overview ? "No attendance marked for this day." : "Loading…"}
								</Typography>
							</Box>
						)}
					</Box>
				</SectionCard>

				<SectionCard title="Attendance Activity" icon={<ScheduleIcon fontSize="small" />} tint="blue">
					{overview && overview.activity.length > 0 ? (
						<Stack spacing={1.5}>
							{overview.activity.map((item, index) => {
								const style = ACTIVITY_ICON[item.kind];
								return (
									<Box key={`${item.title}-${index}`} sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
										<Box sx={{ width: 36, height: 36, borderRadius: "50%", flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center", color: style.color, backgroundColor: style.bg }}>
											{style.icon}
										</Box>
										<Box sx={{ minWidth: 0, flexGrow: 1 }}>
											<Typography variant="body2" noWrap sx={{ fontWeight: 700 }}>
												{item.title}
											</Typography>
											<Typography variant="caption" noWrap sx={{ color: colors.inkSoft, display: "block" }}>
												{item.detail}
											</Typography>
										</Box>
										{item.at && (
											<Typography variant="caption" sx={{ color: colors.inkSoft, flexShrink: 0 }}>
												{new Date(item.at).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })}
											</Typography>
										)}
									</Box>
								);
							})}
						</Stack>
					) : (
						<Box sx={{ minHeight: 160, display: "flex", alignItems: "center", justifyContent: "center" }}>
							<Typography variant="body2" sx={{ color: colors.inkFaint }}>
								{overview ? "Nothing recorded yet." : "Loading…"}
							</Typography>
						</Box>
					)}
				</SectionCard>
			</Box>
		</Stack>
	);
}
