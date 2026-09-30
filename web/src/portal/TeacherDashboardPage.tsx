import { useEffect, useState, type FormEvent } from "react";
import { Link as RouterLink } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { listAcademicYears, type AcademicYearResponse } from "../api/academicYears";
import { ApiError } from "../api/client";
import type { LeaveTypeResponse } from "../api/leaveTypes";
import {
	getPortalLeaveTypes,
	getTeacherLeaveBalance,
	getTeacherLeaveRequests,
	getTeacherSections,
	raiseOwnLeaveRequest,
	type TeacherLeaveBalanceSummary,
	type TeacherLeaveRequestSummary,
	type TeacherSectionSummary,
} from "../api/portal";

function statusColor(status: string): "success" | "error" | "warning" | "default" {
	switch (status) {
		case "APPROVED":
			return "success";
		case "REJECTED":
		case "CANCELLED":
			return "error";
		case "PENDING":
			return "warning";
		default:
			return "default";
	}
}

/** #208: own sections, leave balance/history, and a self-raise leave request form.
 * Own timetable and payslip are explicitly out of scope for this pass - see the issue. */
export function TeacherDashboardPage() {
	const [sections, setSections] = useState<TeacherSectionSummary[] | null>(null);
	const [leaveBalance, setLeaveBalance] = useState<TeacherLeaveBalanceSummary[] | null>(null);
	const [leaveRequests, setLeaveRequests] = useState<TeacherLeaveRequestSummary[] | null>(null);
	const [academicYear, setAcademicYear] = useState<AcademicYearResponse | null>(null);
	const [leaveTypes, setLeaveTypes] = useState<LeaveTypeResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [raiseOpen, setRaiseOpen] = useState(false);
	const [leaveTypeId, setLeaveTypeId] = useState<number | "">("");
	const [startDate, setStartDate] = useState("");
	const [endDate, setEndDate] = useState("");
	const [numberOfDays, setNumberOfDays] = useState("");
	const [reason, setReason] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refreshLeaveData(yearId: number) {
		Promise.all([getTeacherLeaveBalance(yearId), getTeacherLeaveRequests()])
			.then(([balance, requests]) => {
				setLeaveBalance(balance);
				setLeaveRequests(requests);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load leave data"));
	}

	useEffect(() => {
		getTeacherSections()
			.then(setSections)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your sections"));
		getPortalLeaveTypes()
			.then(setLeaveTypes)
			.catch(() => {});
		listAcademicYears()
			.then((years) => {
				const current = years.find((y) => y.current) ?? years[0] ?? null;
				setAcademicYear(current);
				if (current) refreshLeaveData(current.id);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load academic year"));
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	async function handleRaise(event: FormEvent) {
		event.preventDefault();
		if (!academicYear || leaveTypeId === "") return;
		setSubmitting(true);
		try {
			await raiseOwnLeaveRequest({
				leaveTypeId,
				academicYearId: academicYear.id,
				startDate,
				endDate,
				numberOfDays: Number(numberOfDays),
				reason: reason || null,
			});
			setRaiseOpen(false);
			setLeaveTypeId("");
			setStartDate("");
			setEndDate("");
			setNumberOfDays("");
			setReason("");
			refreshLeaveData(academicYear.id);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to raise leave request");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Stack spacing={3}>
			<Typography variant="h5">Teacher portal</Typography>
			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 2 }}>
				<Typography variant="subtitle1" sx={{ mb: 1 }}>
					Your sections
				</Typography>
				{!sections ? (
					<CircularProgress size={20} />
				) : sections.length === 0 ? (
					<Typography variant="body2" color="text.secondary">
						No sections assigned yet.
					</Typography>
				) : (
					<Grid container spacing={2}>
						{sections.map((section) => (
							<Grid key={`${section.sectionId}-${section.subjectName ?? "homeroom"}`} size={{ xs: 12, sm: 6, md: 4 }}>
								<Paper variant="outlined" sx={{ p: 2 }}>
									<Typography variant="subtitle2">{section.schoolClassDisplayName} - {section.sectionName}</Typography>
									<Typography variant="body2" color="text.secondary">
										{section.subjectName ?? "Homeroom"}
									</Typography>
								</Paper>
							</Grid>
						))}
					</Grid>
				)}
			</Paper>

			<Paper sx={{ p: 2 }}>
				<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="subtitle1">Homework, quizzes &amp; learning materials</Typography>
					<Button size="small" component={RouterLink} to="/portal/teaching">
						Open
					</Button>
				</Stack>
			</Paper>

			<Paper sx={{ p: 2 }}>
				<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="subtitle1">Payslips</Typography>
					<Button size="small" component={RouterLink} to="/portal/payslips">
						View my payslips
					</Button>
				</Stack>
			</Paper>

			<Paper sx={{ p: 2 }}>
				<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 1 }}>
					<Typography variant="subtitle1">Leave</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setRaiseOpen(true)} disabled={!academicYear}>
						Request leave
					</Button>
				</Stack>

				{!leaveBalance ? (
					<CircularProgress size={20} />
				) : (
					<>
						<Grid container spacing={2} sx={{ mb: 2 }}>
							{leaveBalance.map((balance) => (
								<Grid key={balance.leaveTypeId} size={{ xs: 12, sm: 6, md: 4 }}>
									<Paper variant="outlined" sx={{ p: 2 }}>
										<Typography variant="body2" color="text.secondary">
											{balance.leaveTypeName}
										</Typography>
										<Typography variant="h6">
											{balance.remainingDays} / {balance.allocatedDays} days
										</Typography>
									</Paper>
								</Grid>
							))}
						</Grid>

						<Typography variant="subtitle2" sx={{ mb: 1 }}>
							History
						</Typography>
						{!leaveRequests || leaveRequests.length === 0 ? (
							<Typography variant="body2" color="text.secondary">
								No leave requests yet.
							</Typography>
						) : (
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Type</TableCell>
										<TableCell>From</TableCell>
										<TableCell>To</TableCell>
										<TableCell align="right">Days</TableCell>
										<TableCell>Status</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{leaveRequests.map((request) => (
										<TableRow key={request.id}>
											<TableCell>{request.leaveTypeName}</TableCell>
											<TableCell>{request.startDate}</TableCell>
											<TableCell>{request.endDate}</TableCell>
											<TableCell align="right">{request.numberOfDays}</TableCell>
											<TableCell>
												<Chip label={request.status} size="small" color={statusColor(request.status)} />
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						)}
					</>
				)}
			</Paper>

			<Dialog open={raiseOpen} onClose={() => setRaiseOpen(false)} component="form" onSubmit={handleRaise} fullWidth maxWidth="sm">
				<DialogTitle>Request leave</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							select
							label="Leave type"
							value={leaveTypeId}
							onChange={(e) => setLeaveTypeId(Number(e.target.value))}
							required
							fullWidth
						>
							{leaveTypes.map((type) => (
								<MenuItem key={type.id} value={type.id}>
									{type.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							type="date"
							label="Start date"
							value={startDate}
							onChange={(e) => setStartDate(e.target.value)}
							required
							fullWidth
							slotProps={{ inputLabel: { shrink: true } }}
						/>
						<TextField
							type="date"
							label="End date"
							value={endDate}
							onChange={(e) => setEndDate(e.target.value)}
							required
							fullWidth
							slotProps={{ inputLabel: { shrink: true } }}
						/>
						<TextField
							type="number"
							label="Number of days"
							value={numberOfDays}
							onChange={(e) => setNumberOfDays(e.target.value)}
							required
							fullWidth
							slotProps={{ htmlInput: { step: "0.5", min: "0.5" } }}
						/>
						<TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} fullWidth multiline minRows={2} />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setRaiseOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Submit
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
