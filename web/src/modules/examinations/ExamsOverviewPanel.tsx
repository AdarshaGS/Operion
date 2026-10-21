import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import InputAdornment from "@mui/material/InputAdornment";
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
import AssignmentIcon from "@mui/icons-material/Assignment";
import BarChartIcon from "@mui/icons-material/BarChart";
import EmojiEventsIcon from "@mui/icons-material/EmojiEvents";
import EventIcon from "@mui/icons-material/Event";
import SearchIcon from "@mui/icons-material/Search";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip as RechartsTooltip } from "recharts";
import type { AcademicYearResponse } from "../../api/academicYears";
import type { ExamOverviewResponse, ExamOverviewRow, ResultStatus } from "../../api/examOverview";
import { SectionCard } from "../../components/DashboardKit";
import { colors } from "../../theme";

const TYPE_LABEL: Record<string, string> = { UNIT_TEST: "Unit Test", MID_TERM: "Mid Term", FINAL: "Final", OTHER: "Other" };

const RESULT_STYLE: Record<ResultStatus, { label: string; color: string; bg: string; action: string }> = {
	PUBLISHED: { label: "Published", color: colors.ok, bg: colors.tintGreen, action: "View" },
	IN_PROGRESS: { label: "In Progress", color: colors.info, bg: colors.tintBlue, action: "Manage" },
	NOT_STARTED: { label: "Not Started", color: colors.inkSoft, bg: colors.paperSunken, action: "Configure" },
	DRAFT: { label: "Draft", color: colors.warn, bg: colors.tintOrange, action: "Configure" },
};

const GRADE_COLORS = [colors.ok, colors.info, colors.warn, colors.accent, colors.bad, colors.ruleStrong];

function formatDate(iso: string | null): string {
	return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }) : "—";
}

function progressColor(percent: number): string {
	return percent >= 100 ? colors.ok : percent > 0 ? colors.info : colors.ruleStrong;
}

function EmptyNote({ children }: { children: string }) {
	return (
		<Box sx={{ minHeight: 160, display: "flex", alignItems: "center", justifyContent: "center" }}>
			<Typography variant="body2" sx={{ color: colors.inkFaint }}>
				{children}
			</Typography>
		</Box>
	);
}

interface Props {
	overview: ExamOverviewResponse | null;
	academicYears: AcademicYearResponse[];
	academicYearId: number | null;
	onAcademicYearChange: (id: number) => void;
}

/** The Examinations overview tab (#134-#139 workflow entry point) - every exam of the chosen year with its marks-entry and
 * result progress, plus the upcoming papers, per-class marks progress and published-result grade split. */
export function ExamsOverviewPanel({ overview, academicYears, academicYearId, onAcademicYearChange }: Props) {
	const navigate = useNavigate();
	const [status, setStatus] = useState("");
	const [examType, setExamType] = useState("");
	const [search, setSearch] = useState("");
	const [page, setPage] = useState(0);
	const [rowsPerPage, setRowsPerPage] = useState(10);

	const filtered = useMemo<ExamOverviewRow[]>(() => {
		const needle = search.trim().toLowerCase();
		return (overview?.exams ?? []).filter(
			(exam) => (!status || exam.resultStatus === status) && (!examType || exam.examType === examType) && (!needle || exam.name.toLowerCase().includes(needle)),
		);
	}, [overview, status, examType, search]);

	const pageRows = filtered.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);
	const summary = overview?.resultSummary;

	return (
		<Stack spacing={3}>
			<Paper sx={{ p: 2.5 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 2, flexWrap: "wrap" }}>
						<Typography variant="h6">Examinations</Typography>
						<Stack direction="row" spacing={1.5} sx={{ flexWrap: "wrap", rowGap: 1.5 }}>
							<TextField select size="small" label="Academic Year" value={academicYearId ?? ""} onChange={(e) => onAcademicYearChange(Number(e.target.value))} sx={{ minWidth: 150 }}>
								{academicYears.map((year) => (
									<MenuItem key={year.id} value={year.id}>
										{year.name}
									</MenuItem>
								))}
							</TextField>
							<TextField
								select
								size="small"
								label="Status"
								value={status}
								onChange={(e) => {
									setStatus(e.target.value);
									setPage(0);
								}}
								sx={{ minWidth: 140 }}
							>
								<MenuItem value="">All Status</MenuItem>
								{(Object.keys(RESULT_STYLE) as ResultStatus[]).map((key) => (
									<MenuItem key={key} value={key}>
										{RESULT_STYLE[key].label}
									</MenuItem>
								))}
							</TextField>
							<TextField
								select
								size="small"
								label="Exam Type"
								value={examType}
								onChange={(e) => {
									setExamType(e.target.value);
									setPage(0);
								}}
								sx={{ minWidth: 140 }}
							>
								<MenuItem value="">All Types</MenuItem>
								{Object.entries(TYPE_LABEL).map(([key, label]) => (
									<MenuItem key={key} value={key}>
										{label}
									</MenuItem>
								))}
							</TextField>
							<TextField
								size="small"
								placeholder="Search examinations..."
								value={search}
								onChange={(e) => {
									setSearch(e.target.value);
									setPage(0);
								}}
								slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
							/>
						</Stack>
					</Box>

					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>#</TableCell>
									<TableCell>Examination Name</TableCell>
									<TableCell>Type</TableCell>
									<TableCell>Classes</TableCell>
									<TableCell>Start Date</TableCell>
									<TableCell>End Date</TableCell>
									<TableCell sx={{ minWidth: 170 }}>Marks Entry</TableCell>
									<TableCell>Result Status</TableCell>
									<TableCell align="right">Actions</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{pageRows.map((exam, index) => {
									const style = RESULT_STYLE[exam.resultStatus];
									return (
										<TableRow key={exam.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/examinations/exams/${exam.id}`)}>
											<TableCell sx={{ color: colors.inkSoft }}>{page * rowsPerPage + index + 1}</TableCell>
											<TableCell>
												<Typography variant="body2" sx={{ fontWeight: 700 }}>
													{exam.name}
												</Typography>
												{exam.status === "INACTIVE" && <Chip size="small" label="Inactive" sx={{ ml: 0.5, height: 18 }} />}
											</TableCell>
											<TableCell>
												<Chip size="small" label={TYPE_LABEL[exam.examType] ?? exam.examType} sx={{ fontWeight: 600, color: colors.accent, backgroundColor: colors.accentSoft }} />
											</TableCell>
											<TableCell>{exam.classes || "—"}</TableCell>
											<TableCell>{formatDate(exam.startDate)}</TableCell>
											<TableCell>{formatDate(exam.endDate)}</TableCell>
											<TableCell>
												<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
													<LinearProgress
														variant="determinate"
														value={exam.marksEntryPercent}
														sx={{ flexGrow: 1, height: 8, borderRadius: 4, backgroundColor: colors.paperSunken, "& .MuiLinearProgress-bar": { borderRadius: 4, backgroundColor: progressColor(exam.marksEntryPercent) } }}
													/>
													<Typography variant="body2" sx={{ width: 40, fontVariantNumeric: "tabular-nums" }}>
														{exam.marksEntryPercent}%
													</Typography>
												</Stack>
											</TableCell>
											<TableCell>
												<Chip size="small" label={style.label} sx={{ fontWeight: 600, color: style.color, backgroundColor: style.bg }} />
											</TableCell>
											<TableCell align="right">
												<Button size="small" variant="outlined" color="inherit" onClick={(e) => { e.stopPropagation(); navigate(`/examinations/exams/${exam.id}`); }}>
													{style.action}
												</Button>
											</TableCell>
										</TableRow>
									);
								})}
								{overview && pageRows.length === 0 && (
									<TableRow>
										<TableCell colSpan={9} align="center" sx={{ py: 5, color: colors.inkSoft }}>
											{overview.exams.length === 0 ? "No examinations yet for this academic year." : "No examinations match these filters."}
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

			<Box sx={{ display: "grid", gap: 2.5, gridTemplateColumns: { xs: "1fr", lg: "1fr 1fr 1fr" } }}>
				<SectionCard title="Exam Schedule (Upcoming)" icon={<EventIcon fontSize="small" />} tint="violet">
					{overview && overview.upcoming.length > 0 ? (
						<Stack spacing={1.5}>
							{overview.upcoming.map((item) => {
								const date = new Date(`${item.examDate}T00:00:00`);
								return (
									<Box
										key={item.scheduleId}
										onClick={() => navigate(`/examinations/exams/${item.examId}`)}
										sx={{ display: "flex", alignItems: "center", gap: 1.5, cursor: "pointer", p: 1, borderRadius: "12px", "&:hover": { backgroundColor: colors.paperSunken } }}
									>
										<Box sx={{ width: 52, height: 52, borderRadius: "12px", flexShrink: 0, textAlign: "center", backgroundColor: colors.tintBlue, color: colors.info, display: "flex", flexDirection: "column", justifyContent: "center" }}>
											<Typography sx={{ fontWeight: 800, fontSize: 20, lineHeight: 1 }}>{date.getDate()}</Typography>
											<Typography variant="caption" sx={{ fontWeight: 700, textTransform: "uppercase" }}>
												{date.toLocaleDateString("en-IN", { month: "short" })}
											</Typography>
										</Box>
										<Box sx={{ minWidth: 0, flexGrow: 1 }}>
											<Typography variant="body2" noWrap sx={{ fontWeight: 700 }}>
												{item.examName}
											</Typography>
											<Typography variant="caption" noWrap sx={{ color: colors.inkSoft, display: "block" }}>
												{item.classLabel} – {item.subjectName}
											</Typography>
										</Box>
										<Chip size="small" label="Upcoming" sx={{ fontWeight: 600, color: colors.accent, backgroundColor: colors.accentSoft }} />
									</Box>
								);
							})}
						</Stack>
					) : (
						<EmptyNote>{overview ? "No upcoming papers scheduled." : "Loading…"}</EmptyNote>
					)}
				</SectionCard>

				<SectionCard title="Marks Entry Progress" icon={<BarChartIcon fontSize="small" />} tint="green">
					{overview && overview.marksByClass.length > 0 ? (
						<Stack spacing={1.5} sx={{ maxHeight: 300, overflowY: "auto", pr: 0.5 }}>
							{overview.marksByClass.map((item) => (
								<Box key={item.schoolClassId} sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
									<Typography variant="body2" sx={{ width: 90, flexShrink: 0 }} noWrap>
										{item.classLabel}
									</Typography>
									<LinearProgress
										variant="determinate"
										value={item.percent}
										sx={{ flexGrow: 1, height: 8, borderRadius: 4, backgroundColor: colors.paperSunken, "& .MuiLinearProgress-bar": { borderRadius: 4, backgroundColor: progressColor(item.percent) } }}
									/>
									<Typography variant="body2" sx={{ width: 40, textAlign: "right", fontVariantNumeric: "tabular-nums" }}>
										{item.percent}%
									</Typography>
								</Box>
							))}
						</Stack>
					) : (
						<EmptyNote>{overview ? "No papers scheduled yet." : "Loading…"}</EmptyNote>
					)}
				</SectionCard>

				<SectionCard title="Result Summary (Published)" icon={<AssignmentIcon fontSize="small" />} tint="blue">
					{summary && summary.grades.length > 0 ? (
						<Stack spacing={2}>
							<Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
								<Box sx={{ position: "relative", width: 160, height: 160, flexShrink: 0 }}>
									<ResponsiveContainer width="100%" height="100%">
										<PieChart>
											<Pie data={summary.grades} dataKey="count" nameKey="grade" innerRadius={50} outerRadius={78} paddingAngle={2} stroke="none">
												{summary.grades.map((item, index) => (
													<Cell key={item.grade} fill={GRADE_COLORS[index % GRADE_COLORS.length]} />
												))}
											</Pie>
											<RechartsTooltip />
										</PieChart>
									</ResponsiveContainer>
									<Box sx={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", pointerEvents: "none" }}>
										<Typography sx={{ fontWeight: 800, fontSize: 24, lineHeight: 1.1 }}>{summary.passRate}%</Typography>
										<Typography variant="caption" sx={{ color: colors.inkSoft }}>
											Pass Rate
										</Typography>
									</Box>
								</Box>
								<Stack spacing={0.75} sx={{ flexGrow: 1, minWidth: 0 }}>
									{summary.grades.map((item, index) => (
										<Box key={item.grade} sx={{ display: "flex", alignItems: "center", gap: 1 }}>
											<Box sx={{ width: 10, height: 10, borderRadius: "50%", flexShrink: 0, backgroundColor: GRADE_COLORS[index % GRADE_COLORS.length] }} />
											<Typography variant="body2" noWrap sx={{ flexGrow: 1 }}>
												{item.grade}
											</Typography>
											<Typography variant="body2" sx={{ fontWeight: 700 }}>
												{item.count}
											</Typography>
										</Box>
									))}
								</Stack>
							</Stack>
							<Box sx={{ display: "flex", alignItems: "center", gap: 1, p: 1.25, borderRadius: "10px", color: colors.ok, backgroundColor: colors.tintGreen }}>
								<EmojiEventsIcon fontSize="small" />
								<Typography variant="body2">
									{overview!.resultsPublishedExams} examination{overview!.resultsPublishedExams === 1 ? "" : "s"} published this academic year
								</Typography>
							</Box>
						</Stack>
					) : (
						<EmptyNote>{overview ? "No results published yet." : "Loading…"}</EmptyNote>
					)}
				</SectionCard>
			</Box>
		</Stack>
	);
}
