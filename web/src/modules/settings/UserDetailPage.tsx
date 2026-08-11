import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
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
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { grantMembership, listMemberships, revokeMembership, type MembershipResponse } from "../../api/memberships";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listRoles, type RoleResponse } from "../../api/roles";
import { getUser, type UserResponse } from "../../api/users";

/** No GET-by-id exists for OrganisationMembership filtered by user, so this composes
 * the user's own memberships client-side from the full list - same tradeoff as
 * RouteDetailPage/MarksEntryPage at this data scale. This is also where "grant an
 * existing person another role" actually lives, rather than in UsersPanel's own
 * "Grant access" dialog (which is deliberately kept to just "onboard a brand new
 * person") - the userId is already fixed from the page, so only a Person needs picking. */
export function UserDetailPage() {
	const { userId } = useParams<{ userId: string }>();
	const navigate = useNavigate();

	const [user, setUser] = useState<UserResponse | null>(null);
	const [memberships, setMemberships] = useState<MembershipResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [personId, setPersonId] = useState<number | "">("");
	const [roleId, setRoleId] = useState<number | "">("");
	const [campusId, setCampusId] = useState<number | "">("");
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		if (!userId) return;
		getUser(Number(userId))
			.then(setUser)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load user"))
			.finally(() => setLoading(false));
		listPersons().then(setPersons).catch(() => undefined);
		listRoles().then(setRoles).catch(() => undefined);
		listCampuses().then(setCampuses).catch(() => undefined);
		refreshMemberships();
	}, [userId]);

	function refreshMemberships() {
		if (!userId) return;
		listMemberships()
			.then((all) => setMemberships(all.filter((m) => m.userId === Number(userId))))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load memberships"));
	}

	async function handleGrant(event: FormEvent) {
		event.preventDefault();
		if (!userId || personId === "" || roleId === "") return;
		setSubmitting(true);
		try {
			await grantMembership({ userId: Number(userId), personId, roleId, campusId: campusId === "" ? null : campusId });
			setPersonId("");
			setRoleId("");
			setCampusId("");
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
			setError(err instanceof ApiError ? err.message : "Failed to revoke membership");
		}
	}

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	return (
		<Stack spacing={2} sx={{ maxWidth: 900 }}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/settings")}>
					Back to settings
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				{user?.email ?? `User #${userId}`}
			</Typography>
			{user && (
				<Typography variant="body2" color="text.secondary">
					{user.phone ?? "No phone on file"} · <Chip label={user.status} size="small" />
				</Typography>
			)}

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Roles</Typography>
						<Can anyOf={["MEMBERSHIP_MANAGE"]}>
							<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
								Grant role
							</Button>
						</Can>
					</Box>

					{memberships.length === 0 && <Alert severity="info">This user holds no roles yet.</Alert>}

					{memberships.length > 0 && (
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
										<TableRow key={membership.id}>
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
														<Button size="small" onClick={() => handleRevoke(membership.id)}>
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
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Grant
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
