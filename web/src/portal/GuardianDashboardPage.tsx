import { useEffect, useMemo, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import { ApiError } from "../api/client";
import {
	getChildAttendance,
	downloadChildReceipt,
	downloadChildReportCard,
	getChildFees,
	getChildPayments,
	type GuardianPaymentSummary,
	getChildReportCards,
	getPortalAnnouncements,
	type GuardianAttendanceSummary,
	type GuardianInvoiceSummary,
	type GuardianReportCardSummary,
	type PortalAnnouncementSummary,
} from "../api/portal";
import { useAuth } from "../auth/AuthContext";
import { GuardianCertificatesSection } from "./GuardianCertificatesSection";
import { StatTile } from "../modules/dashboard/StatTile";
import { colors } from "../theme";

function currency(amount: string): string {
	return `₹${Number(amount).toLocaleString("en-IN")}`;
}

function statusColor(status: string | null): "success" | "error" | "warning" | "default" {
	switch (status) {
		case "PRESENT":
			return "success";
		case "ABSENT":
			return "error";
		case "LATE":
		case "HALF_DAY":
			return "warning";
		default:
			return "default";
	}
}

export function GuardianDashboardPage() {
	const { portalIdentity, isModuleEnabled } = useAuth();
	const context = portalIdentity?.portalContexts.find((c) => c.type === "GUARDIAN");
	const children = useMemo(() => context?.children ?? [], [context]);

	const [selectedStudentId, setSelectedStudentId] = useState<number | null>(children[0]?.studentId ?? null);
	const [attendance, setAttendance] = useState<GuardianAttendanceSummary | null>(null);
	const [fees, setFees] = useState<GuardianInvoiceSummary[] | null>(null);
	const [reportCards, setReportCards] = useState<GuardianReportCardSummary[] | null>(null);
	const [payments, setPayments] = useState<GuardianPaymentSummary[]>([]);
	const [announcements, setAnnouncements] = useState<PortalAnnouncementSummary[] | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getPortalAnnouncements()
			.then(setAnnouncements)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load announcements"));
	}, []);

	useEffect(() => {
		if (selectedStudentId === null) return;
		setAttendance(null);
		setFees(null);
		setReportCards(null);
		Promise.all([getChildAttendance(selectedStudentId), getChildFees(selectedStudentId), getChildReportCards(selectedStudentId)])
			.then(([attendanceRes, feesRes, reportCardsRes]) => {
				setAttendance(attendanceRes);
				setFees(feesRes);
				setReportCards(reportCardsRes);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your child's data"));
		setPayments([]);
		getChildPayments(selectedStudentId)
			.then(setPayments)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load payments"));
	}, [selectedStudentId]);

	function handleDownloadReceipt(payment: GuardianPaymentSummary) {
		if (selectedStudentId === null) return;
		downloadChildReceipt(selectedStudentId, payment).catch((err) =>
			setError(err instanceof ApiError ? err.message : "Failed to download the receipt"),
		);
	}

	if (children.length === 0) {
		return (
			<Stack spacing={2}>
				<Typography variant="h5">Parent portal</Typography>
				<Alert severity="info">No children are linked to your account yet. Contact your school's administrator.</Alert>
			</Stack>
		);
	}

	const outstandingTotal = fees?.reduce((sum, invoice) => sum + Number(invoice.outstanding), 0) ?? 0;

	return (
		<Stack spacing={3}>
			<Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ alignItems: { sm: "center" }, justifyContent: "space-between" }}>
				<Typography variant="h5">Parent portal</Typography>
				{selectedStudentId !== null && (
					<Stack direction="row" spacing={1}>
						<Button variant="outlined" size="small" component={RouterLink} to={`/portal/guardian/children/${selectedStudentId}/timetable`}>
							Class timetable
						</Button>
						<Button variant="outlined" size="small" component={RouterLink} to={`/portal/guardian/children/${selectedStudentId}/learning`}>
							Homework &amp; learning
						</Button>
					</Stack>
				)}
				{children.length > 1 && (
					<ToggleButtonGroup
						value={selectedStudentId}
						exclusive
						size="small"
						onChange={(_, next: number | null) => {
							if (next !== null) setSelectedStudentId(next);
						}}
					>
						{children.map((child) => (
							<ToggleButton key={child.studentId} value={child.studentId}>
								{child.name}
							</ToggleButton>
						))}
					</ToggleButtonGroup>
				)}
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{!attendance || !fees || !reportCards ? (
				<Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
					<CircularProgress size={28} />
				</Box>
			) : (
				<>
					<Grid container spacing={2}>
						<Grid size={{ xs: 12, sm: 4 }}>
							<Paper sx={{ p: 2, height: "100%" }}>
								<Stack spacing={0.5}>
									<Typography variant="body2" sx={{ color: colors.inkSoft }}>
										Today's attendance
									</Typography>
									<Box>
										<Chip
											label={attendance.todayStatus ?? "Not marked"}
											color={statusColor(attendance.todayStatus)}
											size="small"
										/>
									</Box>
								</Stack>
							</Paper>
						</Grid>
						<Grid size={{ xs: 12, sm: 4 }}>
							<StatTile
								label="Attendance this month"
								value={`${attendance.monthly.percentage}%`}
								sublabel={`${attendance.monthly.presentCount} present of ${attendance.monthly.totalMarkedDays} marked days`}
							/>
						</Grid>
						<Grid size={{ xs: 12, sm: 4 }}>
							<StatTile label="Fees outstanding" value={currency(outstandingTotal.toFixed(2))} sublabel="Across all invoices" />
						</Grid>
					</Grid>

					<Paper sx={{ p: 2 }}>
						<Typography variant="subtitle1" sx={{ mb: 1 }}>
							Fee invoices
						</Typography>
						{fees.length === 0 ? (
							<Typography variant="body2" color="text.secondary">
								No invoices yet.
							</Typography>
						) : (
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Invoice</TableCell>
										<TableCell>Due date</TableCell>
										<TableCell align="right">Total</TableCell>
										<TableCell align="right">Paid</TableCell>
										<TableCell align="right">Outstanding</TableCell>
										<TableCell>Status</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{fees.map((invoice) => (
										<TableRow key={invoice.invoiceId}>
											<TableCell>{invoice.invoiceNumber}</TableCell>
											<TableCell>{invoice.dueDate}</TableCell>
											<TableCell align="right">{currency(invoice.totalAmount)}</TableCell>
											<TableCell align="right">{currency(invoice.amountPaid)}</TableCell>
											<TableCell align="right">{currency(invoice.outstanding)}</TableCell>
											<TableCell>
												<Chip label={invoice.status} size="small" />
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						)}
					</Paper>

					<Paper sx={{ p: 2 }}>
						<Typography variant="subtitle1" sx={{ mb: 1 }}>
							Payments
						</Typography>
						{payments.length === 0 ? (
							<Typography variant="body2" color="text.secondary">
								No payments yet.
							</Typography>
						) : (
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Receipt</TableCell>
										<TableCell>Date</TableCell>
										<TableCell>Method</TableCell>
										<TableCell align="right">Amount</TableCell>
										<TableCell>Status</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{payments.map((payment) => (
										<TableRow key={payment.paymentId}>
											<TableCell>{payment.receiptNumber}</TableCell>
											<TableCell>{payment.paymentDate}</TableCell>
											<TableCell>{payment.paymentMethod}</TableCell>
											<TableCell align="right">{currency(String(payment.amount))}</TableCell>
											<TableCell>
												<Chip label={payment.status} size="small" />
											</TableCell>
											<TableCell>
												<Button size="small" onClick={() => handleDownloadReceipt(payment)}>
													Download
												</Button>
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						)}
					</Paper>

					<Paper sx={{ p: 2 }}>
						<Typography variant="subtitle1" sx={{ mb: 1 }}>
							Exam results
						</Typography>
						{reportCards.length === 0 ? (
							<Typography variant="body2" color="text.secondary">
								No published results yet.
							</Typography>
						) : (
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Exam</TableCell>
										<TableCell align="right">Percentage</TableCell>
										<TableCell>Grade</TableCell>
										<TableCell>Result</TableCell>
										<TableCell align="right">Class rank</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{reportCards.map((reportCard) => (
										<TableRow key={reportCard.reportCardId}>
											<TableCell>{reportCard.examName}</TableCell>
											<TableCell align="right">{reportCard.percentage}%</TableCell>
											<TableCell>{reportCard.overallGrade}</TableCell>
											<TableCell>
												<Chip
													label={reportCard.passed ? "Passed" : "Failed"}
													size="small"
													color={reportCard.passed ? "success" : "error"}
												/>
											</TableCell>
											<TableCell align="right">{reportCard.classRank ?? "—"}</TableCell>
											<TableCell align="right">
												<Button
													size="small"
													onClick={() =>
														downloadChildReportCard(selectedStudentId!, reportCard.reportCardId).catch((err) =>
															setError(err instanceof ApiError ? err.message : "Failed to download the report card"),
														)
													}
												>
													Download PDF
												</Button>
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						)}
					</Paper>
				</>
			)}

			{/* Outside the loading gate: a child who has left (no current enrollment) still needs their certificates. */}
			{isModuleEnabled("STUDENTS") && selectedStudentId !== null && <GuardianCertificatesSection studentId={selectedStudentId} />}

			<Paper sx={{ p: 2 }}>
				<Typography variant="subtitle1" sx={{ mb: 1 }}>
					Announcements
				</Typography>
				{!announcements ? (
					<CircularProgress size={20} />
				) : announcements.length === 0 ? (
					<Typography variant="body2" color="text.secondary">
						No announcements yet.
					</Typography>
				) : (
					<Stack spacing={1.5} divider={<Box sx={{ borderBottom: `1px solid ${colors.rule}` }} />}>
						{announcements.map((announcement) => (
							<Box key={announcement.id}>
								<Typography variant="subtitle2">{announcement.title}</Typography>
								<Typography variant="body2" color="text.secondary">
									{announcement.body}
								</Typography>
							</Box>
						))}
					</Stack>
				)}
			</Paper>
		</Stack>
	);
}
