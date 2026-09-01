import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import CircularProgress from "@mui/material/CircularProgress";
import Link from "@mui/material/Link";
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
import { useNavigate } from "react-router-dom";
import { getRegister, markAttendance } from "../../api/attendance";
import { ApiError } from "../../api/client";
import { type StudentEnrollmentResponse, listSectionEnrollments } from "../../api/enrollments";
import { type PersonResponse, listPersons } from "../../api/persons";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { listStudents, type StudentResponse } from "../../api/students";

const STATUS_OPTIONS = ["PRESENT", "ABSENT", "LATE", "HALF_DAY", "LEAVE"];

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

interface DraftRow {
	enrollmentId: number;
	status: string;
	excused: boolean;
	remarks: string;
}

/** Focused "mark today's attendance for a class" entry point (#204) - distinct from
 * AttendancePage's full register (date navigation, submit/lock lifecycle, corrections).
 * Always today's date; if the section is already marked for today, hands off to the full
 * register instead of duplicating its submit/lock/correct workflow here. */
export function MarkAttendanceQuickPage() {
	const navigate = useNavigate();
	const today = todayIso();

	const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
	const [sections, setSections] = useState<SectionResponse[]>([]);
	const [classId, setClassId] = useState("");
	const [sectionId, setSectionId] = useState("");

	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [success, setSuccess] = useState(false);
	const [alreadyMarked, setAlreadyMarked] = useState(false);

	const [enrollments, setEnrollments] = useState<StudentEnrollmentResponse[]>([]);
	const [students, setStudents] = useState<StudentResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [rows, setRows] = useState<DraftRow[] | null>(null);

	useEffect(() => {
		listSchoolClasses().then(setClasses).catch(() => {});
	}, []);

	useEffect(() => {
		setSectionId("");
		setSections([]);
		if (!classId) return;
		listSections(Number(classId)).then(setSections).catch(() => {});
	}, [classId]);

	useEffect(() => {
		setRows(null);
		setAlreadyMarked(false);
		setSuccess(false);
		setError(null);
		if (!sectionId) return;

		setLoading(true);
		Promise.all([
			listSectionEnrollments(Number(sectionId)),
			students.length ? Promise.resolve(students) : listStudents(),
			persons.length ? Promise.resolve(persons) : listPersons(),
			getRegister(Number(sectionId), today),
		])
			.then(([enrollmentList, studentList, personList, existingRegister]) => {
				setEnrollments(enrollmentList);
				setStudents(studentList);
				setPersons(personList);
				if (existingRegister) {
					setAlreadyMarked(true);
				} else {
					setRows(enrollmentList.map((enrollment) => ({
						enrollmentId: enrollment.id, status: "PRESENT", excused: false, remarks: "",
					})));
				}
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load section"))
			.finally(() => setLoading(false));
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [sectionId, today]);

	function studentName(enrollmentId: number): string {
		const enrollment = enrollments.find((e) => e.id === enrollmentId);
		if (!enrollment) return `Enrollment #${enrollmentId}`;
		const student = students.find((s) => s.id === enrollment.studentId);
		if (!student) return `Student #${enrollment.studentId}`;
		const person = persons.find((p) => p.id === student.personId);
		return person ? `${person.firstName} ${person.lastName}` : `Student #${student.id}`;
	}

	function rollNumberFor(enrollmentId: number): number | null {
		return enrollments.find((e) => e.id === enrollmentId)?.rollNumber ?? null;
	}

	function updateRow(enrollmentId: number, patch: Partial<DraftRow>) {
		setRows((current) => (current ? current.map((row) => (row.enrollmentId === enrollmentId ? { ...row, ...patch } : row)) : current));
	}

	function handleMarkAllPresent() {
		setRows((current) => (current ? current.map((row) => ({ ...row, status: "PRESENT" })) : current));
	}

	async function handleSubmit() {
		if (!rows || !sectionId) return;
		setLoading(true);
		setError(null);
		try {
			await markAttendance(Number(sectionId), {
				attendanceDate: today,
				marks: rows.map((row) => ({
					studentEnrollmentId: row.enrollmentId,
					status: row.status,
					excused: row.excused,
					remarks: row.remarks || null,
				})),
			});
			setSuccess(true);
			setRows(null);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to mark attendance");
		} finally {
			setLoading(false);
		}
	}

	return (
		<Stack spacing={3}>
			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Mark attendance — {today}</Typography>
					<Box sx={{ display: "flex", gap: 2 }}>
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
					</Box>
				</Stack>
			</Paper>

			{error && <Alert severity="error">{error}</Alert>}

			{success && (
				<Alert severity="success">
					Attendance marked for today.{" "}
					<Link component="button" onClick={() => navigate("/attendance")}>
						View the register
					</Link>
				</Alert>
			)}

			{alreadyMarked && (
				<Alert severity="info">
					Attendance for this section has already been marked today.{" "}
					<Link component="button" onClick={() => navigate("/attendance")}>
						Open the full register
					</Link>{" "}
					to review or correct it.
				</Alert>
			)}

			{loading && (
				<Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
					<CircularProgress />
				</Box>
			)}

			{rows && (
				<Paper sx={{ p: 3 }}>
					<Stack spacing={2}>
						<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
							<Typography variant="subtitle1">{rows.length} student{rows.length === 1 ? "" : "s"}</Typography>
							<Stack direction="row" spacing={1}>
								<Button onClick={handleMarkAllPresent} disabled={loading || rows.length === 0}>
									Mark all present
								</Button>
								<Button variant="contained" onClick={handleSubmit} disabled={loading || rows.length === 0}>
									Submit
								</Button>
							</Stack>
						</Box>

						{rows.length === 0 && <Alert severity="info">No currently-enrolled students in this section.</Alert>}

						{rows.length > 0 && (
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
										{rows.map((row) => (
											<TableRow key={row.enrollmentId}>
												<TableCell>{rollNumberFor(row.enrollmentId) ?? "—"}</TableCell>
												<TableCell>{studentName(row.enrollmentId)}</TableCell>
												<TableCell>
													<TextField
														select
														size="small"
														value={row.status}
														onChange={(e) => updateRow(row.enrollmentId, { status: e.target.value })}
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
														onChange={(e) => updateRow(row.enrollmentId, { excused: e.target.checked })}
													/>
												</TableCell>
												<TableCell>
													<TextField
														size="small"
														value={row.remarks}
														onChange={(e) => updateRow(row.enrollmentId, { remarks: e.target.value })}
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
		</Stack>
	);
}
