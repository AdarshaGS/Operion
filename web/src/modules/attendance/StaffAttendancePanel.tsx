import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import {
	type StaffAttendanceResponse,
	checkOutStaff,
	listStaffAttendance,
	markStaffAttendance,
} from "../../api/attendance";
import { ApiError } from "../../api/client";
import { type CampusResponse, listCampuses } from "../../api/campuses";
import { type PersonResponse, listPersons } from "../../api/persons";
import { type StaffProfileResponse, listStaffProfiles } from "../../api/staffProfiles";
import { useAuth } from "../../auth/AuthContext";

const STATUS_OPTIONS = ["PRESENT", "ABSENT", "LATE", "HALF_DAY", "LEAVE"];
const MARK_FORM = { personId: "", status: "PRESENT", checkInTime: "", remarks: "" };

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

/** Combines the register date with an HH:MM picked in the browser's time zone into an ISO instant. */
function toInstant(date: string, time: string): string {
	return new Date(`${date}T${time}`).toISOString();
}

function formatTime(instant: string | null): string {
	return instant ? new Date(instant).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—";
}

/** Staff check-in/check-out for one campus and day (ATT-13/14). Marking needs STAFF_ATTENDANCE_MARK. */
export function StaffAttendancePanel() {
	const { hasPermission } = useAuth();
	const canMark = hasPermission("STAFF_ATTENDANCE_MARK");

	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [staff, setStaff] = useState<StaffProfileResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [campusId, setCampusId] = useState("");
	const [date, setDate] = useState(todayIso());
	const [rows, setRows] = useState<StaffAttendanceResponse[] | null>(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const [markOpen, setMarkOpen] = useState(false);
	const [markForm, setMarkForm] = useState(MARK_FORM);
	const [markError, setMarkError] = useState<string | null>(null);
	const [checkOutTarget, setCheckOutTarget] = useState<StaffAttendanceResponse | null>(null);
	const [checkOutTime, setCheckOutTime] = useState("");
	const [checkOutError, setCheckOutError] = useState<string | null>(null);

	useEffect(() => {
		listCampuses()
			.then((list) => {
				setCampuses(list);
				if (list.length === 1) setCampusId(String(list[0].id));
			})
			.catch(() => {});
		// Staff names are a convenience - without HR_VIEW the rows fall back to "Person #id".
		listStaffProfiles().then(setStaff).catch(() => {});
		listPersons().then(setPersons).catch(() => {});
	}, []);

	function personName(personId: number): string {
		const person = persons.find((p) => p.id === personId);
		return person ? `${person.firstName} ${person.lastName}` : `Person #${personId}`;
	}

	async function handleLoad() {
		if (!campusId) return;
		setLoading(true);
		setError(null);
		try {
			setRows(await listStaffAttendance(Number(campusId), date));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to load staff attendance");
		} finally {
			setLoading(false);
		}
	}

	async function handleMark() {
		setMarkError(null);
		try {
			await markStaffAttendance({
				personId: Number(markForm.personId),
				campusId: Number(campusId),
				attendanceDate: date,
				status: markForm.status,
				checkInTime: markForm.checkInTime ? toInstant(date, markForm.checkInTime) : null,
				remarks: markForm.remarks || null,
			});
			setMarkOpen(false);
			setMarkForm(MARK_FORM);
			await handleLoad();
		} catch (err) {
			setMarkError(err instanceof ApiError ? err.message : "Failed to mark staff attendance");
		}
	}

	async function handleCheckOut() {
		if (!checkOutTarget) return;
		setCheckOutError(null);
		try {
			await checkOutStaff(checkOutTarget.id, toInstant(date, checkOutTime));
			setCheckOutTarget(null);
			await handleLoad();
		} catch (err) {
			setCheckOutError(err instanceof ApiError ? err.message : "Failed to check out");
		}
	}

	const markedPersonIds = new Set((rows ?? []).map((row) => row.personId));
	const campusStaff = staff.filter(
		(profile) =>
			(profile.status === "ACTIVE" || profile.status === "ON_LEAVE") &&
			(profile.campusId === null || String(profile.campusId) === campusId),
	);

	return (
		<Stack spacing={3}>
			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Staff attendance</Typography>
					<Box sx={{ display: "flex", gap: 2, alignItems: "flex-start" }}>
						<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value)} sx={{ minWidth: 200 }}>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Date"
							type="date"
							value={date}
							onChange={(e) => setDate(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
						/>
						<Button variant="contained" disabled={!campusId || loading} onClick={handleLoad}>
							Load
						</Button>
					</Box>
				</Stack>
			</Paper>

			{error && <Alert severity="error">{error}</Alert>}

			{rows && (
				<Paper sx={{ p: 3 }}>
					<Stack spacing={2}>
						<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
							<Typography variant="h6">Staff — {date}</Typography>
							{canMark && (
								<Button
									variant="contained"
									size="small"
									onClick={() => {
										setMarkForm(MARK_FORM);
										setMarkError(null);
										setMarkOpen(true);
									}}
								>
									Mark staff
								</Button>
							)}
						</Box>
						{rows.length === 0 && <Alert severity="info">No staff attendance recorded for this day.</Alert>}
						{rows.length > 0 && (
							<TableContainer>
								<Table size="small">
									<TableHead>
										<TableRow>
											<TableCell>Staff</TableCell>
											<TableCell>Status</TableCell>
											<TableCell>Check-in</TableCell>
											<TableCell>Check-out</TableCell>
											<TableCell>Remarks</TableCell>
											<TableCell />
										</TableRow>
									</TableHead>
									<TableBody>
										{rows.map((row) => (
											<TableRow key={row.id}>
												<TableCell>{personName(row.personId)}</TableCell>
												<TableCell>
													<Chip label={row.attendanceStatus} size="small" />
												</TableCell>
												<TableCell>{formatTime(row.checkInTime)}</TableCell>
												<TableCell>{formatTime(row.checkOutTime)}</TableCell>
												<TableCell>{row.remarks ?? "—"}</TableCell>
												<TableCell>
													{canMark && row.checkInTime && !row.checkOutTime && (
														<Button
															size="small"
															onClick={() => {
																setCheckOutTarget(row);
																setCheckOutTime("");
																setCheckOutError(null);
															}}
														>
															Check out
														</Button>
													)}
												</TableCell>
											</TableRow>
										))}
									</TableBody>
								</Table>
							</TableContainer>
						)}
					</Stack>
				</Paper>
			)}

			<Dialog open={markOpen} onClose={() => setMarkOpen(false)} fullWidth maxWidth="xs">
				<DialogTitle>Mark staff attendance</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{markError && <Alert severity="error">{markError}</Alert>}
						<TextField
							select
							label="Staff member"
							value={markForm.personId}
							onChange={(e) => setMarkForm((prev) => ({ ...prev, personId: e.target.value }))}
							required
							fullWidth
						>
							{campusStaff
								.filter((profile) => !markedPersonIds.has(profile.personId))
								.map((profile) => (
									<MenuItem key={profile.id} value={profile.personId}>
										{personName(profile.personId)} ({profile.employeeCode})
									</MenuItem>
								))}
						</TextField>
						<TextField
							select
							label="Status"
							value={markForm.status}
							onChange={(e) => setMarkForm((prev) => ({ ...prev, status: e.target.value }))}
							fullWidth
						>
							{STATUS_OPTIONS.map((status) => (
								<MenuItem key={status} value={status}>
									{status}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Check-in time"
							type="time"
							value={markForm.checkInTime}
							onChange={(e) => setMarkForm((prev) => ({ ...prev, checkInTime: e.target.value }))}
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField
							label="Remarks"
							value={markForm.remarks}
							onChange={(e) => setMarkForm((prev) => ({ ...prev, remarks: e.target.value }))}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setMarkOpen(false)}>Cancel</Button>
					<Button variant="contained" onClick={handleMark} disabled={!markForm.personId}>
						Save
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={checkOutTarget !== null} onClose={() => setCheckOutTarget(null)} fullWidth maxWidth="xs">
				<DialogTitle>Check out {checkOutTarget ? personName(checkOutTarget.personId) : ""}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{checkOutError && <Alert severity="error">{checkOutError}</Alert>}
						<TextField
							label="Check-out time"
							type="time"
							value={checkOutTime}
							onChange={(e) => setCheckOutTime(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setCheckOutTarget(null)}>Cancel</Button>
					<Button variant="contained" onClick={handleCheckOut} disabled={!checkOutTime}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
