import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Avatar from "@mui/material/Avatar";
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
import { getPerson, updatePerson, updatePersonPhoto, type PersonResponse } from "../../api/persons";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { getStudent, updateStudent, type StudentResponse } from "../../api/students";
import { resolveAssetUrl, uploadAsset } from "../../api/assets";
import { StudentAttendanceSummaryPanel } from "./StudentAttendanceSummaryPanel";
import { StudentDocumentsPanel } from "./StudentDocumentsPanel";
import { StudentGuardiansPanel } from "./StudentGuardiansPanel";
import { StudentTransferPanel } from "./StudentTransferPanel";

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

interface EditStudentDialogProps {
	open: boolean;
	onClose: () => void;
	onSaved: () => void;
	student: StudentResponse;
	person: PersonResponse;
}

/** studentId/admissionNumber/admissionDate stay read-only here - see
 * UpdateStudentRequest's comment for why. Person + Student are separate entities/API
 * calls, but one form/one Save keeps this from reading like two unrelated edits. */
function EditStudentDialog({ open, onClose, onSaved, student, person }: EditStudentDialogProps) {
	const [firstName, setFirstName] = useState(person.firstName);
	const [lastName, setLastName] = useState(person.lastName ?? "");
	const [dateOfBirth, setDateOfBirth] = useState(person.dateOfBirth ?? "");
	const [gender, setGender] = useState(person.gender ?? "");
	const [phone, setPhone] = useState(person.phone ?? "");
	const [email, setEmail] = useState(person.email ?? "");
	const [address, setAddress] = useState(person.address ?? "");

	const [previousSchool, setPreviousSchool] = useState(student.previousSchool ?? "");
	const [bloodGroup, setBloodGroup] = useState(student.bloodGroup ?? "");
	const [category, setCategory] = useState(student.category ?? "");
	const [nationality, setNationality] = useState(student.nationality ?? "");
	const [remarks, setRemarks] = useState(student.remarks ?? "");
	const [medicalAlerts, setMedicalAlerts] = useState(student.medicalAlerts ?? "");
	const [emergencyContactName, setEmergencyContactName] = useState(student.emergencyContactName ?? "");
	const [emergencyContactPhone, setEmergencyContactPhone] = useState(student.emergencyContactPhone ?? "");

	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		if (!open) return;
		setFirstName(person.firstName);
		setLastName(person.lastName ?? "");
		setDateOfBirth(person.dateOfBirth ?? "");
		setGender(person.gender ?? "");
		setPhone(person.phone ?? "");
		setEmail(person.email ?? "");
		setAddress(person.address ?? "");
		setPreviousSchool(student.previousSchool ?? "");
		setBloodGroup(student.bloodGroup ?? "");
		setCategory(student.category ?? "");
		setNationality(student.nationality ?? "");
		setRemarks(student.remarks ?? "");
		setMedicalAlerts(student.medicalAlerts ?? "");
		setEmergencyContactName(student.emergencyContactName ?? "");
		setEmergencyContactPhone(student.emergencyContactPhone ?? "");
		setError(null);
	}, [open, person, student]);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setError(null);
		try {
			await Promise.all([
				updatePerson(person.id, {
					firstName,
					lastName,
					dateOfBirth: dateOfBirth || null,
					gender: gender || null,
					phone: phone || null,
					email: email || null,
					address: address || null,
				}),
				updateStudent(student.id, {
					admissionSource: student.admissionSource,
					previousSchool: previousSchool || null,
					tcNumber: student.tcNumber,
					entranceScore: student.entranceScore,
					bloodGroup: bloodGroup || null,
					category: category || null,
					nationality: nationality || null,
					remarks: remarks || null,
					medicalAlerts: medicalAlerts || null,
					emergencyContactName: emergencyContactName || null,
					emergencyContactPhone: emergencyContactPhone || null,
				}),
			]);
			onSaved();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save changes");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Dialog open={open} onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
			<DialogTitle>Edit student details</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<Typography variant="subtitle2">Person details</Typography>
					<Grid container spacing={2}>
						<Grid size={6}>
							<TextField label="First name" value={firstName} onChange={(e) => setFirstName(e.target.value)} required fullWidth />
						</Grid>
						<Grid size={6}>
							<TextField label="Last name" value={lastName} onChange={(e) => setLastName(e.target.value)} fullWidth />
						</Grid>
						<Grid size={6}>
							<TextField
								label="Date of birth"
								type="date"
								value={dateOfBirth}
								onChange={(e) => setDateOfBirth(e.target.value)}
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
						</Grid>
						<Grid size={6}>
							<TextField label="Gender" value={gender} onChange={(e) => setGender(e.target.value)} fullWidth />
						</Grid>
						<Grid size={6}>
							<TextField label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} fullWidth />
						</Grid>
						<Grid size={6}>
							<TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} fullWidth />
						</Grid>
						<Grid size={12}>
							<TextField label="Home address" value={address} onChange={(e) => setAddress(e.target.value)} fullWidth multiline rows={2} />
						</Grid>
					</Grid>

					<Typography variant="subtitle2">Admission details</Typography>
					<Grid container spacing={2}>
						<Grid size={6}>
							<TextField label="Previous school" value={previousSchool} onChange={(e) => setPreviousSchool(e.target.value)} fullWidth />
						</Grid>
						<Grid size={6}>
							<TextField label="Blood group" value={bloodGroup} onChange={(e) => setBloodGroup(e.target.value)} fullWidth />
						</Grid>
						<Grid size={6}>
							<TextField label="Category" value={category} onChange={(e) => setCategory(e.target.value)} fullWidth />
						</Grid>
						<Grid size={6}>
							<TextField label="Nationality" value={nationality} onChange={(e) => setNationality(e.target.value)} fullWidth />
						</Grid>
						<Grid size={12}>
							<TextField label="Remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} fullWidth multiline rows={2} />
						</Grid>
						<Grid size={12}>
							<TextField
								label="Medical alerts / allergies"
								value={medicalAlerts}
								onChange={(e) => setMedicalAlerts(e.target.value)}
								fullWidth
								multiline
								rows={2}
							/>
						</Grid>
						<Grid size={6}>
							<TextField
								label="Emergency contact name"
								value={emergencyContactName}
								onChange={(e) => setEmergencyContactName(e.target.value)}
								fullWidth
							/>
						</Grid>
						<Grid size={6}>
							<TextField
								label="Emergency contact phone"
								value={emergencyContactPhone}
								onChange={(e) => setEmergencyContactPhone(e.target.value)}
								fullWidth
							/>
						</Grid>
					</Grid>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" disabled={submitting || !firstName}>
					Save
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
	const [editDialogOpen, setEditDialogOpen] = useState(false);
	const [uploadingPhoto, setUploadingPhoto] = useState(false);

	async function handlePhotoChange(event: React.ChangeEvent<HTMLInputElement>) {
		const file = event.target.files?.[0];
		event.target.value = "";
		if (!file || !person) return;
		setUploadingPhoto(true);
		try {
			const asset = await uploadAsset(file);
			const updated = await updatePersonPhoto(person.id, asset.url);
			setPerson(updated);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to upload photo");
		} finally {
			setUploadingPhoto(false);
		}
	}

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

			{person && (
				<EditStudentDialog
					open={editDialogOpen}
					onClose={() => setEditDialogOpen(false)}
					student={student}
					person={person}
					onSaved={() => {
						setEditDialogOpen(false);
						refresh();
					}}
				/>
			)}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="subtitle1">Person details</Typography>
						<Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
							<Avatar src={person?.photoUrl ? resolveAssetUrl(person.photoUrl) : undefined} sx={{ width: 40, height: 40 }} />
							<Button component="label" size="small" disabled={uploadingPhoto}>
								{uploadingPhoto ? "Uploading..." : "Upload photo"}
								<input type="file" hidden accept="image/png,image/jpeg" onChange={handlePhotoChange} />
							</Button>
							{person && (
								<Button size="small" variant="outlined" onClick={() => setEditDialogOpen(true)}>
									Edit
								</Button>
							)}
						</Box>
					</Box>
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
						<Grid size={12}>
							<Field label="Home address" value={person?.address} />
						</Grid>
					</Grid>

					<Divider />
					<Typography variant="subtitle1">Admission details</Typography>
					<Grid container spacing={2}>
						<Grid size={4}>
							<Field label="Student ID" value={student.studentId} />
						</Grid>
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
						<Grid size={12}>
							<Field label="Medical alerts / allergies" value={student.medicalAlerts} />
						</Grid>
						<Grid size={4}>
							<Field label="Emergency contact name" value={student.emergencyContactName} />
						</Grid>
						<Grid size={4}>
							<Field label="Emergency contact phone" value={student.emergencyContactPhone} />
						</Grid>
					</Grid>
				</Stack>
			</Paper>

			{currentEnrollment && <StudentAttendanceSummaryPanel enrollmentId={currentEnrollment.id} />}

			<StudentGuardiansPanel studentId={student.id} />

			<StudentDocumentsPanel studentId={student.id} />

			<StudentTransferPanel studentId={student.id} />
		</Stack>
	);
}
