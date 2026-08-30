import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Divider from "@mui/material/Divider";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { getAcademicSetupStatus } from "../../api/academicSetupStatus";
import { listAcademicYears, type AcademicYearResponse } from "../../api/academicYears";
import { ApiError } from "../../api/client";
import { createEnrollment } from "../../api/enrollments";
import { createOrGetGuardian } from "../../api/guardians";
import { createPerson } from "../../api/persons";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { admitStudent } from "../../api/students";
import { GUARDIAN_RELATIONSHIP_TYPES, linkGuardian, type GuardianRelationshipType } from "../../api/studentGuardians";

const ACADEMIC_SETUP_REQUIRED_MESSAGE = "Complete Academic Setup before adding a student.";

interface FormState {
	firstName: string;
	lastName: string;
	dateOfBirth: string;
	gender: string;
	phone: string;
	email: string;
	address: string;
	admissionNumber: string;
	admissionDate: string;
	previousSchool: string;
	bloodGroup: string;
	category: string;
	nationality: string;
	remarks: string;
	medicalAlerts: string;
	emergencyContactName: string;
	emergencyContactPhone: string;
	academicYearId: string;
	schoolClassId: string;
	sectionId: string;
	rollNumber: string;
	guardianFirstName: string;
	guardianLastName: string;
	guardianPhone: string;
	guardianRelationshipType: string;
}

const EMPTY_FORM: FormState = {
	firstName: "",
	lastName: "",
	dateOfBirth: "",
	gender: "",
	phone: "",
	email: "",
	address: "",
	admissionNumber: "",
	admissionDate: "",
	previousSchool: "",
	bloodGroup: "",
	category: "",
	nationality: "",
	remarks: "",
	medicalAlerts: "",
	emergencyContactName: "",
	emergencyContactPhone: "",
	academicYearId: "",
	schoolClassId: "",
	sectionId: "",
	rollNumber: "",
	guardianFirstName: "",
	guardianLastName: "",
	guardianPhone: "",
	guardianRelationshipType: "FATHER",
};

/** Person + Student are two backend entities (identity ≠ enrollment, per the project's
 * identity model) - this form creates both in sequence, same as a real admissions desk
 * flow would: register the person, then admit them as a student. */
export function StudentCreatePage() {
	const navigate = useNavigate();
	const [form, setForm] = useState<FormState>(EMPTY_FORM);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);
	const [checkingPrerequisite, setCheckingPrerequisite] = useState(true);
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [schoolClasses, setSchoolClasses] = useState<SchoolClassResponse[]>([]);
	const [sections, setSections] = useState<SectionResponse[]>([]);

	useEffect(() => {
		getAcademicSetupStatus()
			.then((status) => {
				if (!status.configured) {
					navigate("/academics/setup", { replace: true, state: { blockedMessage: ACADEMIC_SETUP_REQUIRED_MESSAGE } });
					return;
				}
				setCheckingPrerequisite(false);
			})
			// Fail open on a check-status error - don't block admission on the prerequisite
			// check itself being unreachable, the admit call will fail on its own merits.
			.catch(() => setCheckingPrerequisite(false));
	}, [navigate]);

	useEffect(() => {
		listAcademicYears().then(setAcademicYears).catch(() => {});
	}, []);

	useEffect(() => {
		setForm((prev) => ({ ...prev, schoolClassId: "", sectionId: "" }));
		setSchoolClasses([]);
		if (!form.academicYearId) return;
		listSchoolClasses(Number(form.academicYearId)).then(setSchoolClasses).catch(() => {});
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [form.academicYearId]);

	useEffect(() => {
		setForm((prev) => ({ ...prev, sectionId: "" }));
		setSections([]);
		if (!form.schoolClassId) return;
		listSections(Number(form.schoolClassId)).then(setSections).catch(() => {});
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [form.schoolClassId]);

	function set<K extends keyof FormState>(key: K) {
		return (event: React.ChangeEvent<HTMLInputElement>) => setForm((prev) => ({ ...prev, [key]: event.target.value }));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSubmitting(true);
		try {
			const person = await createPerson({
				firstName: form.firstName,
				lastName: form.lastName,
				dateOfBirth: form.dateOfBirth || null,
				gender: form.gender || null,
				phone: form.phone || null,
				email: form.email || null,
				address: form.address || null,
			});
			const student = await admitStudent({
				personId: person.id,
				admissionNumber: form.admissionNumber || null,
				admissionDate: form.admissionDate,
				previousSchool: form.previousSchool || null,
				bloodGroup: form.bloodGroup || null,
				category: form.category || null,
				nationality: form.nationality || null,
				remarks: form.remarks || null,
				medicalAlerts: form.medicalAlerts || null,
				emergencyContactName: form.emergencyContactName || null,
				emergencyContactPhone: form.emergencyContactPhone || null,
			});
			await createEnrollment(student.id, {
				academicYearId: Number(form.academicYearId),
				sectionId: Number(form.sectionId),
				rollNumber: form.rollNumber ? Number(form.rollNumber) : null,
				enrolledDate: form.admissionDate,
			});
			if (form.guardianFirstName && form.guardianLastName) {
				const guardianPerson = await createPerson({
					firstName: form.guardianFirstName,
					lastName: form.guardianLastName,
					phone: form.guardianPhone || null,
				});
				const guardian = await createOrGetGuardian({ personId: guardianPerson.id });
				await linkGuardian(student.id, {
					guardianId: guardian.id,
					relationshipType: form.guardianRelationshipType as GuardianRelationshipType,
					primaryGuardian: true,
					emergencyContact: false,
					canPickup: true,
					canReceiveCommunication: true,
					contactPriority: 1,
				});
			}
			navigate(`/students/${student.id}`, { replace: true });
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to admit student");
			setSubmitting(false);
		}
	}

	if (checkingPrerequisite) {
		return <CircularProgress size={28} />;
	}

	return (
		<Stack spacing={2}>
			<Typography variant="h4" component="h1">
				Admit student
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="subtitle1">Person details</Typography>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField label="First name" value={form.firstName} onChange={set("firstName")} required fullWidth />
						<TextField label="Last name" value={form.lastName} onChange={set("lastName")} required fullWidth />
					</Box>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField
							label="Date of birth"
							type="date"
							value={form.dateOfBirth}
							onChange={set("dateOfBirth")}
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField label="Gender" value={form.gender} onChange={set("gender")} fullWidth />
					</Box>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField label="Phone" value={form.phone} onChange={set("phone")} fullWidth />
						<TextField label="Email" type="email" value={form.email} onChange={set("email")} fullWidth />
					</Box>
					<TextField label="Home address" value={form.address} onChange={set("address")} multiline rows={2} fullWidth />

					<Divider />
					<Typography variant="subtitle1">Primary guardian</Typography>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField label="Guardian first name" value={form.guardianFirstName} onChange={set("guardianFirstName")} fullWidth />
						<TextField label="Guardian last name" value={form.guardianLastName} onChange={set("guardianLastName")} fullWidth />
					</Box>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField label="Guardian mobile" value={form.guardianPhone} onChange={set("guardianPhone")} fullWidth />
						<TextField
							select
							label="Relationship"
							value={form.guardianRelationshipType}
							onChange={set("guardianRelationshipType")}
							fullWidth
						>
							{GUARDIAN_RELATIONSHIP_TYPES.map((type) => (
								<MenuItem key={type} value={type}>
									{type.replaceAll("_", " ")}
								</MenuItem>
							))}
						</TextField>
					</Box>

					<Divider />
					<Typography variant="subtitle1">Admission details</Typography>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField
							label="Admission number"
							helperText="Leave blank to auto-generate from the organisation's numbering format"
							value={form.admissionNumber}
							onChange={set("admissionNumber")}
							fullWidth
						/>
						<TextField
							label="Admission date"
							type="date"
							value={form.admissionDate}
							onChange={set("admissionDate")}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Box>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField select label="Academic year" value={form.academicYearId} onChange={set("academicYearId")} required fullWidth>
							{academicYears.map((year) => (
								<MenuItem key={year.id} value={year.id}>
									{year.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Class"
							value={form.schoolClassId}
							onChange={set("schoolClassId")}
							required
							fullWidth
							disabled={!form.academicYearId}
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
							value={form.sectionId}
							onChange={set("sectionId")}
							required
							fullWidth
							disabled={!form.schoolClassId}
						>
							{sections.map((section) => (
								<MenuItem key={section.id} value={section.id}>
									{section.name}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Roll number" type="number" value={form.rollNumber} onChange={set("rollNumber")} fullWidth />
					</Box>
					<TextField label="Previous school" value={form.previousSchool} onChange={set("previousSchool")} fullWidth />
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField label="Blood group" value={form.bloodGroup} onChange={set("bloodGroup")} fullWidth />
						<TextField label="Category" value={form.category} onChange={set("category")} fullWidth />
						<TextField label="Nationality" value={form.nationality} onChange={set("nationality")} fullWidth />
					</Box>
					<TextField label="Remarks" value={form.remarks} onChange={set("remarks")} multiline rows={2} fullWidth />
					<TextField
						label="Medical alerts / allergies"
						value={form.medicalAlerts}
						onChange={set("medicalAlerts")}
						multiline
						rows={2}
						fullWidth
					/>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField
							label="Emergency contact name"
							helperText="A contact who isn't necessarily one of the student's guardians"
							value={form.emergencyContactName}
							onChange={set("emergencyContactName")}
							fullWidth
						/>
						<TextField
							label="Emergency contact phone"
							value={form.emergencyContactPhone}
							onChange={set("emergencyContactPhone")}
							fullWidth
						/>
					</Box>

					<Box sx={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
						<Button onClick={() => navigate("/students")}>Cancel</Button>
						<Button type="submit" variant="contained" disabled={submitting}>
							{submitting ? "Admitting..." : "Admit student"}
						</Button>
					</Box>
				</Stack>
			</Paper>
		</Stack>
	);
}
