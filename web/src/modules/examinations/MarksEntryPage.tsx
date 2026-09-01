import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
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
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { ApiError } from "../../api/client";
import { listSectionEnrollments, type StudentEnrollmentResponse } from "../../api/enrollments";
import { listSchedules, type ExamScheduleResponse } from "../../api/exams";
import {
	approveRegister,
	correctMarks,
	correctMarksAfterPublish,
	enterMarks,
	getRegister,
	listMarks,
	submitRegister,
	type MarksEntryRegisterResponse,
	type MarksEntryResponse,
} from "../../api/marks";
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

function auditTooltip(entry: MarksEntryResponse): string {
	const entered = `Entered by user #${entry.enteredBy ?? "—"} on ${new Date(entry.enteredAt).toLocaleString()}`;
	if (!entry.correctedAt) return entered;
	return `${entered} · Last corrected by user #${entry.correctedBy ?? "—"} on ${new Date(entry.correctedAt).toLocaleString()}`;
}

/** ExamSchedule is scoped by schoolClassId (and optionally sectionId, #139) - marks entry
 * needs every currently-enrolled student in the applicable section(s), composed from
 * listSections + listSectionEnrollments since no "enrollments by class/section" endpoint
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
	const [register, setRegister] = useState<MarksEntryRegisterResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);

	const [correctingEntry, setCorrectingEntry] = useState<MarksEntryResponse | null>(null);
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

				const sectionIds = found.sectionId
					? [found.sectionId]
					: (await listSections(found.schoolClassId)).map((section) => section.id);
				const enrollmentLists = await Promise.all(sectionIds.map((sectionId) => listSectionEnrollments(sectionId)));
				const allEnrollments = enrollmentLists.flat();
				if (cancelled) return;
				setEnrollments(allEnrollments);

				const [existingMarks, currentRegister] = await Promise.all([listMarks(Number(scheduleId)), getRegister(Number(scheduleId))]);
				if (cancelled) return;
				setRegister(currentRegister);
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

	async function refreshRegister() {
		if (!scheduleId) return;
		try {
			setRegister(await getRegister(Number(scheduleId)));
		} catch {
			// non-fatal - keep the previous register state visible
		}
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
			await refreshRegister();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to enter marks");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleSubmitRegister() {
		if (!scheduleId) return;
		setSubmitting(true);
		try {
			setRegister(await submitRegister(Number(scheduleId)));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to submit for review");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleApproveRegister() {
		if (!scheduleId) return;
		setSubmitting(true);
		try {
			setRegister(await approveRegister(Number(scheduleId)));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to approve");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleCorrect() {
		if (!correctingEntry || !scheduleId) return;
		setSubmitting(true);
		try {
			const value = correctAbsent ? null : correctMarksValue ? Number(correctMarksValue) : null;
			if (correctingEntry.published) {
				await correctMarksAfterPublish(correctingEntry.id, value, correctAbsent, correctRemarks);
			} else {
				await correctMarks(correctingEntry.id, value, correctAbsent, correctRemarks);
			}
			const refreshed = await listMarks(Number(scheduleId));
			setMarks(refreshed);
			setCorrectingEntry(null);
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
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate(`/examinations/exams/${examId}`)}>
					Back to exam
				</Button>
			</Box>

			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
				<Box>
					<Typography variant="h4" component="h1">
						{schedule ? subjects.find((s) => s.id === schedule.subjectId)?.name ?? `Subject #${schedule.subjectId}` : "Marks entry"}
					</Typography>
					{schedule && (
						<Typography variant="body2" color="text.secondary">
							{schedule.examDate} — max {schedule.maxMarks}, pass {schedule.passMarks}
						</Typography>
					)}
				</Box>
				{register && (
					<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
						<Chip label={register.registerStatus} color={register.registerStatus === "APPROVED" ? "success" : "default"} />
						{register.registerStatus === "DRAFT" && marks && (
							<Button size="small" variant="outlined" onClick={handleSubmitRegister} disabled={submitting}>
								Submit for review
							</Button>
						)}
						{register.registerStatus === "SUBMITTED" && (
							<Button size="small" variant="outlined" onClick={handleApproveRegister} disabled={submitting}>
								Approve
							</Button>
						)}
					</Stack>
				)}
			</Box>

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
										<TableCell>Pass/Fail</TableCell>
										<TableCell>Rank</TableCell>
										<TableCell>Remarks</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{marks.map((entry) => (
										<TableRow key={entry.id}>
											<TableCell>
												<Tooltip title={auditTooltip(entry)}>
													<span>{studentNameFor(entry.studentEnrollmentId)}</span>
												</Tooltip>
											</TableCell>
											<TableCell>{entry.absent ? "—" : entry.marksObtained}</TableCell>
											<TableCell>{entry.absent ? "Yes" : "No"}</TableCell>
											<TableCell>
												<Chip size="small" color={entry.passed ? "success" : "error"} label={entry.passed ? "PASS" : "FAIL"} />
											</TableCell>
											<TableCell>{entry.rank ?? "—"}</TableCell>
											<TableCell>{entry.remarks ?? "—"}</TableCell>
											<TableCell>
												<Button
													size="small"
													onClick={() => {
														setCorrectingEntry(entry);
														setCorrectMarksValue(entry.marksObtained != null ? String(entry.marksObtained) : "");
														setCorrectAbsent(entry.absent);
														setCorrectRemarks(entry.remarks ?? "");
													}}
												>
													{entry.published ? "Correct (published)" : "Correct"}
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

			<Dialog open={correctingEntry !== null} onClose={() => setCorrectingEntry(null)} fullWidth maxWidth="xs">
				<DialogTitle>{correctingEntry?.published ? "Correct marks (report card already published)" : "Correct marks"}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{correctingEntry?.published && (
							<Alert severity="warning">This student's report card is already published - correcting will flag it stale until republished.</Alert>
						)}
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
					<Button onClick={() => setCorrectingEntry(null)}>Cancel</Button>
					<Button variant="contained" onClick={handleCorrect} disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
