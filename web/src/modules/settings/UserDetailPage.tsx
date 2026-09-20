import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import EditIcon from "@mui/icons-material/Edit";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import RemoveCircleIcon from "@mui/icons-material/RemoveCircle";
import { Can } from "../../auth/Can";
import { MemberStatusChip } from "../../components/MemberStatusChip";
import { getAuditLogs, type AuditLogResponse } from "../../api/auditLogs";
import { ApiError } from "../../api/client";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { listDepartments, type DepartmentResponse } from "../../api/departments";
import { grantMembership, listMemberships, revokeMembership, type MembershipResponse } from "../../api/memberships";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listRoles, type RoleResponse } from "../../api/roles";
import { changeUserStatus, getUser, updateUser, type UserResponse } from "../../api/users";

/** Mirrors RoleController's status pattern (see RoleController.changeStatus) - the same
 * action also serves as "deactivate" (status DISABLED), see UserService.changeStatus.
 * Disable/Reactivate is the one status action surfaced directly in the header (spec: a
 * member's login is either disabled or not); Lock/Unlock is a less common secondary
 * action, tucked into the overflow menu instead. */
const OVERFLOW_STATUS_ACTIONS: Record<string, { label: string; nextStatus: string }[]> = {
	ACTIVE: [{ label: "Lock member", nextStatus: "LOCKED" }],
	LOCKED: [{ label: "Unlock member", nextStatus: "ACTIVE" }],
	DISABLED: [],
	PENDING: [],
};

const ACTIVITY_ACTION_LABEL: Record<string, string> = {
	STATUS_CHANGE: "Status changed",
	CONTACT_UPDATE: "Contact details updated",
};

/** Mirrors the backend's MemberStatus.of(User.status, membership.status) - same three
 * inputs, computed client-side since a member's memberships can lag one tick behind a
 * just-changed user status (see handleStatusChange). */
function computeStatus(user: UserResponse | null, memberships: MembershipResponse[]): "INVITED" | "ACTIVE" | "INACTIVE" {
	if (!user) return "INACTIVE";
	if (user.status === "PENDING") return "INVITED";
	if (user.status === "LOCKED" || user.status === "DISABLED") return "INACTIVE";
	return memberships.some((m) => m.status === "ACTIVE") ? "ACTIVE" : "INACTIVE";
}

/** No GET-by-id exists for OrganisationMembership filtered by user, so this composes
 * the user's own memberships client-side from the full list - same tradeoff as
 * RouteDetailPage/MarksEntryPage at this data scale. This is also where "grant an
 * existing person another role" actually lives, rather than in UsersPanel's own
 * "Add member" dialog (which is deliberately kept to just "onboard a brand new
 * person") - the userId is already fixed from this page, so only a Person needs picking. */
export function UserDetailPage() {
	const { userId } = useParams<{ userId: string }>();
	const navigate = useNavigate();
	const location = useLocation();
	const [searchParams, setSearchParams] = useSearchParams();
	// Reachable from both /members/:userId and /settings/users/:userId - back goes to
	// whichever list the caller entered from, not always Settings.
	const fromMembers = location.pathname.startsWith("/members");

	const [user, setUser] = useState<UserResponse | null>(null);
	const [memberships, setMemberships] = useState<MembershipResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
	const [activity, setActivity] = useState<AuditLogResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [personId, setPersonId] = useState<number | "">("");
	const [roleId, setRoleId] = useState<number | "">("");
	const [campusId, setCampusId] = useState<number | "">("");
	const [departmentId, setDepartmentId] = useState<number | "">("");
	const [submitting, setSubmitting] = useState(false);

	const [statusMenuAnchor, setStatusMenuAnchor] = useState<HTMLElement | null>(null);
	const [statusSubmitting, setStatusSubmitting] = useState(false);
	const [editOpen, setEditOpen] = useState(false);
	const [editEmail, setEditEmail] = useState("");
	const [editPhone, setEditPhone] = useState("");
	const [editSubmitting, setEditSubmitting] = useState(false);

	useEffect(() => {
		if (!userId) return;
		getUser(Number(userId))
			.then(setUser)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load member"))
			.finally(() => setLoading(false));
		listPersons().then(setPersons).catch(() => undefined);
		listRoles().then(setRoles).catch(() => undefined);
		listCampuses().then(setCampuses).catch(() => undefined);
		listDepartments().then(setDepartments).catch(() => undefined);
		getAuditLogs({ entityType: "User", entityId: Number(userId), size: 5 })
			.then((page) => setActivity(page.content))
			.catch(() => undefined);
		refreshMemberships();
	}, [userId]);

	useEffect(() => {
		if (searchParams.get("action") === "grant") {
			setDialogOpen(true);
			const next = new URLSearchParams(searchParams);
			next.delete("action");
			setSearchParams(next, { replace: true });
		}
		// Only react to the initial query string, not to state changes this effect itself causes.
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	function refreshMemberships() {
		if (!userId) return;
		listMemberships()
			.then((all) => setMemberships(all.filter((m) => m.userId === Number(userId))))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load roles"));
	}

	async function handleGrant(event: FormEvent) {
		event.preventDefault();
		if (!userId || personId === "" || roleId === "") return;
		setSubmitting(true);
		try {
			await grantMembership({
				userId: Number(userId),
				personId,
				roleId,
				campusId: campusId === "" ? null : campusId,
				departmentId: departmentId === "" ? null : departmentId,
			});
			setPersonId("");
			setRoleId("");
			setCampusId("");
			setDepartmentId("");
			setDialogOpen(false);
			refreshMemberships();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to grant role");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleRevoke(id: number) {
		try {
			await revokeMembership(id);
			refreshMemberships();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to revoke role");
		}
	}

	async function handleStatusChange(nextStatus: string) {
		if (!userId) return;
		setStatusMenuAnchor(null);
		setStatusSubmitting(true);
		try {
			setUser(await changeUserStatus(Number(userId), nextStatus));
			refreshMemberships();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update status");
		} finally {
			setStatusSubmitting(false);
		}
	}

	function openEdit() {
		setEditEmail(user?.email ?? "");
		setEditPhone(user?.phone ?? "");
		setEditOpen(true);
	}

	async function handleEditSave(event: FormEvent) {
		event.preventDefault();
		if (!userId) return;
		setEditSubmitting(true);
		try {
			setUser(await updateUser(Number(userId), { email: editEmail, phone: editPhone || null }));
			setEditOpen(false);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update member");
		} finally {
			setEditSubmitting(false);
		}
	}

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	const status = computeStatus(user, memberships);
	const personName = memberships[0]?.personName ?? `Member #${userId}`;
	const memberId = memberships.find((m) => m.memberId)?.memberId ?? "—";
	const overflowActions = user ? (OVERFLOW_STATUS_ACTIONS[user.status] ?? []) : [];
	const primaryStatusAction =
		user?.status === "DISABLED" ? { label: "Reactivate member", nextStatus: "ACTIVE" } : { label: "Disable member", nextStatus: "DISABLED" };

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate(fromMembers ? "/members" : "/settings")}>
					{fromMembers ? "Back to members" : "Back to settings"}
				</Button>
			</Box>

			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: 2 }}>
				<Box>
					<Typography variant="h4" component="h1">
						{personName}
					</Typography>
					{user && (
						<Typography variant="body2" color="text.secondary">
							{user.email} · {user.phone ?? "No phone on file"}
						</Typography>
					)}
					<Box sx={{ mt: 1 }}>
						<MemberStatusChip status={status} />
					</Box>
				</Box>
				{user && (
					<Can anyOf={["MEMBERSHIP_MANAGE"]}>
						<Stack direction="row" spacing={1}>
							<Button size="small" startIcon={<EditIcon />} onClick={openEdit}>
								Edit member
							</Button>
							<Button
								size="small"
								variant="outlined"
								color={primaryStatusAction.nextStatus === "DISABLED" ? "error" : "primary"}
								disabled={statusSubmitting}
								onClick={() => handleStatusChange(primaryStatusAction.nextStatus)}
							>
								{primaryStatusAction.label}
							</Button>
							{overflowActions.length > 0 && (
								<>
									<IconButton size="small" onClick={(e) => setStatusMenuAnchor(e.currentTarget)}>
										<MoreVertIcon fontSize="small" />
									</IconButton>
									<Menu anchorEl={statusMenuAnchor} open={statusMenuAnchor !== null} onClose={() => setStatusMenuAnchor(null)}>
										{overflowActions.map((action) => (
											<MenuItem key={action.nextStatus} onClick={() => handleStatusChange(action.nextStatus)}>
												{action.label}
											</MenuItem>
										))}
									</Menu>
								</>
							)}
						</Stack>
					</Can>
				)}
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			<Grid container spacing={2}>
				<Grid size={{ xs: 12, md: 6 }}>
					<Paper sx={{ p: 3, height: "100%" }}>
						<Typography variant="h6" sx={{ mb: 2 }}>
							Account
						</Typography>
						<Grid container spacing={2}>
							<AccountField label="Email" value={user?.email ?? "—"} />
							<AccountField label="Phone" value={user?.phone ?? "No phone on file"} />
							<AccountField label="Member ID" value={memberId} />
							<AccountField label="Status" value={<MemberStatusChip status={status} />} />
							<AccountField label="Invitation date" value={user ? new Date(user.invitedAt).toLocaleDateString() : "—"} />
							<AccountField label="Last login" value={user?.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : "Never signed in"} />
						</Grid>
					</Paper>
				</Grid>

				<Grid size={{ xs: 12, md: 6 }}>
					<Paper sx={{ p: 3, height: "100%" }}>
						<Typography variant="h6" sx={{ mb: 2 }}>
							Activity
						</Typography>
						<Stack spacing={1.5}>
							<Typography variant="body2">
								{user?.status === "PENDING"
									? `Invitation sent ${new Date(user.invitedAt).toLocaleDateString()} — not yet accepted.`
									: "Invitation accepted."}
							</Typography>
							<Typography variant="body2">
								Last login: {user?.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : "Never signed in"}
							</Typography>
							<Box>
								<Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5 }}>
									Recent activity
								</Typography>
								{activity.length === 0 && (
									<Typography variant="body2" color="text.secondary">
										No recorded activity yet.
									</Typography>
								)}
								<Stack spacing={0.75}>
									{activity.map((entry) => (
										<Typography key={entry.id} variant="body2">
											{ACTIVITY_ACTION_LABEL[entry.action] ?? entry.action} · {new Date(entry.occurredAt).toLocaleString()}
										</Typography>
									))}
								</Stack>
							</Box>
						</Stack>
					</Paper>
				</Grid>
			</Grid>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Access</Typography>
						<Can anyOf={["MEMBERSHIP_MANAGE"]}>
							<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
								Grant role
							</Button>
						</Can>
					</Box>

					{memberships.length === 0 && <Alert severity="info">This member holds no roles yet.</Alert>}

					{memberships.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Role</TableCell>
										<TableCell>Campus</TableCell>
										<TableCell>Department</TableCell>
										<TableCell>Status</TableCell>
										<TableCell align="right">Actions</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{memberships.map((membership) => (
										<TableRow key={membership.id}>
											<TableCell>{membership.roleName}</TableCell>
											<TableCell>
												{membership.campusId === null
													? "Org-wide"
													: (campuses.find((c) => c.id === membership.campusId)?.name ?? membership.campusId)}
											</TableCell>
											<TableCell>{membership.departmentName ?? "—"}</TableCell>
											<TableCell>
												<MemberStatusChip status={membership.memberStatus} />
											</TableCell>
											<TableCell align="right">
												<Can anyOf={["MEMBERSHIP_MANAGE"]}>
													{membership.status === "ACTIVE" && (
														<Tooltip title="Revoke role">
															<IconButton size="small" color="default" onClick={() => handleRevoke(membership.id)}>
																<RemoveCircleIcon fontSize="small" />
															</IconButton>
														</Tooltip>
													)}
												</Can>
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleGrant} fullWidth maxWidth="xs">
				<DialogTitle>Grant role</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							select
							label="Person"
							value={personId}
							onChange={(e) => setPersonId(e.target.value === "" ? "" : Number(e.target.value))}
							required
							autoFocus
							fullWidth
						>
							{persons.map((person) => (
								<MenuItem key={person.id} value={person.id}>
									{person.firstName} {person.lastName}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Role"
							value={roleId}
							onChange={(e) => setRoleId(e.target.value === "" ? "" : Number(e.target.value))}
							required
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
							value={campusId}
							onChange={(e) => setCampusId(e.target.value === "" ? "" : Number(e.target.value))}
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
							value={departmentId}
							onChange={(e) => setDepartmentId(e.target.value === "" ? "" : Number(e.target.value))}
							fullWidth
						>
							<MenuItem value="">No department</MenuItem>
							{departments.map((department) => (
								<MenuItem key={department.id} value={department.id}>
									{department.name}
								</MenuItem>
							))}
						</TextField>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Grant
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={editOpen} onClose={() => setEditOpen(false)} component="form" onSubmit={handleEditSave} fullWidth maxWidth="xs">
				<DialogTitle>Edit member</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Email"
							type="email"
							value={editEmail}
							onChange={(e) => setEditEmail(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<TextField label="Phone" value={editPhone} onChange={(e) => setEditPhone(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setEditOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={editSubmitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}

function AccountField({ label, value }: { label: string; value: ReactNode }) {
	return (
		<Grid size={{ xs: 12, sm: 6 }}>
			<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
				{label}
			</Typography>
			<Typography variant="body2" component="div">
				{value}
			</Typography>
		</Grid>
	);
}
