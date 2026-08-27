import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
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
	type AttendanceRegisterResponse,
	correctAttendance,
	getRegister,
	lockRegister,
	markAttendance,
	submitRegister,
} from "../../api/attendance";
import { ApiError } from "../../api/client";
import { type StudentEnrollmentResponse, listSectionEnrollments } from "../../api/enrollments";
import { type PersonResponse, listPersons } from "../../api/persons";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { listStudents, type StudentResponse } from "../../api/students";

const STATUS_OPTIONS = ["PRESENT", "ABSENT", "LATE", "HALF_DAY"];

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

interface DraftRow {
	enrollmentId: number;
	status: string;
	excused: boolean;
	remarks: string;
}

export function AttendancePage() {
	const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
	const [sections, setSections] = useState<SectionResponse[]>([]);
	const [classId, setClassId] = useState("");
	const [sectionId, setSectionId] = useState("");
	const [date, setDate] = useState(todayIso());

	// Kept in state (not just local to handleLoad) so both the fresh-marking table and
	// the existing-register table can resolve enrollmentId -> studentId -> person name.
	const [enrollments, setEnrollments] = useState<StudentEnrollmentResponse[]>([]);
	const [students, setStudents] = useState<StudentResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);

	const [loaded, setLoaded] = useState(false);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const [register, setRegister] = useState<AttendanceRegisterResponse | null>(null);
	const [draftRows, setDraftRows] = useState<DraftRow[] | null>(null);
	const [correctingId, setCorrectingId] = useState<number | null>(null);
	const [correctStatus, setCorrectStatus] = useState("PRESENT");
	const [correctReason, setCorrectReason] = useState("");

	useEffect(() => {
		listSchoolClasses().then(setClasses).catch(() => {});
	}, []);

	useEffect(() => {
		setSectionId("");
		setSections([]);
		if (!classId) return;
		listSections(Number(classId)).then(setSections).catch(() => {});
	}, [classId]);

	function enrollmentStudentName(enrollmentId: number): string {
		const enrollment = enrollments.find((e) => e.id === enrollmentId);
		if (!enrollment) return `Enrollment #${enrollmentId}`;
		const student = students.find((s) => s.id === enrollment.studentId);
		if (!student) return `Student #${enrollment.studentId}`;
		const person = persons.find((p) => p.id === student.personId);
		return person ? `${person.firstName} ${person.lastName}` : `Student #${student.id}`;
	}

	async function handleLoad() {
		if (!sectionId) return;
		setLoading(true);
		setError(null);
		setLoaded(false);
		setRegister(null);
		setDraftRows(null);
		try {
			const [enrollmentList, studentList, personList] = await Promise.all([
				listSectionEnrollments(Number(sectionId)),
				students.length ? Promise.resolve(students) : listStudents(),
				persons.length ? Promise.resolve(persons) : listPersons(),
			]);
			setEnrollments(enrollmentList);
			setStudents(studentList);
			setPersons(personList);

			const existing = await getRegister(Number(sectionId), date);
			if (existing) {
				setRegister(existing);
			} else {
				setDraftRows(enrollmentList.map((enrollment) => ({ enrollmentId: enrollment.id, status: "PRESENT", excused: false, remarks: "" })));
			}
			setLoaded(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to load attendance");
		} finally {
			setLoading(false);
		}
	}

	function updateDraftRow(enrollmentId: number, patch: Partial<DraftRow>) {
		setDraftRows((rows) => (rows ? rows.map((row) => (row.enrollmentId === enrollmentId ? { ...row, ...patch } : row)) : rows));
	}

	function rollNumberFor(enrollmentId: number): number | null {
		return enrollments.find((e) => e.id === enrollmentId)?.rollNumber ?? null;
	}

	async function handleSubmitMarks() {
		if (!draftRows || !sectionId) return;
		setLoading(true);
		setError(null);
		try {
			const result = await markAttendance(Number(sectionId), {
				attendanceDate: date,
				marks: draftRows.map((row) => ({
					studentEnrollmentId: row.enrollmentId,
					status: row.status,
					excused: row.excused,
					remarks: row.remarks || null,
				})),
			});
			setRegister(result);
			setDraftRows(null);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to mark attendance");
		} finally {
			setLoading(false);
		}
	}

	async function handleSubmitRegister() {
		if (!register) return;
		setLoading(true);
		try {
			setRegister(await submitRegister(register.register.id));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to submit register");
		} finally {
			setLoading(false);
		}
	}

	async function handleLockRegister() {
		if (!register) return;
		setLoading(true);
		try {
			setRegister(await lockRegister(register.register.id));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to lock register");
		} finally {
			setLoading(false);
		}
	}

	async function handleCorrect() {
		if (correctingId === null) return;
		setLoading(true);
		try {
			await correctAttendance(correctingId, correctStatus, correctReason);
			setRegister(await getRegister(Number(sectionId), date));
			setCorrectingId(null);
			setCorrectReason("");
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to correct attendance");
		} finally {
			setLoading(false);
		}
	}

	return (
		<Stack spacing={3}>
			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Class register</Typography>
					<Box sx={{ display: "flex", gap: 2, alignItems: "flex-start" }}>
						<TextField select label="Class" value={classId} onChange={(e) => setClassId(e.target.value)} sx={{ minWidth: 200 }}>
							{classes.map((schoolClass) => (
								<MenuItem key={schoolClass.id} value={schoolClass.id}>
									{schoolClass.displayName ?? `Class #${schoolClass.id}`}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Section"
							value={sectionId}
							onChange={(e) => setSectionId(e.target.value)}
							disabled={!classId}
							sx={{ minWidth: 160 }}
						>
							{sections.map((section) => (
								<MenuItem key={section.id} value={section.id}>
									{section.name}
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
						<Button variant="contained" disabled={!sectionId || loading} onClick={handleLoad}>
							Load
						</Button>
					</Box>
				</Stack>
			</Paper>

			{error && <Alert severity="error">{error}</Alert>}

			{loading && (
				<Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
					<CircularProgress />
				</Box>
			)}

			{loaded && draftRows && (
				<Paper sx={{ p: 3 }}>
					<Stack spacing={2}>
						<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
							<Typography variant="h6">Mark attendance — {date}</Typography>
							<Button variant="contained" onClick={handleSubmitMarks} disabled={loading || draftRows.length === 0}>
								Submit marks
							</Button>
						</Box>

						{draftRows.length === 0 && <Alert severity="info">No currently-enrolled students in this section.</Alert>}

						{draftRows.length > 0 && (
							<TableContainer>
								<Table size="small">
									<TableHead>
										<TableRow>
											<TableCell>Roll #</TableCell>
											<TableCell>Student</TableCell>
											<TableCell>Status</TableCell>
											<TableCell>Excused</TableCell>
											<TableCell>Remarks</TableCell>
										</TableRow>
									</TableHead>
									<TableBody>
										{draftRows.map((row) => (
											<TableRow key={row.enrollmentId}>
												<TableCell>{rollNumberFor(row.enrollmentId) ?? "—"}</TableCell>
												<TableCell>{enrollmentStudentName(row.enrollmentId)}</TableCell>
												<TableCell>
													<TextField
														select
														size="small"
														value={row.status}
														onChange={(e) => updateDraftRow(row.enrollmentId, { status: e.target.value })}
														sx={{ minWidth: 130 }}
													>
														{STATUS_OPTIONS.map((status) => (
															<MenuItem key={status} value={status}>
																{status}
															</MenuItem>
														))}
													</TextField>
												</TableCell>
												<TableCell>
													<Checkbox
														checked={row.excused}
														onChange={(e) => updateDraftRow(row.enrollmentId, { excused: e.target.checked })}
													/>
												</TableCell>
												<TableCell>
													<TextField
														size="small"
														value={row.remarks}
														onChange={(e) => updateDraftRow(row.enrollmentId, { remarks: e.target.value })}
													/>
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

			{loaded && register && (
				<Paper sx={{ p: 3 }}>
					<Stack spacing={2}>
						<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
							<Typography variant="h6">Register — {register.register.attendanceDate}</Typography>
							<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
								<Chip label={register.register.registerStatus} size="small" />
								{register.register.registerStatus === "DRAFT" && (
									<Button size="small" variant="contained" onClick={handleSubmitRegister} disabled={loading}>
										Submit
									</Button>
								)}
								{register.register.registerStatus === "SUBMITTED" && (
									<Button size="small" variant="contained" onClick={handleLockRegister} disabled={loading}>
										Lock
									</Button>
								)}
							</Stack>
						</Box>

						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Student</TableCell>
										<TableCell>Status</TableCell>
										<TableCell>Excused</TableCell>
										<TableCell>Remarks</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{register.entries.map((entry) => (
										<TableRow key={entry.id}>
											<TableCell>{enrollmentStudentName(entry.studentEnrollmentId)}</TableCell>
											<TableCell>
												<Chip label={entry.attendanceStatus} size="small" />
											</TableCell>
											<TableCell>{entry.excused ? "Yes" : "No"}</TableCell>
											<TableCell>{entry.remarks ?? "—"}</TableCell>
											<TableCell>
												{register.register.registerStatus !== "LOCKED" && (
													<Button
														size="small"
														onClick={() => {
															setCorrectingId(entry.id);
															setCorrectStatus(entry.attendanceStatus);
															setCorrectReason("");
														}}
													>
														Correct
													</Button>
												)}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					</Stack>
				</Paper>
			)}

			<Dialog open={correctingId !== null} onClose={() => setCorrectingId(null)} fullWidth maxWidth="xs">
				<DialogTitle>Correct attendance</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="New status" value={correctStatus} onChange={(e) => setCorrectStatus(e.target.value)} fullWidth>
							{STATUS_OPTIONS.map((status) => (
								<MenuItem key={status} value={status}>
									{status}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Reason"
							value={correctReason}
							onChange={(e) => setCorrectReason(e.target.value)}
							required
							fullWidth
							multiline
							rows={2}
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setCorrectingId(null)}>Cancel</Button>
					<Button variant="contained" onClick={handleCorrect} disabled={!correctReason || loading}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
