import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import InputAdornment from "@mui/material/InputAdornment";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
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
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import BlockIcon from "@mui/icons-material/Block";
import EditIcon from "@mui/icons-material/Edit";
import MailIcon from "@mui/icons-material/Mail";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import RemoveCircleIcon from "@mui/icons-material/RemoveCircle";
import SearchIcon from "@mui/icons-material/Search";
import VisibilityIcon from "@mui/icons-material/Visibility";
import { Can } from "../../auth/Can";
import { AddMemberFields, EMPTY_ADD_MEMBER_FORM, submitAddMember, type AddMemberFormState } from "../../components/AddMemberForm";
import { MemberStatusChip } from "../../components/MemberStatusChip";
import { StaffInviteDialog } from "../../components/StaffInviteDialog";
import { ApiError } from "../../api/client";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { listDepartments, type DepartmentResponse } from "../../api/departments";
import { listMemberships, revokeMembership, type MembershipResponse } from "../../api/memberships";
import { listRoles, type RoleResponse } from "../../api/roles";
import { changeUserStatus, resendInvite, type StaffInviteResponse } from "../../api/users";
import { StatTile } from "../dashboard/StatTile";

/** One "Users" section rather than a separate plain login list and a separate "people
 * with access" list. Each table row is a PERSON (one or more OrganisationMembership rows
 * grouped by userId, see groupMembers below), not a single membership - the raw
 * one-row-per-role data still backs the detail page's own Access table. Add member
 * creates a brand-new Person + User "login shell" + Membership in one sequential flow
 * (see StaffInviteService); granting an additional role to someone who already has a
 * Person/User record lives on the member detail page instead. */
interface UsersPanelProps {
	/** /members/invite lands here with the Add-member dialog already open, instead of the
	 * caller having to land on a plain list and hunt for the button themselves. */
	autoOpenInvite?: boolean;
}

interface MemberRow {
	userId: number;
	personName: string;
	email: string;
	phone: string | null;
	memberId: string | null;
	memberStatus: "INVITED" | "ACTIVE" | "INACTIVE";
	lastLoginAt: string | null;
	memberships: MembershipResponse[];
}

function groupMembers(memberships: MembershipResponse[]): MemberRow[] {
	const byUser = new Map<number, MembershipResponse[]>();
	for (const membership of memberships) {
		const rows = byUser.get(membership.userId) ?? [];
		rows.push(membership);
		byUser.set(membership.userId, rows);
	}
	return Array.from(byUser.values()).map((rows) => {
		const first = rows[0];
		const memberStatus: MemberRow["memberStatus"] = rows.some((row) => row.memberStatus === "INVITED")
			? "INVITED"
			: rows.some((row) => row.memberStatus === "ACTIVE")
				? "ACTIVE"
				: "INACTIVE";
		return {
			userId: first.userId,
			personName: first.personName,
			email: first.email,
			phone: first.phone,
			memberId: rows.find((row) => row.memberId)?.memberId ?? null,
			memberStatus,
			lastLoginAt: first.lastLoginAt,
			memberships: rows,
		};
	});
}

function formatLastActivity(row: MemberRow): string {
	if (row.lastLoginAt) return new Date(row.lastLoginAt).toLocaleString();
	return row.memberStatus === "INVITED" ? "Not yet signed in" : "Never signed in";
}

export function UsersPanel({ autoOpenInvite = false }: UsersPanelProps = {}) {
	const navigate = useNavigate();
	const location = useLocation();
	// Reachable from both /members and /settings/users - drill-down stays under
	// whichever URL the caller entered from, rather than always landing on one.
	const detailBasePath = location.pathname.startsWith("/members") ? "/members" : "/settings/users";
	const [memberships, setMemberships] = useState<MembershipResponse[]>([]);
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(autoOpenInvite);
	const [submitting, setSubmitting] = useState(false);

	const [form, setForm] = useState<AddMemberFormState>(EMPTY_ADD_MEMBER_FORM);
	const [invite, setInvite] = useState<StaffInviteResponse | null>(null);

	const [search, setSearch] = useState("");
	const [roleFilter, setRoleFilter] = useState("");
	const [campusFilter, setCampusFilter] = useState("");
	const [statusFilter, setStatusFilter] = useState("");

	const [menu, setMenu] = useState<{ anchor: HTMLElement; row: MemberRow } | null>(null);

	function refresh() {
		listMemberships()
			.then(setMemberships)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load members"));
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

	const members = useMemo(() => groupMembers(memberships), [memberships]);

	const filteredMembers = useMemo(() => {
		const query = search.trim().toLowerCase();
		return members.filter((row) => {
			if (query) {
				const haystack = `${row.personName} ${row.email} ${row.phone ?? ""} ${row.memberId ?? ""}`.toLowerCase();
				if (!haystack.includes(query)) return false;
			}
			if (roleFilter && !row.memberships.some((m) => String(m.roleId) === roleFilter)) return false;
			if (campusFilter === "org-wide" && !row.memberships.some((m) => m.campusId === null)) return false;
			if (campusFilter && campusFilter !== "org-wide" && !row.memberships.some((m) => String(m.campusId) === campusFilter)) {
				return false;
			}
			if (statusFilter && row.memberStatus !== statusFilter) return false;
			return true;
		});
	}, [members, search, roleFilter, campusFilter, statusFilter]);

	const totalCount = members.length;
	const activeCount = members.filter((row) => row.memberStatus === "ACTIVE").length;
	const pendingCount = members.filter((row) => row.memberStatus === "INVITED").length;

	function campusLabel(row: MemberRow): string {
		const campusIds = new Set(row.memberships.map((m) => m.campusId));
		if (campusIds.size > 1) return "Multiple campuses";
		const [only] = campusIds;
		if (only === null || only === undefined) return "Org-wide";
		return campuses.find((c) => c.id === only)?.name ?? String(only);
	}

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
			setError(err instanceof ApiError ? err.message : "Failed to add member");
		} finally {
			setSubmitting(false);
		}
	}

	function closeMenu() {
		setMenu(null);
	}

	async function handleResendInvite(row: MemberRow) {
		closeMenu();
		try {
			setInvite(await resendInvite(row.userId));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to resend invitation");
		}
	}

	async function handleDisable(row: MemberRow) {
		closeMenu();
		try {
			await changeUserStatus(row.userId, "DISABLED");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to disable member");
		}
	}

	async function handleRevokeRole(row: MemberRow) {
		closeMenu();
		const [onlyMembership] = row.memberships;
		try {
			await revokeMembership(onlyMembership.id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to revoke role");
		}
	}

	return (
		<Stack spacing={2}>
			<Grid container spacing={2}>
				<Grid size={{ xs: 12, sm: 4 }}>
					<StatTile label="Total members" value={totalCount} />
				</Grid>
				<Grid size={{ xs: 12, sm: 4 }}>
					<StatTile label="Active" value={activeCount} />
				</Grid>
				<Grid size={{ xs: 12, sm: 4 }}>
					<StatTile label="Pending" value={pendingCount} />
				</Grid>
			</Grid>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", flexWrap: "wrap", gap: 1.5, alignItems: "center" }}>
						<TextField
							size="small"
							placeholder="Search members"
							value={search}
							onChange={(e) => setSearch(e.target.value)}
							slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
							sx={{ minWidth: 220 }}
						/>
						<TextField select size="small" label="Role" value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)} sx={{ minWidth: 160 }}>
							<MenuItem value="">All roles</MenuItem>
							{roles.map((role) => (
								<MenuItem key={role.id} value={String(role.id)}>
									{role.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							size="small"
							label="Campus"
							value={campusFilter}
							onChange={(e) => setCampusFilter(e.target.value)}
							sx={{ minWidth: 160 }}
						>
							<MenuItem value="">All campuses</MenuItem>
							<MenuItem value="org-wide">Org-wide</MenuItem>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={String(campus.id)}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							size="small"
							label="Status"
							value={statusFilter}
							onChange={(e) => setStatusFilter(e.target.value)}
							sx={{ minWidth: 150 }}
						>
							<MenuItem value="">All statuses</MenuItem>
							<MenuItem value="ACTIVE">Active</MenuItem>
							<MenuItem value="INVITED">Invited</MenuItem>
							<MenuItem value="INACTIVE">Inactive</MenuItem>
						</TextField>
						<Box sx={{ flexGrow: 1 }} />
						<Can anyOf={["MEMBERSHIP_MANAGE"]}>
							<Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
								Add member
							</Button>
						</Can>
					</Box>

					{error && <Alert severity="error">{error}</Alert>}

					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Person</TableCell>
									<TableCell>Contact</TableCell>
									<TableCell>Role</TableCell>
									<TableCell>Campus</TableCell>
									<TableCell>Status</TableCell>
									<TableCell>Last activity</TableCell>
									<TableCell align="right">Actions</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{filteredMembers.map((row) => {
									const visibleRoles = row.memberships.slice(0, 2);
									const extraRoles = row.memberships.length - visibleRoles.length;
									return (
										<TableRow
											key={row.userId}
											hover
											sx={{ cursor: "pointer" }}
											onClick={() => navigate(`${detailBasePath}/${row.userId}`)}
										>
											<TableCell>
												<Typography variant="body2" sx={{ fontWeight: 600 }}>
													{row.personName}
												</Typography>
												{row.memberId && (
													<Typography variant="caption" color="text.secondary">
														{row.memberId}
													</Typography>
												)}
											</TableCell>
											<TableCell>
												<Typography variant="body2">{row.email}</Typography>
												<Typography variant="caption" color="text.secondary">
													{row.phone ?? "No phone on file"}
												</Typography>
											</TableCell>
											<TableCell>
												<Stack direction="row" spacing={0.5} sx={{ flexWrap: "wrap" }}>
													{visibleRoles.map((m) => (
														<Chip key={m.id} label={m.roleName} size="small" />
													))}
													{extraRoles > 0 && <Chip label={`+${extraRoles}`} size="small" variant="outlined" />}
												</Stack>
											</TableCell>
											<TableCell>{campusLabel(row)}</TableCell>
											<TableCell>
												<MemberStatusChip status={row.memberStatus} />
											</TableCell>
											<TableCell>{formatLastActivity(row)}</TableCell>
											<TableCell align="right">
												<Can anyOf={["MEMBERSHIP_MANAGE"]}>
													<IconButton
														size="small"
														onClick={(event) => {
															event.stopPropagation();
															setMenu({ anchor: event.currentTarget, row });
														}}
													>
														<MoreVertIcon fontSize="small" />
													</IconButton>
												</Can>
											</TableCell>
										</TableRow>
									);
								})}
								{filteredMembers.length === 0 && (
									<TableRow>
										<TableCell colSpan={7}>
											<Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: "center" }}>
												No members match these filters.
											</Typography>
										</TableCell>
									</TableRow>
								)}
							</TableBody>
						</Table>
					</TableContainer>
				</Stack>
			</Paper>

			<Menu anchorEl={menu?.anchor} open={menu !== null} onClose={closeMenu} onClick={(e) => e.stopPropagation()}>
				<MenuItem
					onClick={() => {
						if (!menu) return;
						navigate(`${detailBasePath}/${menu.row.userId}`);
						closeMenu();
					}}
				>
					<ListItemIcon>
						<VisibilityIcon fontSize="small" />
					</ListItemIcon>
					<ListItemText>View member</ListItemText>
				</MenuItem>
				<MenuItem
					onClick={() => {
						if (!menu) return;
						navigate(`${detailBasePath}/${menu.row.userId}?action=grant`);
						closeMenu();
					}}
				>
					<ListItemIcon>
						<EditIcon fontSize="small" />
					</ListItemIcon>
					<ListItemText>Edit access</ListItemText>
				</MenuItem>
				{menu?.row.memberStatus === "INVITED" && (
					<MenuItem onClick={() => menu && handleResendInvite(menu.row)}>
						<ListItemIcon>
							<MailIcon fontSize="small" />
						</ListItemIcon>
						<ListItemText>Resend invitation</ListItemText>
					</MenuItem>
				)}
				{menu?.row.memberships.length === 1 && menu.row.memberships[0].status === "ACTIVE" && (
					<MenuItem onClick={() => menu && handleRevokeRole(menu.row)} sx={{ color: "text.secondary" }}>
						<ListItemIcon>
							<RemoveCircleIcon fontSize="small" />
						</ListItemIcon>
						<ListItemText>Revoke role</ListItemText>
					</MenuItem>
				)}
				{menu?.row.memberStatus !== "INACTIVE" && (
					<MenuItem onClick={() => menu && handleDisable(menu.row)} sx={{ color: "error.main" }}>
						<ListItemIcon>
							<BlockIcon fontSize="small" color="error" />
						</ListItemIcon>
						<ListItemText>Disable member</ListItemText>
					</MenuItem>
				)}
			</Menu>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>Add member</DialogTitle>
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
		</Stack>
	);
}
