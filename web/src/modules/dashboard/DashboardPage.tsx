import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { getDashboardSummary, type DashboardSummaryResponse } from "../../api/dashboard";
import { ApiError } from "../../api/client";
import { OnboardingChecklist } from "./OnboardingChecklist";
import { StatTile } from "./StatTile";

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

interface Section {
	title: string;
	tiles: { label: string; value: string | number; sublabel?: string }[];
}

function buildSections(summary: DashboardSummaryResponse): Section[] {
	return [
		{
			title: "Enrollment",
			tiles: [
				{ label: "Active students", value: summary.enrollment.activeStudents },
				{ label: "Campuses", value: summary.enrollment.campuses },
			],
		},
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

/** Post-login landing page for anyone with ORGANISATION_MANAGE (#30) - a read-only
 * rollup across every module, plus the dismissible getting-started checklist (#97). */
export function DashboardPage() {
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
			{error && <Alert severity="error">{error}</Alert>}

			{loading && <CircularProgress size={28} />}

			{summary && (
				<>
					<OnboardingChecklist checklist={summary.setupChecklist} />

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
