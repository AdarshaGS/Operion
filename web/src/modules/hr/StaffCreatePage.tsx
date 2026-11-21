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
import Tooltip from "@mui/material/Tooltip";
import { Can } from "../../auth/Can";
import { useAuth } from "../../auth/AuthContext";
import { useCan } from "../../auth/useCan";
import {
	EMPTY_ADD_MEMBER_FORM,
	MemberLoginFields,
	MemberPersonFields,
	MemberPlacementFields,
	MemberRoleField,
	submitAddMember,
	type AddMemberFormState,
} from "../../components/AddMemberForm";
import { StaffInviteDialog } from "../../components/StaffInviteDialog";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import type { StaffInviteResponse } from "../../api/users";
import { ApiError } from "../../api/client";
import type { PersonResponse } from "../../api/persons";
import { listDepartments, type DepartmentResponse } from "../../api/departments";
import { listDesignations, type DesignationResponse } from "../../api/designations";
import { listRoles, type RoleResponse } from "../../api/roles";
import { createStaffProfile, listStaffProfiles, type StaffProfileResponse } from "../../api/staffProfiles";

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
	// Creating the StaffProfile is POST /hr/staff (HR_STAFF_MANAGE); the person/user steps before it carry their own codes.
	const canManageStaff = useCan("HR_STAFF_MANAGE");
	const { hasPermission } = useAuth();

	const [wantsStaffProfile, setWantsStaffProfile] = useState(true);
	// Optional: invite this person to sign in (a login + membership) in the same step as the staff profile. Only reachable
	// through the <Can> block below, so it can never be true for someone without the membership-granting permission.
	const [givesAccess, setGivesAccess] = useState(false);
	const [accessRoleId, setAccessRoleId] = useState<number | "">("");
	const [issuedInvite, setIssuedInvite] = useState<StaffInviteResponse | null>(null);
	const [landingPath, setLandingPath] = useState("/hr");
	const inviting = wantsStaffProfile && givesAccess;
	const [hrExtension, setHrExtension] = useState<HrExtension>(EMPTY_HR_EXTENSION);
	// The Person created on a previous submit that then failed at the staff-profile/login step. A retry reuses it instead of
	// creating a second Person; `key` is the person fields it was created from, so editing any of them starts a fresh Person.
	const [createdPerson, setCreatedPerson] = useState<{ person: PersonResponse; key: string } | null>(null);
	// The backend refuses a role whose permissions the actor does not hold themselves, so only offer roles they can delegate.
	const delegableRoles = roles.filter((role) => role.status === "ACTIVE" && role.permissionCodes.every((code) => hasPermission(code)));

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
				setError("Staff ID, joining date, and designation are required to create an HR staff profile");
				setSubmitting(false);
				return;
			}
			if (inviting && (!form.email || accessRoleId === "")) {
				setError("An email address and a role are required to give login access");
				setSubmitting(false);
				return;
			}
			if (!wantsStaffProfile && form.roleIds.length === 0) {
				setError("Select at least one role, or create an HR staff profile");
				setSubmitting(false);
				return;
			}

			// When inviting, the login is created by the staff-profile call below (one transaction), so no password login or
			// role grants are made here.
			const personKey = JSON.stringify([form.firstName, form.lastName, form.gender, form.email, form.phone, form.address]);
			let person: PersonResponse;
			let user: Awaited<ReturnType<typeof submitAddMember>>["user"] = null;
			if (inviting && createdPerson !== null && createdPerson.key === personKey) {
				person = createdPerson.person;
			} else {
				({ person, user } = await submitAddMember(inviting ? { ...form, roleIds: [] } : form));
				if (inviting) {
					setCreatedPerson({ person, key: personKey });
				}
			}

			let landingPath = user ? `/settings/members/${user.id}` : "/hr";
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
					access: inviting ? { email: form.email, roleId: Number(accessRoleId), phone: form.phone || null } : null,
				});
				landingPath = `/hr/staff/${staffProfile.id}`;
				if (staffProfile.invite) {
					// Show the claim link before leaving - the token is never shown again.
					setLandingPath(landingPath);
					setIssuedInvite(staffProfile.invite);
					return;
				}
			}

			navigate(landingPath, { replace: true });
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add staff");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Stack spacing={2}>
			<Typography variant="h4" component="h1">
				Add staff
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="subtitle1">Staff details</Typography>
					{/* AddMemberFields' parts, so the password and role fields can step aside while an invite is chosen below. */}
					<MemberPersonFields value={form} onChange={setForm} campuses={campuses} departments={departments} roles={roles} rolesRequired={false} />
					{!inviting && (
						<MemberLoginFields value={form} onChange={setForm} campuses={campuses} departments={departments} roles={roles} rolesRequired={false} />
					)}
					<MemberPlacementFields value={form} onChange={setForm} campuses={campuses} departments={departments} roles={roles} rolesRequired={false} />
					{!inviting && (
						<MemberRoleField value={form} onChange={setForm} campuses={campuses} departments={departments} roles={roles} rolesRequired={false} />
					)}

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

					{wantsStaffProfile && (
						<Can anyOf={["MEMBERSHIP_MANAGE"]}>
							<FormControlLabel
								control={<Checkbox checked={givesAccess} onChange={(e) => setGivesAccess(e.target.checked)} />}
								label="Give login access (emails them an invite to set their own password)"
							/>
							{givesAccess && (
								<TextField
									select
									label="Role"
									value={accessRoleId}
									onChange={(e) => setAccessRoleId(e.target.value === "" ? "" : Number(e.target.value))}
									required
									helperText="Uses the email above. Staff are added to the campus and department chosen above. Only roles whose permissions you hold yourself are listed."
									fullWidth
								>
									{delegableRoles.map((role) => (
										<MenuItem key={role.id} value={role.id}>
											{role.name}
										</MenuItem>
									))}
								</TextField>
							)}
						</Can>
					)}

					<Box sx={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
						<Button onClick={() => navigate("/hr")}>Cancel</Button>
						<Tooltip title={wantsStaffProfile && !canManageStaff ? "You don't have permission to create staff profiles" : ""}>
							<span>
								<Button type="submit" variant="contained" disabled={submitting || (wantsStaffProfile && !canManageStaff)}>
									{submitting ? "Adding..." : "Add staff"}
								</Button>
							</span>
						</Tooltip>
					</Box>
				</Stack>
			</Paper>
			<StaffInviteDialog
				invite={issuedInvite}
				onClose={() => {
					setIssuedInvite(null);
					navigate(landingPath, { replace: true });
				}}
			/>
		</Stack>
	);
}
