import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { listAcademicYears, type AcademicYearResponse } from "../../api/academicYears";
import { ApiError } from "../../api/client";
import {
	createEnrollment,
	listStudentEnrollments,
	type StudentEnrollmentResponse,
} from "../../api/enrollments";
import { getPerson, type PersonResponse } from "../../api/persons";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { getStudent, type StudentResponse } from "../../api/students";
import { StudentGuardiansPanel } from "./StudentGuardiansPanel";

function Field({ label, value }: { label: string; value: string | number | null | undefined }) {
	return (
		<Box>
			<Typography variant="caption" color="text.secondary">
				{label}
			</Typography>
			<Typography variant="body1">{value ?? "—"}</Typography>
		</Box>
	);
}

interface EnrollDialogProps {
	open: boolean;
	onClose: () => void;
	onEnrolled: () => void;
	studentId: number;
}

/** Student.status only moves ADMITTED -> ACTIVE via enrollment (see Student.java) - until
 * now, that transition had no UI at all (enrollStudent existed only as a raw API call the
 * e2e suite's own seeding used - see e2e/api/organisations.ts). This is the minimal form:
 * academic year -> class -> section, the same drill-down FeesPage/AttendancePage already
 * use for scoping to a section. */
function EnrollDialog({ open, onClose, onEnrolled, studentId }: EnrollDialogProps) {
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [schoolClasses, setSchoolClasses] = useState<SchoolClassResponse[]>([]);
	const [sections, setSections] = useState<SectionResponse[]>([]);
	const [academicYearId, setAcademicYearId] = useState("");
	const [schoolClassId, setSchoolClassId] = useState("");
	const [sectionId, setSectionId] = useState("");
	const [rollNumber, setRollNumber] = useState("");
	const [enrolledDate, setEnrolledDate] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		if (!open) return;
		listAcademicYears().then(setAcademicYears).catch(() => {});
	}, [open]);

	useEffect(() => {
		setSchoolClassId("");
		setSchoolClasses([]);
		if (!academicYearId) return;
		listSchoolClasses(Number(academicYearId)).then(setSchoolClasses).catch(() => {});
	}, [academicYearId]);

	useEffect(() => {
		setSectionId("");
		setSections([]);
		if (!schoolClassId) return;
		listSections(Number(schoolClassId)).then(setSections).catch(() => {});
	}, [schoolClassId]);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setError(null);
		try {
			await createEnrollment(studentId, {
				academicYearId: Number(academicYearId),
				sectionId: Number(sectionId),
				rollNumber: rollNumber ? Number(rollNumber) : null,
				enrolledDate,
			});
			onEnrolled();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to enroll student");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Dialog open={open} onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
			<DialogTitle>Enroll student</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField select label="Academic year" value={academicYearId} onChange={(e) => setAcademicYearId(e.target.value)} required fullWidth>
						{academicYears.map((year) => (
							<MenuItem key={year.id} value={year.id}>
								{year.name}
							</MenuItem>
						))}
					</TextField>
					<TextField
						select
						label="Class"
						value={schoolClassId}
						onChange={(e) => setSchoolClassId(e.target.value)}
						required
						fullWidth
						disabled={!academicYearId}
					>
						{schoolClasses.map((schoolClass) => (
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
						required
						fullWidth
						disabled={!schoolClassId}
					>
						{sections.map((section) => (
							<MenuItem key={section.id} value={section.id}>
								{section.name}
							</MenuItem>
						))}
					</TextField>
					<TextField label="Roll number" type="number" value={rollNumber} onChange={(e) => setRollNumber(e.target.value)} fullWidth />
					<TextField
						label="Enrolled date"
						type="date"
						value={enrolledDate}
						onChange={(e) => setEnrolledDate(e.target.value)}
						required
						slotProps={{ inputLabel: { shrink: true } }}
						fullWidth
					/>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" disabled={submitting || !sectionId}>
					Enroll
				</Button>
			</DialogActions>
		</Dialog>
	);
}

export function StudentDetailPage() {
	const { studentId } = useParams<{ studentId: string }>();
	const navigate = useNavigate();
	const [student, setStudent] = useState<StudentResponse | null>(null);
	const [person, setPerson] = useState<PersonResponse | null>(null);
	const [enrollments, setEnrollments] = useState<StudentEnrollmentResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [enrollDialogOpen, setEnrollDialogOpen] = useState(false);

	function refresh() {
		if (!studentId) return;
		getStudent(Number(studentId))
			.then((studentResponse) => {
				setStudent(studentResponse);
				return getPerson(studentResponse.personId);
			})
			.then((personResponse) => {
				if (!personResponse) return;
				setPerson(personResponse);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load student"));
		listStudentEnrollments(Number(studentId)).then(setEnrollments).catch(() => {});
	}

	useEffect(refresh, [studentId]);

	if (error) {
		return <Alert severity="error">{error}</Alert>;
	}

	if (!student) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	const currentEnrollment = enrollments.find((enrollment) => enrollment.current) ?? null;

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/students")}>
					Back to students
				</Button>
			</Box>

			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
				<Typography variant="h4" component="h1">
					{person ? `${person.firstName} ${person.lastName}` : `Student #${student.id}`}
				</Typography>
				<Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
					<Chip label={student.status} />
					{student.status === "ADMITTED" && (
						<Button size="small" variant="contained" onClick={() => setEnrollDialogOpen(true)}>
							Enroll student
						</Button>
					)}
				</Stack>
			</Box>

			{currentEnrollment && (
				<Alert severity="success" variant="outlined">
					Enrolled - roll number {currentEnrollment.rollNumber ?? "—"}, since {currentEnrollment.enrolledDate}
				</Alert>
			)}

			<EnrollDialog
				open={enrollDialogOpen}
				onClose={() => setEnrollDialogOpen(false)}
				studentId={student.id}
				onEnrolled={() => {
					setEnrollDialogOpen(false);
					refresh();
				}}
			/>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="subtitle1">Person details</Typography>
					<Grid container spacing={2}>
						<Grid size={4}>
							<Field label="Date of birth" value={person?.dateOfBirth} />
						</Grid>
						<Grid size={4}>
							<Field label="Gender" value={person?.gender} />
						</Grid>
						<Grid size={4}>
							<Field label="Phone" value={person?.phone} />
						</Grid>
						<Grid size={4}>
							<Field label="Email" value={person?.email} />
						</Grid>
					</Grid>

					<Divider />
					<Typography variant="subtitle1">Admission details</Typography>
					<Grid container spacing={2}>
						<Grid size={4}>
							<Field label="Admission number" value={student.admissionNumber} />
						</Grid>
						<Grid size={4}>
							<Field label="Admission date" value={student.admissionDate} />
						</Grid>
						<Grid size={4}>
							<Field label="Previous school" value={student.previousSchool} />
						</Grid>
						<Grid size={4}>
							<Field label="Blood group" value={student.bloodGroup} />
						</Grid>
						<Grid size={4}>
							<Field label="Category" value={student.category} />
						</Grid>
						<Grid size={4}>
							<Field label="Nationality" value={student.nationality} />
						</Grid>
						<Grid size={12}>
							<Field label="Remarks" value={student.remarks} />
						</Grid>
					</Grid>
				</Stack>
			</Paper>

			<StudentGuardiansPanel studentId={student.id} />
		</Stack>
	);
}
