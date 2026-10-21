import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import Accordion from "@mui/material/Accordion";
import AccordionDetails from "@mui/material/AccordionDetails";
import AccordionSummary from "@mui/material/AccordionSummary";
import Grid from "@mui/material/Grid";
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
import BadgeIcon from "@mui/icons-material/Badge";
import GroupsIcon from "@mui/icons-material/Groups";
import PaymentsIcon from "@mui/icons-material/Payments";
import {
	dismissQuickActions,
	dismissSetupProgress,
	getDashboardCharts,
	getDashboardSummary,
	type DashboardChartsResponse,
	type DashboardSummaryResponse,
} from "../../api/dashboard";
import { ApiError } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { colors } from "../../theme";
import { HeroBanner, StatCard, StatGrid } from "../../components/DashboardKit";
import { DashboardCharts } from "./DashboardCharts";
import { AnnouncementsCard, AttendanceOverview, UpcomingEventsCard } from "./DashboardWidgets";
import { QuickActions } from "./QuickActions";
import { isSetupComplete, SetupProgress } from "./SetupProgress";
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
	/** Route of the module this block summarises - hidden when the platform owner switches that module off. */
	path: string;
	tiles: { label: string; value: string | number; sublabel?: string }[];
}

const PATH_PERMISSIONS: Record<string, string[]> = {
	"/students": ["STUDENT_VIEW"],
	"/attendance": ["ATTENDANCE_VIEW", "STAFF_ATTENDANCE_VIEW"],
	"/fees": ["FEE_VIEW"],
	"/hr": ["HR_VIEW"],
	"/examinations": ["EXAM_VIEW"],
	"/library": ["LIBRARY_VIEW"],
	"/transport": ["TRANSPORT_VIEW"],
	"/inventory": ["INVENTORY_VIEW"],
	"/sales": ["SALES_VIEW"],
	"/purchase": ["PURCHASE_VIEW"],
	"/communication": ["COMMUNICATION_VIEW"],
	"/academics/setup": ["CLASS_VIEW", "GRADE_LEVEL_VIEW", "SUBJECT_VIEW", "TEACHER_ASSIGNMENT_VIEW"],
	"/students/new": ["STUDENT_MANAGE"],
};

function buildSections(summary: DashboardSummaryResponse): Section[] {
	const sections: (Section | null)[] = [
		summary.attendanceToday && {
			title: "Attendance today",
			path: "/attendance",
			tiles: [
				{ label: "Attendance rate", value: `${summary.attendanceToday.attendanceRatePercent}%`, sublabel: `${summary.attendanceToday.marked} marked` },
				{ label: "Present", value: summary.attendanceToday.present },
				{ label: "Absent", value: summary.attendanceToday.absent },
				{ label: "Late", value: summary.attendanceToday.late },
				{ label: "Half day", value: summary.attendanceToday.halfDay },
			],
		},
		summary.fees && {
			title: "Fees",
			path: "/fees",
			tiles: [
				{ label: "Collection rate", value: `${summary.fees.collectionRatePercent}%` },
				{ label: "Total invoiced", value: currency(summary.fees.totalInvoiced) },
				{ label: "Total collected", value: currency(summary.fees.totalCollected) },
				{ label: "Outstanding", value: currency(summary.fees.outstanding) },
				{ label: "Overdue invoices", value: summary.fees.overdueInvoices },
			],
		},
		summary.staff && {
			title: "Staff",
			path: "/hr",
			tiles: [
				{ label: "Active staff", value: summary.staff.activeStaff },
				{ label: "Departments", value: summary.staff.departments },
				{ label: "Designations", value: summary.staff.designations },
			],
		},
		summary.examinations && {
			title: "Examinations",
			path: "/examinations",
			tiles: [{ label: "Active exams", value: summary.examinations.activeExams }],
		},
		summary.library && {
			title: "Library",
			path: "/library",
			tiles: [
				{ label: "Active books", value: summary.library.activeBooks },
				{ label: "Currently borrowed", value: summary.library.currentlyBorrowed },
				{ label: "Overdue borrows", value: summary.library.overdueBorrows },
			],
		},
		summary.transport && {
			title: "Transport",
			path: "/transport",
			tiles: [
				{ label: "Active vehicles", value: summary.transport.activeVehicles },
				{ label: "Active routes", value: summary.transport.activeRoutes },
				{ label: "Students using transport", value: summary.transport.studentsUsingTransport },
			],
		},
		summary.sales && {
			title: "Sales",
			path: "/sales",
			tiles: [
				{ label: "Sales today", value: currency(summary.sales.totalToday) },
				{ label: "Sales this month", value: currency(summary.sales.totalThisMonth) },
			],
		},
		summary.purchase && {
			title: "Purchase",
			path: "/purchase",
			tiles: [{ label: "Open purchase orders", value: summary.purchase.openOrders }],
		},
		summary.inventory && {
			title: "Inventory",
			path: "/inventory",
			tiles: [
				{ label: "Active items", value: summary.inventory.activeItems },
				{ label: "Categories", value: summary.inventory.categories },
				{ label: "Low stock items", value: summary.inventory.lowStockItems },
			],
		},
		summary.communication && {
			title: "Communication",
			path: "/communication",
			tiles: [{ label: "Announcements this month", value: summary.communication.announcementsThisMonth }],
		},
	];
	return sections.filter((section): section is Section => section !== null);
}

/** Post-login landing page for anyone with ORGANISATION_MANAGE (#30) - a greeting, the
 * setup-progress card and quick actions, headline stats, and a read-only rollup across
 * every module below. */
export function DashboardPage() {
	const { profile, isPathEnabled: isModuleOn, hasAnyPermission, hasPermission, permissionsLoaded } = useAuth();
	// Only ORGANISATION_MANAGE opens the dashboard; anyone else who lands here by URL goes to
	// their own landing page (IndexRedirect) instead of seeing the API's 403 text.
	const canViewDashboard = !permissionsLoaded || hasPermission("ORGANISATION_MANAGE");
	/** A module shows only when it is switched on for the org AND the caller holds a
	 * permission that opens it - ORGANISATION_MANAGE alone doesn't reveal every module. */
	const isPathEnabled = (path: string) => isModuleOn(path) && (!PATH_PERMISSIONS[path] || hasAnyPermission(PATH_PERMISSIONS[path]));
	const navigate = useNavigate();
	const [summary, setSummary] = useState<DashboardSummaryResponse | null>(null);
	const [charts, setCharts] = useState<DashboardChartsResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		if (!canViewDashboard) return;
		getDashboardSummary()
			.then(setSummary)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load dashboard"))
			.finally(() => setLoading(false));
		// Charts are an enhancement - if the request fails the rest of the dashboard still stands.
		getDashboardCharts()
			.then(setCharts)
			.catch(() => setCharts({ attendanceTrend: [], feeCollections: [], studentsByClass: [] }));
	}, [canViewDashboard]);

	/** Optimistic + permanent (#97 dismiss) - flips the flag immediately so the card
	 * disappears without waiting on the round trip; the dismiss endpoints have no undo,
	 * so there's nothing to reconcile if the request fails beyond a silent retry-later. */
	function handleDismissSetupProgress() {
		setSummary((current) => (current ? { ...current, preferences: { ...current.preferences, setupProgressDismissed: true } } : current));
		dismissSetupProgress().catch(() => undefined);
	}

	function handleDismissQuickActions() {
		setSummary((current) => (current ? { ...current, preferences: { ...current.preferences, quickActionsDismissed: true } } : current));
		dismissQuickActions().catch(() => undefined);
	}

	/** No students yet (#125) - lead with one clear next step instead of a wall of
	 * zero-value tiles, checking academic setup before students per the issue's own
	 * dependency order. Once students exist, this returns exactly today's layout. */
	function renderSummary(summaryData: DashboardSummaryResponse) {
		const noStudents = summaryData.enrollment.activeStudents === 0;
		const primaryCta = !summaryData.setupChecklist.academicSetupConfigured
			? { heading: "Set up your academic year", label: "Set up academic year", path: "/academics/setup" }
			: { heading: "Add your first students", label: "Add students", path: "/students/new" };

		// Quick actions are part of the onboarding aid: once every setup step is done they go too.
		const setupComplete = isSetupComplete(summaryData.setupChecklist, isModuleOn);
		const showSetupProgress = !summaryData.preferences.setupProgressDismissed && !setupComplete;
		const showQuickActions = !summaryData.preferences.quickActionsDismissed;
		const att = summaryData.attendanceToday;

		const statRow = (
			<StatGrid>
				{isPathEnabled("/students") && (
					<StatCard icon={<GroupsIcon />} tint="violet" label="Active students" value={summaryData.enrollment.activeStudents} linkLabel="View all students" linkPath="/students" />
				)}
				{isPathEnabled("/hr") && summaryData.staff && (
					<StatCard icon={<BadgeIcon />} tint="green" label="Active staff" value={summaryData.staff.activeStaff} linkLabel="View staff" linkPath="/hr" />
				)}
				{isPathEnabled("/attendance") && att && (
					<StatCard
						icon={<EventAvailableIcon />}
						tint="orange"
						label="Today's attendance"
						value={att.marked === 0 ? "Not started" : `${att.attendanceRatePercent}%`}
						linkLabel="Take attendance"
						linkPath="/attendance"
					/>
				)}
				{isPathEnabled("/fees") && summaryData.fees && (
					<StatCard
						icon={<PaymentsIcon />}
						tint="pink"
						label="Fees due"
						value={currency(summaryData.fees.outstanding)}
						chip={summaryData.fees.overdueInvoices > 0 ? `${summaryData.fees.overdueInvoices} overdue` : undefined}
						chipTone="bad"
						linkLabel="View fee dashboard"
						linkPath="/fees"
					/>
				)}
			</StatGrid>
		);

		const sections = buildSections(summaryData).filter((section) => isPathEnabled(section.path)).map((section) => (
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
						{isPathEnabled(primaryCta.path) && (
							<Button variant="contained" endIcon={<ArrowForwardIcon />} onClick={() => navigate(primaryCta.path)}>
								{primaryCta.label}
							</Button>
						)}
					</Paper>
				)}

				{noStudents ? (
					<Accordion disableGutters sx={{ "&:before": { display: "none" }, boxShadow: "none", border: `1px solid ${colors.rule}` }}>
						<AccordionSummary expandIcon={<ExpandMoreIcon />}>
							<Typography variant="subtitle2" sx={{ color: colors.inkSoft }}>
								Summary stats
							</Typography>
						</AccordionSummary>
						<AccordionDetails>
							<Stack spacing={3}>
								{statRow}
								{sections}
							</Stack>
						</AccordionDetails>
					</Accordion>
				) : (
					statRow
				)}

				{showSetupProgress && <SetupProgress checklist={summaryData.setupChecklist} onDismiss={handleDismissSetupProgress} />}

				<Grid container spacing={2.5}>
					{isPathEnabled("/attendance") && att && (
						<Grid size={{ xs: 12, lg: showQuickActions ? 8 : 12 }}>
							<AttendanceOverview charts={charts} />
						</Grid>
					)}
					{showQuickActions && (
						<Grid size={{ xs: 12, lg: isPathEnabled("/attendance") && att ? 4 : 12 }}>
							<QuickActions onDismiss={handleDismissQuickActions} />
						</Grid>
					)}
					<Grid size={{ xs: 12, md: 6 }}>
						<AnnouncementsCard />
					</Grid>
					<Grid size={{ xs: 12, md: 6 }}>
						<UpcomingEventsCard />
					</Grid>
				</Grid>

				{!noStudents && (
					<>
						<DashboardCharts summary={summaryData} charts={charts} />
						{sections}
					</>
				)}
			</>
		);
	}

	if (!canViewDashboard) {
		return <Navigate to="/" replace />;
	}

	return (
		<Stack spacing={3}>
			<HeroBanner
				title={
					<>
						{greeting()}, {firstName(profile?.personName ?? null)} 👋
					</>
				}
				subtitle="Here's what's happening at your school today."
				quote="Education is the most powerful tool to change the future."
			/>

			{error && <Alert severity="error">{error}</Alert>}

			{loading && <CircularProgress size={28} />}

			{summary && renderSummary(summary)}
		</Stack>
	);
}
