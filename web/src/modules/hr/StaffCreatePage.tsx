import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Divider from "@mui/material/Divider";
import FormControlLabel from "@mui/material/FormControlLabel";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { StaffInviteDialog } from "../../components/StaffInviteDialog";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { ApiError } from "../../api/client";
import { listDepartments, type DepartmentResponse } from "../../api/departments";
import { listDesignations, type DesignationResponse } from "../../api/designations";
import { grantMembership } from "../../api/memberships";
import { createPerson } from "../../api/persons";
import { listRoles, type RoleResponse } from "../../api/roles";
import { createStaffProfile } from "../../api/staffProfiles";
import { inviteUser, type StaffInviteResponse } from "../../api/users";

const EMPLOYMENT_TYPES = ["PERMANENT", "CONTRACT", "PART_TIME"];

interface FormState {
	firstName: string;
	lastName: string;
	phone: string;
	email: string;
	campusId: string;
	employeeCode: string;
	designationId: string;
	departmentId: string;
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
	designationId: "",
	departmentId: "",
	dateOfJoining: "",
	employmentType: "PERMANENT",
};

/** Person + StaffProfile are two backend entities, same identity-vs-role split as Student -
 * this form creates both in sequence, same as StudentCreatePage. Designation/department are
 * catalogs managed in Settings (DepartmentsPanel/DesignationsPanel), not free text - see #94. */
export function StaffCreatePage() {
	const navigate = useNavigate();
	const [form, setForm] = useState<FormState>(EMPTY_FORM);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [designations, setDesignations] = useState<DesignationResponse[]>([]);
	const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [wantsLogin, setWantsLogin] = useState(false);
	const [loginRoleId, setLoginRoleId] = useState("");
	const [loginCampusId, setLoginCampusId] = useState("");
	const [loginDepartmentId, setLoginDepartmentId] = useState("");
	const [issuedInvite, setIssuedInvite] = useState<StaffInviteResponse | null>(null);
	const [pendingNavigateTo, setPendingNavigateTo] = useState<string | null>(null);

	useEffect(() => {
		listCampuses().then(setCampuses).catch(() => {});
		listDesignations().then(setDesignations).catch(() => {});
		listDepartments().then(setDepartments).catch(() => {});
		listRoles().then(setRoles).catch(() => {});
	}, []);

	function toggleWantsLogin(checked: boolean) {
		setWantsLogin(checked);
		if (checked) {
			setLoginCampusId(form.campusId);
			setLoginDepartmentId(form.departmentId);
		}
	}

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
				designationId: Number(form.designationId),
				departmentId: form.departmentId ? Number(form.departmentId) : null,
				dateOfJoining: form.dateOfJoining,
				employmentType: form.employmentType,
			});

			if (wantsLogin && loginRoleId) {
				const invite = await inviteUser({ email: person.email ?? "", phone: person.phone });
				await grantMembership({
					userId: invite.userId,
					personId: person.id,
					roleId: Number(loginRoleId),
					campusId: loginCampusId ? Number(loginCampusId) : null,
					departmentId: loginDepartmentId ? Number(loginDepartmentId) : null,
				});
				// Navigation is deferred until the invite dialog is closed - the claim token
				// is shown once and never stored server-side, so an immediate redirect would
				// lose it.
				setPendingNavigateTo(`/hr/staff/${staffProfile.id}`);
				setIssuedInvite(invite);
				setSubmitting(false);
				return;
			}

			navigate(`/hr/staff/${staffProfile.id}`, { replace: true });
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add staff member");
			setSubmitting(false);
		}
	}

	function closeInviteDialog() {
		setIssuedInvite(null);
		if (pendingNavigateTo) {
			navigate(pendingNavigateTo, { replace: true });
		}
	}

	return (
		<Stack spacing={2}>
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
						<TextField select label="Designation" value={form.designationId} onChange={set("designationId")} required fullWidth>
							{designations.map((designation) => (
								<MenuItem key={designation.id} value={designation.id}>
									{designation.name}
								</MenuItem>
							))}
						</TextField>
						<TextField select label="Department" value={form.departmentId} onChange={set("departmentId")} fullWidth>
							<MenuItem value="">No department</MenuItem>
							{departments.map((department) => (
								<MenuItem key={department.id} value={department.id}>
									{department.name}
								</MenuItem>
							))}
						</TextField>
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

					<Divider />
					<FormControlLabel
						control={<Checkbox checked={wantsLogin} onChange={(e) => toggleWantsLogin(e.target.checked)} />}
						label="Also create a login for this person"
					/>
					{wantsLogin && (
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField
								select
								label="Role"
								value={loginRoleId}
								onChange={(e) => setLoginRoleId(e.target.value)}
								required={wantsLogin}
								fullWidth
							>
								{roles
									.filter((role) => role.status === "ACTIVE")
									.map((role) => (
										<MenuItem key={role.id} value={role.id}>
											{role.name}
										</MenuItem>
									))}
							</TextField>
							<TextField
								select
								label="Campus (optional — org-wide if left blank)"
								value={loginCampusId}
								onChange={(e) => setLoginCampusId(e.target.value)}
								fullWidth
							>
								<MenuItem value="">Org-wide</MenuItem>
								{campuses.map((campus) => (
									<MenuItem key={campus.id} value={campus.id}>
										{campus.name}
									</MenuItem>
								))}
							</TextField>
							<TextField
								select
								label="Department (optional)"
								value={loginDepartmentId}
								onChange={(e) => setLoginDepartmentId(e.target.value)}
								fullWidth
							>
								<MenuItem value="">No department</MenuItem>
								{departments.map((department) => (
									<MenuItem key={department.id} value={department.id}>
										{department.name}
									</MenuItem>
								))}
							</TextField>
						</Box>
					)}

					<Box sx={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
						<Button onClick={() => navigate("/hr")}>Cancel</Button>
						<Button type="submit" variant="contained" disabled={submitting}>
							{submitting ? "Adding..." : "Add staff member"}
						</Button>
					</Box>
				</Stack>
			</Paper>

			<StaffInviteDialog invite={issuedInvite} onClose={closeInviteDialog} />
		</Stack>
	);
}
