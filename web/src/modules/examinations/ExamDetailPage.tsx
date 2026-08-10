import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
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
import AddIcon from "@mui/icons-material/Add";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { ApiError } from "../../api/client";
import { addSchedule, getExam, listSchedules, type ExamResponse, type ExamScheduleResponse } from "../../api/exams";
import { listStudentEnrollments } from "../../api/enrollments";
import { listGradingScales, type GradingScaleResponse } from "../../api/gradingScales";
import { type PersonResponse, listPersons } from "../../api/persons";
import { publishReportCard, type ReportCardResponse } from "../../api/reportCards";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listStudents, type StudentResponse } from "../../api/students";
import { listSubjects, type SubjectResponse } from "../../api/subjects";

export function ExamDetailPage() {
	const { examId } = useParams<{ examId: string }>();
	const navigate = useNavigate();

	const [exam, setExam] = useState<ExamResponse | null>(null);
	const [schedules, setSchedules] = useState<ExamScheduleResponse[]>([]);
	const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
	const [subjects, setSubjects] = useState<SubjectResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [scheduleDialogOpen, setScheduleDialogOpen] = useState(false);
	const [schoolClassId, setSchoolClassId] = useState("");
	const [subjectId, setSubjectId] = useState("");
	const [examDate, setExamDate] = useState("");
	const [maxMarks, setMaxMarks] = useState("100");
	const [passMarks, setPassMarks] = useState("35");

	const [students, setStudents] = useState<StudentResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [gradingScales, setGradingScales] = useState<GradingScaleResponse[]>([]);
	const [reportDialogOpen, setReportDialogOpen] = useState(false);
	const [reportStudentId, setReportStudentId] = useState("");
	const [gradingScaleId, setGradingScaleId] = useState("");
	const [publishedReport, setPublishedReport] = useState<ReportCardResponse | null>(null);

	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		if (!examId) return;
		getExam(Number(examId))
			.then(setExam)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load exam"));
		refreshSchedules();
		listSchoolClasses().then(setClasses).catch(() => {});
		listSubjects().then(setSubjects).catch(() => {});
		listStudents().then(setStudents).catch(() => {});
		listPersons().then(setPersons).catch(() => {});
		listGradingScales().then(setGradingScales).catch(() => {});
	}, [examId]);

	function refreshSchedules() {
		if (!examId) return;
		listSchedules(Number(examId))
			.then(setSchedules)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load schedules"));
	}

	function className(id: number): string {
		return classes.find((c) => c.id === id)?.displayName ?? `Class #${id}`;
	}

	function subjectName(id: number): string {
		return subjects.find((s) => s.id === id)?.name ?? `Subject #${id}`;
	}

	function studentLabel(student: StudentResponse): string {
		const person = persons.find((p) => p.id === student.personId);
		return person ? `${person.firstName} ${person.lastName} (${student.admissionNumber})` : student.admissionNumber;
	}

	async function handleAddSchedule(event: FormEvent) {
		event.preventDefault();
		if (!examId) return;
		setSubmitting(true);
		try {
			await addSchedule(Number(examId), {
				schoolClassId: Number(schoolClassId),
				subjectId: Number(subjectId),
				examDate,
				maxMarks: Number(maxMarks),
				passMarks: Number(passMarks),
			});
			setSchoolClassId("");
			setSubjectId("");
			setExamDate("");
			setScheduleDialogOpen(false);
			refreshSchedules();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add schedule");
		} finally {
			setSubmitting(false);
		}
	}

	async function handlePublishReportCard(event: FormEvent) {
		event.preventDefault();
		if (!examId) return;
		setSubmitting(true);
		setPublishedReport(null);
		try {
			const enrollments = await listStudentEnrollments(Number(reportStudentId));
			const current = enrollments.find((e) => e.current);
			if (!current) {
				setError("This student has no current enrollment.");
				return;
			}
			const reportCard = await publishReportCard(Number(examId), current.id, Number(gradingScaleId));
			setPublishedReport(reportCard);
			setReportDialogOpen(false);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to publish report card");
		} finally {
			setSubmitting(false);
		}
	}

	if (!exam) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	return (
		<Stack spacing={3} sx={{ maxWidth: 900 }}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/examinations")}>
					Back to examinations
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				{exam.name} — {exam.examType}
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Schedules</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setScheduleDialogOpen(true)}>
							Add schedule
						</Button>
					</Box>

					{schedules.length === 0 && <Alert severity="info">No schedules yet.</Alert>}

					{schedules.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Class</TableCell>
										<TableCell>Subject</TableCell>
										<TableCell>Date</TableCell>
										<TableCell>Max marks</TableCell>
										<TableCell>Pass marks</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{schedules.map((schedule) => (
										<TableRow
											key={schedule.id}
											hover
											sx={{ cursor: "pointer" }}
											onClick={() => navigate(`/examinations/exams/${examId}/schedules/${schedule.id}`)}
										>
											<TableCell>{className(schedule.schoolClassId)}</TableCell>
											<TableCell>{subjectName(schedule.subjectId)}</TableCell>
											<TableCell>{schedule.examDate}</TableCell>
											<TableCell>{schedule.maxMarks}</TableCell>
											<TableCell>{schedule.passMarks}</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Report card</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setReportDialogOpen(true)}>
							Publish report card
						</Button>
					</Box>

					{publishedReport && (
						<Alert severity="success">
							Published: {publishedReport.totalMarksObtained}/{publishedReport.totalMaxMarks} (
							{publishedReport.percentage.toFixed(1)}%) — grade {publishedReport.overallGrade}
						</Alert>
					)}
				</Stack>
			</Paper>

			<Dialog open={scheduleDialogOpen} onClose={() => setScheduleDialogOpen(false)} component="form" onSubmit={handleAddSchedule} fullWidth maxWidth="xs">
				<DialogTitle>Add schedule</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Class" value={schoolClassId} onChange={(e) => setSchoolClassId(e.target.value)} required fullWidth>
							{classes.map((schoolClass) => (
								<MenuItem key={schoolClass.id} value={schoolClass.id}>
									{schoolClass.displayName ?? `Class #${schoolClass.id}`}
								</MenuItem>
							))}
						</TextField>
						<TextField select label="Subject" value={subjectId} onChange={(e) => setSubjectId(e.target.value)} required fullWidth>
							{subjects.map((subject) => (
								<MenuItem key={subject.id} value={subject.id}>
									{subject.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Exam date"
							type="date"
							value={examDate}
							onChange={(e) => setExamDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField label="Max marks" type="number" value={maxMarks} onChange={(e) => setMaxMarks(e.target.value)} required fullWidth />
							<TextField label="Pass marks" type="number" value={passMarks} onChange={(e) => setPassMarks(e.target.value)} required fullWidth />
						</Box>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setScheduleDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={reportDialogOpen} onClose={() => setReportDialogOpen(false)} component="form" onSubmit={handlePublishReportCard} fullWidth maxWidth="xs">
				<DialogTitle>Publish report card</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Student" value={reportStudentId} onChange={(e) => setReportStudentId(e.target.value)} required fullWidth>
							{students.map((student) => (
								<MenuItem key={student.id} value={student.id}>
									{studentLabel(student)}
								</MenuItem>
							))}
						</TextField>
						<TextField select label="Grading scale" value={gradingScaleId} onChange={(e) => setGradingScaleId(e.target.value)} required fullWidth>
							{gradingScales.map((scale) => (
								<MenuItem key={scale.id} value={scale.id}>
									{scale.name}
								</MenuItem>
							))}
						</TextField>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setReportDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !reportStudentId || !gradingScaleId}>
						Publish
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
