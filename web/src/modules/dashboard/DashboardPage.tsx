import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
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
	const [summary, setSummary] = useState<DashboardSummaryResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		getDashboardSummary()
			.then(setSummary)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load dashboard"))
			.finally(() => setLoading(false));
	}, []);

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

			{summary && (
				<>
					<Stack direction={{ xs: "column", md: "row" }} spacing={3} sx={{ alignItems: "stretch" }}>
						<Box sx={{ flex: "3 1 0" }}>
							<SetupProgress checklist={summary.setupChecklist} />
						</Box>
						<Box sx={{ flex: "2 1 0" }}>
							<QuickActions />
						</Box>
					</Stack>

					<Stack direction="row" spacing={3} sx={{ flexWrap: "wrap" }}>
						<HeroStatCard
							icon={<GroupsIcon />}
							label="Active students"
							value={summary.enrollment.activeStudents}
							linkLabel="View all students"
							linkPath="/students"
						/>
						<HeroStatCard
							icon={<EventAvailableIcon />}
							label="Today's attendance"
							value={summary.attendanceToday.marked === 0 ? "Not started" : `${summary.attendanceToday.attendanceRatePercent}%`}
							linkLabel="Take attendance"
							linkPath="/attendance"
						/>
						<HeroStatCard
							icon={<PaymentsIcon />}
							label="Fees due"
							value={currency(summary.fees.outstanding)}
							linkLabel="View fee dashboard"
							linkPath="/fees"
						/>
					</Stack>

					<RecentActivity />

					{buildSections(summary).map((section) => (
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
					))}
				</>
			)}
		</Stack>
	);
}
