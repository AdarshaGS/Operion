import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
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
import { Can } from "../../auth/Can";
import { StaffInviteDialog } from "../../components/StaffInviteDialog";
import { ApiError } from "../../api/client";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { grantMembership, listMemberships, revokeMembership, type MembershipResponse } from "../../api/memberships";
import { createPerson } from "../../api/persons";
import { listRoles, type RoleResponse } from "../../api/roles";
import { inviteUser, type StaffInviteResponse } from "../../api/users";

/** One "Users" section rather than a separate plain login list and a separate "people
 * with access" list - this table is memberships (one row per person+role, same as
 * before), each row links through to UserDetailPage for that row's user. Add user
 * creates a brand-new Person + User "login shell" + Membership in one sequential flow,
 * same pattern as StudentCreatePage/StaffCreatePage, but via the invite flow (see
 * StaffInviteService) rather than an admin setting the password directly - granting an
 * additional role to someone who already has a Person/User record lives on
 * UserDetailPage instead (reachable by clicking a row here), since the userId is already
 * fixed from that page and only a Person needs picking there. */
export function UsersPanel() {
	const navigate = useNavigate();
	const [memberships, setMemberships] = useState<MembershipResponse[]>([]);
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [submitting, setSubmitting] = useState(false);

	const [firstName, setFirstName] = useState("");
	const [lastName, setLastName] = useState("");
	const [email, setEmail] = useState("");
	const [phone, setPhone] = useState("");
	const [roleId, setRoleId] = useState<number | "">("");
	const [campusId, setCampusId] = useState<number | "">("");

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
	}, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		if (roleId === "") return;
		setSubmitting(true);
		try {
			const person = await createPerson({ firstName, lastName });
			const issuedInvite = await inviteUser({ email, phone: phone || null });
			await grantMembership({ userId: issuedInvite.userId, personId: person.id, roleId, campusId: campusId === "" ? null : campusId });
			setFirstName("");
			setLastName("");
			setEmail("");
			setPhone("");
			setRoleId("");
			setCampusId("");
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
									<TableCell>{membership.roleName}</TableCell>
									<TableCell>
										{membership.campusId === null
											? "Org-wide"
											: (campuses.find((c) => c.id === membership.campusId)?.name ?? membership.campusId)}
									</TableCell>
									<TableCell>
										<Chip label={membership.status} size="small" />
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

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add user</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="First name" value={firstName} onChange={(e) => setFirstName(e.target.value)} required autoFocus fullWidth />
						<TextField label="Last name" value={lastName} onChange={(e) => setLastName(e.target.value)} required fullWidth />
						<TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required fullWidth />
						<TextField label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} fullWidth />
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
