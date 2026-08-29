import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Accordion from "@mui/material/Accordion";
import AccordionDetails from "@mui/material/AccordionDetails";
import AccordionSummary from "@mui/material/AccordionSummary";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import GroupsIcon from "@mui/icons-material/Groups";
import PaymentsIcon from "@mui/icons-material/Payments";
import { getDashboardSummary, type DashboardSummaryResponse } from "../../api/dashboard";
import { ApiError } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { colors } from "../../theme";
import { HeroStatCard } from "./HeroStatCard";
import { QuickActions } from "./QuickActions";
import { RecentActivity } from "./RecentActivity";
import { SetupProgress } from "./SetupProgress";
import { StatTile } from "./StatTile";

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

function greeting(): string {
	const hour = new Date().getHours();
	if (hour < 12) return "Good morning";
	if (hour < 17) return "Good afternoon";
	return "Good evening";
}

function firstName(fullName: string | null): string {
	if (!fullName) return "there";
	return fullName.split(" ")[0];
}

interface Section {
	title: string;
	tiles: { label: string; value: string | number; sublabel?: string }[];
}

function buildSections(summary: DashboardSummaryResponse): Section[] {
	return [
		{
			title: "Attendance today",
			tiles: [
				{ label: "Attendance rate", value: `${summary.attendanceToday.attendanceRatePercent}%`, sublabel: `${summary.attendanceToday.marked} marked` },
				{ label: "Present", value: summary.attendanceToday.present },
				{ label: "Absent", value: summary.attendanceToday.absent },
				{ label: "Late", value: summary.attendanceToday.late },
				{ label: "Half day", value: summary.attendanceToday.halfDay },
			],
		},
		{
			title: "Fees",
			tiles: [
				{ label: "Collection rate", value: `${summary.fees.collectionRatePercent}%` },
				{ label: "Total invoiced", value: currency(summary.fees.totalInvoiced) },
				{ label: "Total collected", value: currency(summary.fees.totalCollected) },
				{ label: "Outstanding", value: currency(summary.fees.outstanding) },
				{ label: "Overdue invoices", value: summary.fees.overdueInvoices },
			],
		},
		{
			title: "Staff",
			tiles: [
				{ label: "Active staff", value: summary.staff.activeStaff },
				{ label: "Departments", value: summary.staff.departments },
				{ label: "Designations", value: summary.staff.designations },
			],
		},
		{
			title: "Examinations",
			tiles: [{ label: "Active exams", value: summary.examinations.activeExams }],
		},
		{
			title: "Library",
			tiles: [
				{ label: "Active books", value: summary.library.activeBooks },
				{ label: "Currently borrowed", value: summary.library.currentlyBorrowed },
				{ label: "Overdue borrows", value: summary.library.overdueBorrows },
			],
		},
		{
			title: "Transport",
			tiles: [
				{ label: "Active vehicles", value: summary.transport.activeVehicles },
				{ label: "Active routes", value: summary.transport.activeRoutes },
				{ label: "Students using transport", value: summary.transport.studentsUsingTransport },
			],
		},
		{
			title: "Inventory",
			tiles: [
				{ label: "Active items", value: summary.inventory.activeItems },
				{ label: "Categories", value: summary.inventory.categories },
			],
		},
		{
			title: "Communication",
			tiles: [{ label: "Announcements this month", value: summary.communication.announcementsThisMonth }],
		},
	];
}

/** Post-login landing page for anyone with ORGANISATION_MANAGE (#30) - a greeting, the
 * setup-progress card and quick actions, headline stats, and a read-only rollup across
 * every module below. */
export function DashboardPage() {
	const { profile } = useAuth();
	const navigate = useNavigate();
	const [summary, setSummary] = useState<DashboardSummaryResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		getDashboardSummary()
			.then(setSummary)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load dashboard"))
			.finally(() => setLoading(false));
	}, []);

	/** No students yet (#125) - lead with one clear next step instead of a wall of
	 * zero-value tiles, checking academic setup before students per the issue's own
	 * dependency order. Once students exist, this returns exactly today's layout. */
	function renderSummary(summaryData: DashboardSummaryResponse) {
		const noStudents = summaryData.enrollment.activeStudents === 0;
		const primaryCta = !summaryData.setupChecklist.academicSetupConfigured
			? { heading: "Set up your academic year", label: "Set up academic year", path: "/academics/setup" }
			: { heading: "Add your first students", label: "Add students", path: "/students/new" };

		const heroRow = (
			<Stack direction="row" spacing={3} sx={{ flexWrap: "wrap" }}>
				<HeroStatCard
					icon={<GroupsIcon />}
					label="Active students"
					value={summaryData.enrollment.activeStudents}
					linkLabel="View all students"
					linkPath="/students"
				/>
				<HeroStatCard
					icon={<EventAvailableIcon />}
					label="Today's attendance"
					value={summaryData.attendanceToday.marked === 0 ? "Not started" : `${summaryData.attendanceToday.attendanceRatePercent}%`}
					linkLabel="Take attendance"
					linkPath="/attendance"
				/>
				<HeroStatCard
					icon={<PaymentsIcon />}
					label="Fees due"
					value={currency(summaryData.fees.outstanding)}
					linkLabel="View fee dashboard"
					linkPath="/fees"
				/>
			</Stack>
		);

		const sections = buildSections(summaryData).map((section) => (
			<Box key={section.title}>
				<Typography variant="h6" sx={{ mb: 1.5 }}>
					{section.title}
				</Typography>
				<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
					{section.tiles.map((tile) => (
						<Box key={tile.label} sx={{ flex: "1 1 180px", minWidth: 180 }}>
							<StatTile label={tile.label} value={tile.value} sublabel={tile.sublabel} />
						</Box>
					))}
				</Stack>
			</Box>
		));

		return (
			<>
				{noStudents && (
					<Paper
						sx={{
							p: 3,
							display: "flex",
							alignItems: "center",
							justifyContent: "space-between",
							gap: 2,
							flexWrap: "wrap",
							backgroundColor: colors.accentSoft,
							border: `1px solid ${colors.accent}`,
						}}
					>
						<Typography variant="subtitle1" sx={{ color: colors.ink, fontWeight: 700 }}>
							{primaryCta.heading}
						</Typography>
						<Button variant="contained" endIcon={<ArrowForwardIcon />} onClick={() => navigate(primaryCta.path)}>
							{primaryCta.label}
						</Button>
					</Paper>
				)}

				<Stack direction={{ xs: "column", md: "row" }} spacing={3} sx={{ alignItems: "stretch" }}>
					<Box sx={{ flex: "3 1 0" }}>
						<SetupProgress checklist={summaryData.setupChecklist} />
					</Box>
					<Box sx={{ flex: "2 1 0" }}>
						<QuickActions />
					</Box>
				</Stack>

				<RecentActivity />

				{noStudents ? (
					<Accordion disableGutters sx={{ "&:before": { display: "none" }, boxShadow: "none", border: `1px solid ${colors.rule}` }}>
						<AccordionSummary expandIcon={<ExpandMoreIcon />}>
							<Typography variant="subtitle2" sx={{ color: colors.inkSoft }}>
								Summary stats
							</Typography>
						</AccordionSummary>
						<AccordionDetails>
							<Stack spacing={3}>
								{heroRow}
								{sections}
							</Stack>
						</AccordionDetails>
					</Accordion>
				) : (
					<>
						{heroRow}
						{sections}
					</>
				)}
			</>
		);
	}

	return (
		<Stack spacing={3}>
			<Box>
				<Typography variant="h4" sx={{ color: colors.ink }}>
					{greeting()}, {firstName(profile?.personName ?? null)}
				</Typography>
				<Typography variant="body1" sx={{ color: colors.inkSoft, mt: 0.5 }}>
					Here's what's happening at your school today.
				</Typography>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			{loading && <CircularProgress size={28} />}

			{summary && renderSummary(summary)}
		</Stack>
	);
}
