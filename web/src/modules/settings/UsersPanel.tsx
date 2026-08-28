import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { Can } from "../../auth/Can";
import { AddMemberFields, EMPTY_ADD_MEMBER_FORM, submitAddMember, type AddMemberFormState } from "../../components/AddMemberForm";
import { MemberStatusChip } from "../../components/MemberStatusChip";
import { StaffInviteDialog } from "../../components/StaffInviteDialog";
import { ApiError } from "../../api/client";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { listDepartments, type DepartmentResponse } from "../../api/departments";
import { listMemberships, revokeMembership, type MembershipResponse } from "../../api/memberships";
import { listRoles, type RoleResponse } from "../../api/roles";
import type { StaffInviteResponse } from "../../api/users";

/** One "Users" section rather than a separate plain login list and a separate "people
 * with access" list - this table is memberships (one row per person+role, same as
 * before), each row links through to UserDetailPage for that row's user. Add user
 * creates a brand-new Person + User "login shell" + Membership in one sequential flow,
 * same pattern as StudentCreatePage/StaffCreatePage, but via the invite flow (see
 * StaffInviteService) rather than an admin setting the password directly - granting an
 * additional role to someone who already has a Person/User record lives on
 * UserDetailPage instead (reachable by clicking a row here), since the userId is already
 * fixed from that page and only a Person needs picking there. */
interface UsersPanelProps {
	/** /members/invite lands here with the Add-user dialog already open, instead of the
	 * caller having to land on a plain list and hunt for the button themselves. */
	autoOpenInvite?: boolean;
}

export function UsersPanel({ autoOpenInvite = false }: UsersPanelProps = {}) {
	const navigate = useNavigate();
	const [memberships, setMemberships] = useState<MembershipResponse[]>([]);
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(autoOpenInvite);
	const [submitting, setSubmitting] = useState(false);

	const [form, setForm] = useState<AddMemberFormState>(EMPTY_ADD_MEMBER_FORM);
	const [invite, setInvite] = useState<StaffInviteResponse | null>(null);

	function refresh() {
		listMemberships()
			.then(setMemberships)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load users"));
	}

	useEffect(() => {
		refresh();
		listRoles()
			.then(setRoles)
			.catch(() => undefined);
		listCampuses()
			.then(setCampuses)
			.catch(() => undefined);
		listDepartments()
			.then(setDepartments)
			.catch(() => undefined);
	}, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			const { invite: issuedInvite } = await submitAddMember(form);
			setForm(EMPTY_ADD_MEMBER_FORM);
			setDialogOpen(false);
			setInvite(issuedInvite);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add user");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleRevoke(id: number) {
		try {
			await revokeMembership(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to revoke membership");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Users</Typography>
					<Can anyOf={["MEMBERSHIP_MANAGE"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
							Add user
						</Button>
					</Can>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Person</TableCell>
								<TableCell>Member ID</TableCell>
								<TableCell>Role</TableCell>
								<TableCell>Campus</TableCell>
								<TableCell>Status</TableCell>
								<TableCell align="right">Actions</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{memberships.map((membership) => (
								<TableRow
									key={membership.id}
									hover
									sx={{ cursor: "pointer" }}
									onClick={() => navigate(`/settings/users/${membership.userId}`)}
								>
									<TableCell>{membership.personName}</TableCell>
									<TableCell>{membership.memberId ?? "—"}</TableCell>
									<TableCell>{membership.roleName}</TableCell>
									<TableCell>
										{membership.campusId === null
											? "Org-wide"
											: (campuses.find((c) => c.id === membership.campusId)?.name ?? membership.campusId)}
									</TableCell>
									<TableCell>
										<MemberStatusChip status={membership.memberStatus} />
									</TableCell>
									<TableCell align="right">
										<Can anyOf={["MEMBERSHIP_MANAGE"]}>
											{membership.status === "ACTIVE" && (
												<Button
													size="small"
													onClick={(event) => {
														event.stopPropagation();
														handleRevoke(membership.id);
													}}
												>
													Revoke
												</Button>
											)}
										</Can>
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>Add user</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<AddMemberFields value={form} onChange={setForm} campuses={campuses} departments={departments} roles={roles} />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Invite
					</Button>
				</DialogActions>
			</Dialog>

			<StaffInviteDialog invite={invite} onClose={() => setInvite(null)} />
		</Paper>
	);
}
