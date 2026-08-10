import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { ApiError } from "../../api/client";
import { createPerson } from "../../api/persons";
import { createStaffProfile } from "../../api/staffProfiles";

const EMPLOYMENT_TYPES = ["PERMANENT", "CONTRACT", "PART_TIME"];

interface FormState {
	firstName: string;
	lastName: string;
	phone: string;
	email: string;
	campusId: string;
	employeeCode: string;
	designation: string;
	department: string;
	dateOfJoining: string;
	employmentType: string;
}

const EMPTY_FORM: FormState = {
	firstName: "",
	lastName: "",
	phone: "",
	email: "",
	campusId: "",
	employeeCode: "",
	designation: "",
	department: "",
	dateOfJoining: "",
	employmentType: "PERMANENT",
};

/** Person + StaffProfile are two backend entities, same identity-vs-role split as Student -
 * this form creates both in sequence, same as StudentCreatePage. */
export function StaffCreatePage() {
	const navigate = useNavigate();
	const [form, setForm] = useState<FormState>(EMPTY_FORM);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		listCampuses().then(setCampuses).catch(() => {});
	}, []);

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
				phone: form.phone || null,
				email: form.email || null,
			});
			const staffProfile = await createStaffProfile({
				personId: person.id,
				campusId: form.campusId ? Number(form.campusId) : null,
				employeeCode: form.employeeCode,
				designation: form.designation,
				department: form.department || null,
				dateOfJoining: form.dateOfJoining,
				employmentType: form.employmentType,
			});
			navigate(`/hr/staff/${staffProfile.id}`, { replace: true });
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add staff member");
			setSubmitting(false);
		}
	}

	return (
		<Stack spacing={2} sx={{ maxWidth: 640 }}>
			<Typography variant="h4" component="h1">
				Add staff member
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
						<TextField label="Phone" value={form.phone} onChange={set("phone")} fullWidth />
						<TextField label="Email" type="email" value={form.email} onChange={set("email")} fullWidth />
					</Box>

					<Divider />
					<Typography variant="subtitle1">Employment details</Typography>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField select label="Campus" value={form.campusId} onChange={set("campusId")} sx={{ minWidth: 200 }} fullWidth>
							<MenuItem value="">Org-wide (no campus)</MenuItem>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Employee code" value={form.employeeCode} onChange={set("employeeCode")} required fullWidth />
					</Box>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField label="Designation" value={form.designation} onChange={set("designation")} required fullWidth />
						<TextField label="Department" value={form.department} onChange={set("department")} fullWidth />
					</Box>
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField
							label="Date of joining"
							type="date"
							value={form.dateOfJoining}
							onChange={set("dateOfJoining")}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField select label="Employment type" value={form.employmentType} onChange={set("employmentType")} required fullWidth>
							{EMPLOYMENT_TYPES.map((type) => (
								<MenuItem key={type} value={type}>
									{type}
								</MenuItem>
							))}
						</TextField>
					</Box>

					<Box sx={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
						<Button onClick={() => navigate("/hr")}>Cancel</Button>
						<Button type="submit" variant="contained" disabled={submitting}>
							{submitting ? "Adding..." : "Add staff member"}
						</Button>
					</Box>
				</Stack>
			</Paper>
		</Stack>
	);
}
