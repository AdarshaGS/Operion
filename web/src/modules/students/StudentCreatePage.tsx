import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Divider from "@mui/material/Divider";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { getAcademicSetupStatus } from "../../api/academicSetupStatus";
import { ApiError } from "../../api/client";
import { createPerson } from "../../api/persons";
import { admitStudent } from "../../api/students";
import { useAuth } from "../../auth/AuthContext";

const ACADEMIC_SETUP_REQUIRED_MESSAGE = "Complete Academic Setup before adding a student.";

interface FormState {
	firstName: string;
	lastName: string;
	dateOfBirth: string;
	gender: string;
	phone: string;
	email: string;
	admissionNumber: string;
	admissionDate: string;
	previousSchool: string;
	bloodGroup: string;
	category: string;
	nationality: string;
	remarks: string;
	medicalAlerts: string;
}

const EMPTY_FORM: FormState = {
	firstName: "",
	lastName: "",
	dateOfBirth: "",
	gender: "",
	phone: "",
	email: "",
	admissionNumber: "",
	admissionDate: "",
	previousSchool: "",
	bloodGroup: "",
	category: "",
	nationality: "",
	remarks: "",
	medicalAlerts: "",
};

/** Person + Student are two backend entities (identity ≠ enrollment, per the project's
 * identity model) - this form creates both in sequence, same as a real admissions desk
 * flow would: register the person, then admit them as a student. */
export function StudentCreatePage() {
	const navigate = useNavigate();
	const { hasPermission } = useAuth();
	const canViewSensitive = hasPermission("STUDENT_SENSITIVE_VIEW");
	const [form, setForm] = useState<FormState>(EMPTY_FORM);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);
	const [checkingPrerequisite, setCheckingPrerequisite] = useState(true);

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
			});
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
					<TextField label="Previous school" value={form.previousSchool} onChange={set("previousSchool")} fullWidth />
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField label="Blood group" value={form.bloodGroup} onChange={set("bloodGroup")} fullWidth />
						<TextField label="Category" value={form.category} onChange={set("category")} fullWidth />
						<TextField label="Nationality" value={form.nationality} onChange={set("nationality")} fullWidth />
					</Box>
					<TextField label="Remarks" value={form.remarks} onChange={set("remarks")} multiline rows={2} fullWidth />
					{canViewSensitive && (
						<TextField
							label="Medical alerts / allergies"
							helperText="Only visible to roles granted STUDENT_SENSITIVE_VIEW"
							value={form.medicalAlerts}
							onChange={set("medicalAlerts")}
							multiline
							rows={2}
							fullWidth
						/>
					)}

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
