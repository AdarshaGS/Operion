import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import type { CampusResponse } from "../api/campuses";
import type { DepartmentResponse } from "../api/departments";
import { grantMembership } from "../api/memberships";
import { createPerson, type PersonResponse } from "../api/persons";
import type { RoleResponse } from "../api/roles";
import { inviteUser, type StaffInviteResponse } from "../api/users";

/** The base "Add member" fields per the product spec's generic member workflow (GitHub
 * #104): full name, email/mobile, optional member ID, campus, optional department,
 * optional joining date, and role(s) - available to any member regardless of which
 * entry point (Settings > Users or HR > Add staff) created them. HR-specific fields
 * (designation, employment type) are a separate, optional extension a caller layers on
 * top of this, not part of the shared shape. */
export interface AddMemberFormState {
	firstName: string;
	lastName: string;
	email: string;
	phone: string;
	address: string;
	memberId: string;
	campusId: number | "";
	departmentId: number | "";
	joiningDate: string;
	roleIds: number[];
}

export const EMPTY_ADD_MEMBER_FORM: AddMemberFormState = {
	firstName: "",
	lastName: "",
	email: "",
	phone: "",
	address: "",
	memberId: "",
	campusId: "",
	departmentId: "",
	joiningDate: "",
	roleIds: [],
};

interface AddMemberFieldsProps {
	value: AddMemberFormState;
	onChange: (next: AddMemberFormState) => void;
	campuses: CampusResponse[];
	departments: DepartmentResponse[];
	roles: RoleResponse[];
	/** Settings > Users exists purely to grant access, so at least one role is mandatory
	 * there. HR > Add staff can still add a person with no login at all (e.g. staff who
	 * never touch the system) - defaults true since that's the more common case. */
	rolesRequired?: boolean;
}

/** Shared fields UI - each caller supplies its own container (Dialog vs a full Paper
 * page) and its own submit button, since those differ enough between Settings > Users
 * and HR > Add staff that forcing one shared container added more indirection than it
 * saved. */
export function AddMemberFields({ value, onChange, campuses, departments, roles, rolesRequired = true }: AddMemberFieldsProps) {
	function set<K extends keyof AddMemberFormState>(key: K, next: AddMemberFormState[K]) {
		onChange({ ...value, [key]: next });
	}

	const activeRoles = roles.filter((role) => role.status === "ACTIVE");

	return (
		<>
			<Box sx={{ display: "flex", gap: 2 }}>
				<TextField
					label="First name"
					value={value.firstName}
					onChange={(e) => set("firstName", e.target.value)}
					required
					autoFocus
					fullWidth
				/>
				<TextField label="Last name" value={value.lastName} onChange={(e) => set("lastName", e.target.value)} required fullWidth />
			</Box>
			<Box sx={{ display: "flex", gap: 2 }}>
				<TextField label="Email" type="email" value={value.email} onChange={(e) => set("email", e.target.value)} fullWidth />
				<TextField label="Phone" value={value.phone} onChange={(e) => set("phone", e.target.value)} fullWidth />
			</Box>
			<TextField
				label="Address (optional)"
				value={value.address}
				onChange={(e) => set("address", e.target.value)}
				fullWidth
				multiline
				minRows={2}
			/>
			<Box sx={{ display: "flex", gap: 2 }}>
				<TextField label="Member ID (optional)" value={value.memberId} onChange={(e) => set("memberId", e.target.value)} fullWidth />
				<TextField
					label="Joining date (optional)"
					type="date"
					value={value.joiningDate}
					onChange={(e) => set("joiningDate", e.target.value)}
					slotProps={{ inputLabel: { shrink: true } }}
					fullWidth
				/>
			</Box>
			<Box sx={{ display: "flex", gap: 2 }}>
				<TextField
					select
					label="Campus (optional — org-wide if left blank)"
					value={value.campusId}
					onChange={(e) => set("campusId", e.target.value === "" ? "" : Number(e.target.value))}
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
					value={value.departmentId}
					onChange={(e) => set("departmentId", e.target.value === "" ? "" : Number(e.target.value))}
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
			<TextField
				select
				label={rolesRequired ? "Role(s)" : "Role(s) (optional — no login granted if left blank)"}
				value={value.roleIds.map(String)}
				onChange={(e) => {
					const raw = e.target.value as unknown as string[];
					set("roleIds", (Array.isArray(raw) ? raw : [raw]).filter(Boolean).map(Number));
				}}
				slotProps={{
					select: {
						multiple: true,
						renderValue: (selected) =>
							roles
								.filter((role) => (selected as string[]).includes(String(role.id)))
								.map((role) => role.name)
								.join(", "),
					},
				}}
				required={rolesRequired}
				fullWidth
			>
				{activeRoles.map((role) => (
					<MenuItem key={role.id} value={role.id}>
						<Checkbox checked={value.roleIds.includes(role.id)} size="small" />
						<ListItemText primary={role.name} />
					</MenuItem>
				))}
			</TextField>
		</>
	);
}

export interface AddMemberResult {
	person: PersonResponse;
	/** Null when the caller left Role(s) blank (only valid where AddMemberFields was
	 * rendered with rolesRequired={false}) - a person with no login granted at all. */
	invite: StaffInviteResponse | null;
}

/** The one sequence both entry points used to duplicate: create Person → invite login →
 * grant membership once per selected role (GitHub #104 + #107 - a member can hold more
 * than one role from the moment they're added, not just via a UserDetailPage follow-up).
 * Each grantMembership call is a separate OrganisationMembership row by design (one row
 * per role, see that entity's javadoc) - memberId/joiningDate are duplicated onto every
 * one of them since there's no separate "member" aggregate to hang a single copy off. */
export async function submitAddMember(form: AddMemberFormState): Promise<AddMemberResult> {
	const person = await createPerson({
		firstName: form.firstName,
		lastName: form.lastName,
		email: form.email || null,
		phone: form.phone || null,
		address: form.address || null,
	});

	if (form.roleIds.length === 0) {
		return { person, invite: null };
	}

	const invite = await inviteUser({ email: form.email, phone: form.phone || null });
	for (const roleId of form.roleIds) {
		await grantMembership({
			userId: invite.userId,
			personId: person.id,
			roleId,
			campusId: form.campusId === "" ? null : form.campusId,
			departmentId: form.departmentId === "" ? null : form.departmentId,
			memberId: form.memberId || null,
			joiningDate: form.joiningDate || null,
		});
	}

	return { person, invite };
}
