import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import AssignmentIcon from "@mui/icons-material/Assignment";
import BarChartIcon from "@mui/icons-material/BarChart";
import EditNoteIcon from "@mui/icons-material/EditNote";
import EventIcon from "@mui/icons-material/Event";
import GradingIcon from "@mui/icons-material/Grading";
import SettingsIcon from "@mui/icons-material/Settings";
import { listAcademicYears, type AcademicYearResponse } from "../../api/academicYears";
import { ApiError } from "../../api/client";
import { getExamOverview, type ExamOverviewResponse } from "../../api/examOverview";
import { Can } from "../../auth/Can";
import { KpiTile } from "../../components/DashboardKit";
import { colors } from "../../theme";
import { CreateExamDialog } from "./CreateExamDialog";
import { ExaminationSettingsPanel } from "./ExaminationSettingsPanel";
import { ExamsOverviewPanel } from "./ExamsOverviewPanel";
import { GradingScalesPanel } from "./GradingScalesPanel";

function formatDate(iso: string | null | undefined): string {
	return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }) : "";
}


/** Examinations landing page - headline counts for the chosen academic year over an Overview / Grading Scales / Settings
 * tab set. Per-exam work (schedules, marks entry, report cards) stays on the exam detail page the overview rows open. */
export function ExaminationsPage() {
	const navigate = useNavigate();
	const [tab, setTab] = useState<"overview" | "grading" | "settings">("overview");
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [academicYearId, setAcademicYearId] = useState<number | null>(null);
	const [overview, setOverview] = useState<ExamOverviewResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [createOpen, setCreateOpen] = useState(false);

	useEffect(() => {
		listAcademicYears()
			.then((years) => {
				setAcademicYears(years);
				setAcademicYearId(years.find((y) => y.current)?.id ?? years[0]?.id ?? null);
			})
			.catch(() => {});
	}, []);

	function load() {
		if (!academicYearId) return;
		getExamOverview(academicYearId)
			.then((result) => {
				setOverview(result);
				setError(null);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load examinations"));
	}

	useEffect(() => {
		setOverview(null);
		load();
	}, [academicYearId]);

	const openFirst = (examId: number | undefined) => (examId ? () => navigate(`/examinations/exams/${examId}`) : undefined);

	return (
		<Stack spacing={3}>
			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 2, flexWrap: "wrap" }}>
				<Box>
					<Typography variant="h4" sx={{ fontWeight: 800, color: colors.ink }}>
						Examinations
					</Typography>
					<Typography variant="body1" sx={{ color: colors.inkSoft }}>
						Manage examination schedules, marks, grading and student results.
					</Typography>
				</Box>
				<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", rowGap: 1 }}>
					<Can anyOf={["EXAM_MANAGE"]}>
						<Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)} disabled={!academicYearId}>
							Create Examination
						</Button>
					</Can>
					<Button variant="outlined" color="inherit" startIcon={<GradingIcon />} onClick={() => setTab("grading")}>
						Grading Scales
					</Button>
					<Button variant="outlined" color="inherit" startIcon={<SettingsIcon />} onClick={() => setTab("settings")}>
						Examination Settings
					</Button>
				</Stack>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			<Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr", lg: "repeat(4, 1fr)" } }}>
				<KpiTile icon={<AssignmentIcon />} tint="violet" label="Total Examinations" value={overview?.totalExams ?? "—"} detail={overview ? "this academic year" : undefined} />
				<KpiTile
					icon={<EventIcon />}
					tint="blue"
					label="Upcoming Examinations"
					value={overview?.upcomingExams ?? "—"}
					detail={overview?.nextExam ? `Next: ${overview.nextExam.name} · ${formatDate(overview.nextExam.startDate)}` : overview ? "None scheduled" : undefined}
					onClick={openFirst(overview?.nextExam?.examId)}
				/>
				<KpiTile
					icon={<EditNoteIcon />}
					tint="orange"
					label="Marks Pending"
					value={overview?.marksPendingSubjects ?? "—"}
					detail={overview ? `Subject${overview.marksPendingSubjects === 1 ? "" : "s"} across ${overview.marksPendingClasses} class${overview.marksPendingClasses === 1 ? "" : "es"}` : undefined}
				/>
				<KpiTile
					icon={<BarChartIcon />}
					tint="green"
					label="Results Published"
					value={overview?.resultsPublishedExams ?? "—"}
					detail={overview ? (overview.lastPublished ? `Last: ${overview.lastPublished.name}${overview.lastPublished.endDate ? ` · ${formatDate(overview.lastPublished.endDate)}` : ""}` : "None yet") : undefined}
					onClick={openFirst(overview?.lastPublished?.examId)}
				/>
			</Box>

			<Tabs value={tab} onChange={(_, next) => setTab(next)} sx={{ borderBottom: `1px solid ${colors.rule}` }}>
				<Tab value="overview" label="Overview" icon={<AssignmentIcon fontSize="small" />} iconPosition="start" />
				<Tab value="grading" label="Grading Scales" icon={<GradingIcon fontSize="small" />} iconPosition="start" />
				<Tab value="settings" label="Settings" icon={<SettingsIcon fontSize="small" />} iconPosition="start" />
			</Tabs>

			{tab === "overview" && <ExamsOverviewPanel overview={overview} academicYears={academicYears} academicYearId={academicYearId} onAcademicYearChange={setAcademicYearId} />}
			{tab === "grading" && <GradingScalesPanel />}
			{tab === "settings" && <ExaminationSettingsPanel />}

			<CreateExamDialog
				open={createOpen}
				academicYearId={academicYearId}
				onClose={() => setCreateOpen(false)}
				onCreated={() => {
					setCreateOpen(false);
					load();
				}}
			/>
		</Stack>
	);
}
