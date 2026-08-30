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
import { AddMemberFields, EMPTY_ADD_MEMBER_FORM, submitAddMember, type AddMemberFormState } from "../../components/AddMemberForm";
import { StaffInviteDialog } from "../../components/StaffInviteDialog";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { ApiError } from "../../api/client";
import { listDepartments, type DepartmentResponse } from "../../api/departments";
import { listDesignations, type DesignationResponse } from "../../api/designations";
import { listRoles, type RoleResponse } from "../../api/roles";
import { createStaffProfile, listStaffProfiles, type StaffProfileResponse } from "../../api/staffProfiles";
import { type StaffInviteResponse } from "../../api/users";

const EMPLOYMENT_TYPES = ["PERMANENT", "CONTRACT", "PART_TIME"];

interface HrExtension {
	designationId: string;
	employmentType: string;
	reportingManagerId: string;
}

const EMPTY_HR_EXTENSION: HrExtension = {
	designationId: "",
	employmentType: "PERMANENT",
	reportingManagerId: "",
};

/** Person + Membership(s) via the shared "Add member" fields (GitHub #104), plus an
 * optional StaffProfile extension for staff who need one (GitHub #104's "HR-specific
 * fields stay an optional extension section" call, since designation/employmentType have
 * no generic-member equivalent). Member ID/joining date entered once in the base section
 * double as StaffProfile.employeeCode/dateOfJoining when this extension is on, rather
 * than asking for them twice. */
export function StaffCreatePage() {
	const navigate = useNavigate();
	const [form, setForm] = useState<AddMemberFormState>(EMPTY_ADD_MEMBER_FORM);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [designations, setDesignations] = useState<DesignationResponse[]>([]);
	const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [existingStaff, setExistingStaff] = useState<StaffProfileResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [wantsStaffProfile, setWantsStaffProfile] = useState(true);
	const [hrExtension, setHrExtension] = useState<HrExtension>(EMPTY_HR_EXTENSION);
	const [issuedInvite, setIssuedInvite] = useState<StaffInviteResponse | null>(null);
	const [pendingNavigateTo, setPendingNavigateTo] = useState<string | null>(null);

	useEffect(() => {
		listCampuses().then(setCampuses).catch(() => {});
		listDesignations().then(setDesignations).catch(() => {});
		listDepartments().then(setDepartments).catch(() => {});
		listRoles().then(setRoles).catch(() => {});
		listStaffProfiles().then(setExistingStaff).catch(() => {});
	}, []);

	function setHrField<K extends keyof HrExtension>(key: K) {
		return (event: React.ChangeEvent<HTMLInputElement>) => setHrExtension((prev) => ({ ...prev, [key]: event.target.value }));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSubmitting(true);
		try {
			if (wantsStaffProfile && (!form.memberId || !form.joiningDate || !hrExtension.designationId)) {
				setError("Employee code, joining date, and designation are required to create an HR staff profile");
				setSubmitting(false);
				return;
			}
			if (!wantsStaffProfile && form.roleIds.length === 0) {
				setError("Select at least one role, or create an HR staff profile");
				setSubmitting(false);
				return;
			}

			const { person, invite } = await submitAddMember(form);

			let landingPath = invite ? `/settings/users/${invite.userId}` : "/hr";
			if (wantsStaffProfile) {
				const staffProfile = await createStaffProfile({
					personId: person.id,
					campusId: form.campusId === "" ? null : form.campusId,
					employeeCode: form.memberId,
					designationId: Number(hrExtension.designationId),
					departmentId: form.departmentId === "" ? null : form.departmentId,
					dateOfJoining: form.joiningDate,
					employmentType: hrExtension.employmentType,
					reportingManagerId: hrExtension.reportingManagerId === "" ? null : Number(hrExtension.reportingManagerId),
				});
				landingPath = `/hr/staff/${staffProfile.id}`;
			}

			if (invite) {
				// Navigation is deferred until the invite dialog is closed - the claim token
				// is shown once and never stored server-side, so an immediate redirect
				// would lose it.
				setPendingNavigateTo(landingPath);
				setIssuedInvite(invite);
			} else {
				navigate(landingPath, { replace: true });
			}
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add staff member");
		} finally {
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
					<Typography variant="subtitle1">Member details</Typography>
					<AddMemberFields
						value={form}
						onChange={setForm}
						campuses={campuses}
						departments={departments}
						roles={roles}
						rolesRequired={false}
					/>

					<Divider />
					<FormControlLabel
						control={<Checkbox checked={wantsStaffProfile} onChange={(e) => setWantsStaffProfile(e.target.checked)} />}
						label="Also create an HR staff profile for this person"
					/>
					{wantsStaffProfile && (
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField
								select
								label="Designation"
								value={hrExtension.designationId}
								onChange={setHrField("designationId")}
								required
								fullWidth
							>
								{designations.map((designation) => (
									<MenuItem key={designation.id} value={designation.id}>
										{designation.name}
									</MenuItem>
								))}
							</TextField>
							<TextField
								select
								label="Employment type"
								value={hrExtension.employmentType}
								onChange={setHrField("employmentType")}
								required
								fullWidth
							>
								{EMPLOYMENT_TYPES.map((type) => (
									<MenuItem key={type} value={type}>
										{type}
									</MenuItem>
								))}
							</TextField>
							<TextField
								select
								label="Reporting manager (optional)"
								value={hrExtension.reportingManagerId}
								onChange={setHrField("reportingManagerId")}
								fullWidth
							>
								<MenuItem value="">No reporting manager</MenuItem>
								{existingStaff.map((staff) => (
									<MenuItem key={staff.id} value={staff.id}>
										{staff.employeeCode} — {staff.designationName}
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
