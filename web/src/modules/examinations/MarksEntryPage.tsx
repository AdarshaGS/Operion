import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
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
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { ApiError } from "../../api/client";
import { listSectionEnrollments, type StudentEnrollmentResponse } from "../../api/enrollments";
import { listSchedules, type ExamScheduleResponse } from "../../api/exams";
import { correctMarks, enterMarks, listMarks, type MarksEntryResponse } from "../../api/marks";
import { type PersonResponse, listPersons } from "../../api/persons";
import { listSections } from "../../api/sections";
import { listStudents, type StudentResponse } from "../../api/students";
import { listSubjects, type SubjectResponse } from "../../api/subjects";

interface DraftRow {
	enrollmentId: number;
	marksObtained: string;
	absent: boolean;
	remarks: string;
}

/** ExamSchedule is scoped by schoolClassId, not a single section - marks entry needs
 * every currently-enrolled student across all of the class's sections, composed from
 * listSections + listSectionEnrollments since no "enrollments by school class" endpoint
 * exists (same list+compose tradeoff already documented for FeesPage's class lookup). */
export function MarksEntryPage() {
	const { examId, scheduleId } = useParams<{ examId: string; scheduleId: string }>();
	const navigate = useNavigate();

	const [schedule, setSchedule] = useState<ExamScheduleResponse | null>(null);
	const [subjects, setSubjects] = useState<SubjectResponse[]>([]);
	const [enrollments, setEnrollments] = useState<StudentEnrollmentResponse[]>([]);
	const [students, setStudents] = useState<StudentResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);

	const [marks, setMarks] = useState<MarksEntryResponse[] | null>(null);
	const [draftRows, setDraftRows] = useState<DraftRow[] | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);

	const [correctingId, setCorrectingId] = useState<number | null>(null);
	const [correctMarksValue, setCorrectMarksValue] = useState("");
	const [correctAbsent, setCorrectAbsent] = useState(false);
	const [correctRemarks, setCorrectRemarks] = useState("");

	useEffect(() => {
		if (!examId || !scheduleId) return;
		let cancelled = false;
		setLoading(true);
		(async () => {
			try {
				const [schedules, subjectList, studentList, personList] = await Promise.all([
					listSchedules(Number(examId)),
					listSubjects(),
					listStudents(),
					listPersons(),
				]);
				const found = schedules.find((s) => s.id === Number(scheduleId));
				if (!found) throw new Error("Schedule not found");
				if (cancelled) return;
				setSchedule(found);
				setSubjects(subjectList);
				setStudents(studentList);
				setPersons(personList);

				const sections = await listSections(found.schoolClassId);
				const enrollmentLists = await Promise.all(sections.map((section) => listSectionEnrollments(section.id)));
				const allEnrollments = enrollmentLists.flat();
				if (cancelled) return;
				setEnrollments(allEnrollments);

				const existingMarks = await listMarks(Number(scheduleId));
				if (cancelled) return;
				if (existingMarks.length > 0) {
					setMarks(existingMarks);
				} else {
					setDraftRows(
						allEnrollments.map((enrollment) => ({ enrollmentId: enrollment.id, marksObtained: "", absent: false, remarks: "" })),
					);
				}
			} catch (err) {
				if (!cancelled) setError(err instanceof ApiError ? err.message : "Failed to load marks entry");
			} finally {
				if (!cancelled) setLoading(false);
			}
		})();
		return () => {
			cancelled = true;
		};
	}, [examId, scheduleId]);

	function studentNameFor(enrollmentId: number): string {
		const enrollment = enrollments.find((e) => e.id === enrollmentId);
		if (!enrollment) return `Enrollment #${enrollmentId}`;
		const student = students.find((s) => s.id === enrollment.studentId);
		if (!student) return `Student #${enrollment.studentId}`;
		const person = persons.find((p) => p.id === student.personId);
		return person ? `${person.firstName} ${person.lastName}` : `Student #${student.id}`;
	}

	function updateDraftRow(enrollmentId: number, patch: Partial<DraftRow>) {
		setDraftRows((rows) => (rows ? rows.map((row) => (row.enrollmentId === enrollmentId ? { ...row, ...patch } : row)) : rows));
	}

	async function handleSubmitMarks() {
		if (!draftRows || !scheduleId) return;
		setSubmitting(true);
		try {
			const result = await enterMarks(
				Number(scheduleId),
				draftRows.map((row) => ({
					studentEnrollmentId: row.enrollmentId,
					marksObtained: row.absent ? null : row.marksObtained ? Number(row.marksObtained) : null,
					absent: row.absent,
					remarks: row.remarks || null,
				})),
			);
			setMarks(result);
			setDraftRows(null);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to enter marks");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleCorrect() {
		if (correctingId === null) return;
		setSubmitting(true);
		try {
			await correctMarks(correctingId, correctAbsent ? null : correctMarksValue ? Number(correctMarksValue) : null, correctAbsent, correctRemarks);
			const refreshed = await listMarks(Number(scheduleId));
			setMarks(refreshed);
			setCorrectingId(null);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to correct marks");
		} finally {
			setSubmitting(false);
		}
	}

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	return (
		<Stack spacing={2} sx={{ maxWidth: 900 }}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate(`/examinations/exams/${examId}`)}>
					Back to exam
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				{schedule ? subjects.find((s) => s.id === schedule.subjectId)?.name ?? `Subject #${schedule.subjectId}` : "Marks entry"}
			</Typography>
			{schedule && (
				<Typography variant="body2" color="text.secondary">
					{schedule.examDate} — max {schedule.maxMarks}, pass {schedule.passMarks}
				</Typography>
			)}

			{error && <Alert severity="error">{error}</Alert>}

			{draftRows && (
				<Paper sx={{ p: 3 }}>
					<Stack spacing={2}>
						<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
							<Typography variant="h6">Enter marks</Typography>
							<Button variant="contained" onClick={handleSubmitMarks} disabled={submitting || draftRows.length === 0}>
								Submit marks
							</Button>
						</Box>

						{draftRows.length === 0 && <Alert severity="info">No currently-enrolled students in this class.</Alert>}

						{draftRows.length > 0 && (
							<TableContainer>
								<Table size="small">
									<TableHead>
										<TableRow>
											<TableCell>Student</TableCell>
											<TableCell>Marks obtained</TableCell>
											<TableCell>Absent</TableCell>
											<TableCell>Remarks</TableCell>
										</TableRow>
									</TableHead>
									<TableBody>
										{draftRows.map((row) => (
											<TableRow key={row.enrollmentId}>
												<TableCell>{studentNameFor(row.enrollmentId)}</TableCell>
												<TableCell>
													<TextField
														size="small"
														type="number"
														value={row.marksObtained}
														disabled={row.absent}
														onChange={(e) => updateDraftRow(row.enrollmentId, { marksObtained: e.target.value })}
														sx={{ width: 120 }}
													/>
												</TableCell>
												<TableCell>
													<Checkbox
														checked={row.absent}
														onChange={(e) => updateDraftRow(row.enrollmentId, { absent: e.target.checked, marksObtained: "" })}
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

			{marks && (
				<Paper sx={{ p: 3 }}>
					<Stack spacing={2}>
						<Typography variant="h6">Marks</Typography>
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Student</TableCell>
										<TableCell>Marks obtained</TableCell>
										<TableCell>Absent</TableCell>
										<TableCell>Remarks</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{marks.map((entry) => (
										<TableRow key={entry.id}>
											<TableCell>{studentNameFor(entry.studentEnrollmentId)}</TableCell>
											<TableCell>{entry.absent ? "—" : entry.marksObtained}</TableCell>
											<TableCell>{entry.absent ? "Yes" : "No"}</TableCell>
											<TableCell>{entry.remarks ?? "—"}</TableCell>
											<TableCell>
												<Button
													size="small"
													onClick={() => {
														setCorrectingId(entry.id);
														setCorrectMarksValue(entry.marksObtained != null ? String(entry.marksObtained) : "");
														setCorrectAbsent(entry.absent);
														setCorrectRemarks(entry.remarks ?? "");
													}}
												>
													Correct
												</Button>
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
				<DialogTitle>Correct marks</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Marks obtained"
							type="number"
							value={correctMarksValue}
							disabled={correctAbsent}
							onChange={(e) => setCorrectMarksValue(e.target.value)}
							fullWidth
						/>
						<Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
							<Checkbox checked={correctAbsent} onChange={(e) => setCorrectAbsent(e.target.checked)} />
							<Typography>Absent</Typography>
						</Box>
						<TextField label="Remarks" value={correctRemarks} onChange={(e) => setCorrectRemarks(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setCorrectingId(null)}>Cancel</Button>
					<Button variant="contained" onClick={handleCorrect} disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
