import { useEffect, useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
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
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
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
import { ApiError } from "../../api/client";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { listDepartments, type DepartmentResponse } from "../../api/departments";
import { listDesignations, type DesignationResponse } from "../../api/designations";
import { listRoles, type RoleResponse } from "../../api/roles";
import { createStaffProfile } from "../../api/staffProfiles";

const EMPLOYMENT_TYPES = ["PERMANENT", "CONTRACT", "PART_TIME"];

/** Full-page "Add member" (replaces the old dialog in UsersPanel), laid out like Admit student:
 * one Paper, sections separated by dividers. An "employee" member also gets an HR StaffProfile,
 * created with the same Person, campus, employee code (= Member ID), department and joining date. */
export function MemberCreatePage() {
	const navigate = useNavigate();
	const location = useLocation();
	// Back to wherever the page was opened from (/members or /settings/users).
	const returnTo = (location.state as { from?: string } | null)?.from ?? "/members";
	const canManageMembers = useCan("MEMBERSHIP_MANAGE");
	const canManageStaff = useCan("HR_STAFF_MANAGE");

	const [form, setForm] = useState<AddMemberFormState>(EMPTY_ADD_MEMBER_FORM);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
	const [designations, setDesignations] = useState<DesignationResponse[]>([]);
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [isEmployee, setIsEmployee] = useState(false);
	const [designationId, setDesignationId] = useState<number | "">("");
	const [employmentType, setEmploymentType] = useState("PERMANENT");
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		listCampuses().then(setCampuses).catch(() => undefined);
		listDepartments().then(setDepartments).catch(() => undefined);
		listDesignations().then(setDesignations).catch(() => undefined);
		listRoles().then(setRoles).catch(() => undefined);
	}, []);

	// Submitting would only 403 without MEMBERSHIP_MANAGE, so deep links land on the list instead.
	if (!canManageMembers) {
		return <Navigate to="/members" replace />;
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSubmitting(true);
		try {
			// Validate everything the staff profile needs up front, so a missing field doesn't
			// leave a Person + login behind with no staff profile.
			if (isEmployee && (!form.memberId || !form.joiningDate || form.campusId === "" || designationId === "")) {
				setError("Employee code (Member ID), joining date, campus and designation are required for an employee");
				return;
			}
			const { person } = await submitAddMember(form);
			if (isEmployee) {
				await createStaffProfile({
					personId: person.id,
					campusId: form.campusId === "" ? null : form.campusId,
					employeeCode: form.memberId,
					designationId: Number(designationId),
					departmentId: form.departmentId === "" ? null : form.departmentId,
					dateOfJoining: form.joiningDate,
					employmentType,
					reportingManagerId: null,
				});
			}
			navigate(returnTo, {
				replace: true,
				state: {
					notice: isEmployee
						? "Employee added with an HR staff profile. They can sign in with the password you set."
						: "Member added. They can sign in with the password you set.",
				},
			});
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add member");
		} finally {
			setSubmitting(false);
		}
	}

	const fieldProps = { value: form, onChange: setForm, campuses, departments, roles };

	return (
		<Stack spacing={2}>
			<Typography variant="h4" component="h1">
				Add member
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="subtitle1">Person details</Typography>
					<MemberPersonFields {...fieldProps} />

					<Divider />
					<Typography variant="subtitle1">Access</Typography>
					<MemberRoleField {...fieldProps} />
					<MemberLoginFields {...fieldProps} />

					<Divider />
					<Typography variant="subtitle1">Placement</Typography>
					<MemberPlacementFields {...fieldProps} />

					<Divider />
					<Typography variant="subtitle1">Employment</Typography>
					<FormControlLabel
						control={<Checkbox checked={isEmployee} onChange={(e) => setIsEmployee(e.target.checked)} />}
						label="This member is an employee (also create an HR staff profile)"
					/>
					{isEmployee && (
						<>
							<Typography variant="caption" color="text.secondary">
								Member ID becomes the employee code; campus, department and joining date carry over to the staff profile.
							</Typography>
							{!canManageStaff && (
								<Alert severity="warning">You don't have permission to create staff profiles (HR_STAFF_MANAGE).</Alert>
							)}
							<Box sx={{ display: "flex", gap: 2 }}>
								<TextField
									select
									label="Designation"
									value={designationId}
									onChange={(e) => setDesignationId(Number(e.target.value))}
									required
									fullWidth
								>
									{designations
										.filter((designation) => designation.status === "ACTIVE")
										.map((designation) => (
											<MenuItem key={designation.id} value={designation.id}>
												{designation.name}
											</MenuItem>
										))}
								</TextField>
								<TextField
									select
									label="Employment type"
									value={employmentType}
									onChange={(e) => setEmploymentType(e.target.value)}
									required
									fullWidth
								>
									{EMPLOYMENT_TYPES.map((type) => (
										<MenuItem key={type} value={type}>
											{type}
										</MenuItem>
									))}
								</TextField>
							</Box>
						</>
					)}

					<Box sx={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
						<Button onClick={() => navigate(returnTo)}>Cancel</Button>
						<Tooltip title={isEmployee && !canManageStaff ? "You don't have permission to create staff profiles" : ""}>
							<span>
								<Button type="submit" variant="contained" disabled={submitting || (isEmployee && !canManageStaff)}>
									{submitting ? "Adding..." : "Add member"}
								</Button>
							</span>
						</Tooltip>
					</Box>
				</Stack>
			</Paper>
		</Stack>
	);
}
