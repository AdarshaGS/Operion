import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import FormControlLabel from "@mui/material/FormControlLabel";
import IconButton from "@mui/material/IconButton";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import EditIcon from "@mui/icons-material/Edit";
import KeyIcon from "@mui/icons-material/Key";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import { createOrGetGuardian, getGuardian, grantPortalAccess, type PortalInviteResponse } from "../../api/guardians";
import { createPerson, listPersons, type PersonResponse } from "../../api/persons";
import {
	GUARDIAN_RELATIONSHIP_TYPES,
	linkGuardian,
	listGuardiansForStudent,
	updateGuardianRelationship,
	type GuardianRelationshipType,
	type StudentGuardianResponse,
} from "../../api/studentGuardians";

interface RelationshipFormState {
	relationshipType: GuardianRelationshipType;
	primaryGuardian: boolean;
	emergencyContact: boolean;
	canPickup: boolean;
	canReceiveCommunication: boolean;
	contactPriority: string;
}

const EMPTY_RELATIONSHIP: RelationshipFormState = {
	relationshipType: "FATHER",
	primaryGuardian: false,
	emergencyContact: false,
	canPickup: true,
	canReceiveCommunication: true,
	contactPriority: "1",
};

interface GuardianRow {
	link: StudentGuardianResponse;
	person: PersonResponse | null;
}

export function StudentGuardiansPanel({ studentId }: { studentId: number }) {
	const [rows, setRows] = useState<GuardianRow[] | null>(null);
	const [error, setError] = useState<string | null>(null);

	const [addOpen, setAddOpen] = useState(false);
	const [addTab, setAddTab] = useState<"new" | "existing">("new");
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [newFirstName, setNewFirstName] = useState("");
	const [newLastName, setNewLastName] = useState("");
	const [newPhone, setNewPhone] = useState("");
	const [newEmail, setNewEmail] = useState("");
	const [existingPersonId, setExistingPersonId] = useState("");
	const [occupation, setOccupation] = useState("");
	const [relationship, setRelationship] = useState<RelationshipFormState>(EMPTY_RELATIONSHIP);
	const [submitting, setSubmitting] = useState(false);

	const [editTarget, setEditTarget] = useState<StudentGuardianResponse | null>(null);
	const [editForm, setEditForm] = useState<RelationshipFormState>(EMPTY_RELATIONSHIP);

	const [invite, setInvite] = useState<PortalInviteResponse | null>(null);
	const [inviteError, setInviteError] = useState<string | null>(null);

	// StudentGuardianResponse only carries guardianId, not personId - resolve each link's
	// display name via its Guardian record, then match against a fresh Person list.
	async function loadGuardians(personList: PersonResponse[]): Promise<GuardianRow[]> {
		const links = await listGuardiansForStudent(studentId);
		return Promise.all(
			links.map(async (link) => {
				const guardian = await getGuardian(link.guardianId).catch(() => null);
				const person = guardian ? (personList.find((p) => p.id === guardian.personId) ?? null) : null;
				return { link, person };
			}),
		);
	}

	function refresh() {
		listPersons()
			.then((personList) => {
				setPersons(personList);
				return loadGuardians(personList);
			})
			.then(setRows)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load guardians"));
	}

	useEffect(() => {
		let cancelled = false;
		listPersons()
			.then(async (personList) => {
				if (cancelled) return;
				setPersons(personList);
				const rows = await loadGuardians(personList);
				if (!cancelled) setRows(rows);
			})
			.catch((err) => {
				if (cancelled) return;
				setError(err instanceof ApiError ? err.message : "Failed to load guardians");
			});
		return () => {
			cancelled = true;
		};
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [studentId]);

	function resetAddForm() {
		setAddTab("new");
		setNewFirstName("");
		setNewLastName("");
		setNewPhone("");
		setNewEmail("");
		setExistingPersonId("");
		setOccupation("");
		setRelationship(EMPTY_RELATIONSHIP);
	}

	async function handleAdd(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSubmitting(true);
		try {
			const personId =
				addTab === "existing"
					? Number(existingPersonId)
					: (
							await createPerson({
								firstName: newFirstName,
								lastName: newLastName,
								phone: newPhone || null,
								email: newEmail || null,
							})
						).id;

			const guardian = await createOrGetGuardian({ personId, occupation: occupation || null });
			await linkGuardian(studentId, {
				guardianId: guardian.id,
				relationshipType: relationship.relationshipType,
				primaryGuardian: relationship.primaryGuardian,
				emergencyContact: relationship.emergencyContact,
				canPickup: relationship.canPickup,
				canReceiveCommunication: relationship.canReceiveCommunication,
				contactPriority: Number(relationship.contactPriority) || 0,
			});

			setAddOpen(false);
			resetAddForm();
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add guardian");
		} finally {
			setSubmitting(false);
		}
	}

	function openEdit(link: StudentGuardianResponse) {
		setEditTarget(link);
		setEditForm({
			relationshipType: link.relationshipType,
			primaryGuardian: link.primaryGuardian,
			emergencyContact: link.emergencyContact,
			canPickup: link.canPickup,
			canReceiveCommunication: link.canReceiveCommunication,
			contactPriority: String(link.contactPriority),
		});
	}

	async function handleEditSave(event: FormEvent) {
		event.preventDefault();
		if (!editTarget) return;
		setError(null);
		setSubmitting(true);
		try {
			await updateGuardianRelationship(studentId, editTarget.id, {
				relationshipType: editForm.relationshipType,
				primaryGuardian: editForm.primaryGuardian,
				emergencyContact: editForm.emergencyContact,
				canPickup: editForm.canPickup,
				canReceiveCommunication: editForm.canReceiveCommunication,
				contactPriority: Number(editForm.contactPriority) || 0,
			});
			setEditTarget(null);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update relationship");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleGrantPortalAccess(guardianId: number) {
		setInviteError(null);
		try {
			setInvite(await grantPortalAccess(guardianId));
		} catch (err) {
			setInviteError(err instanceof ApiError ? err.message : "Failed to issue portal invite");
		}
	}

	const claimLinkHint = invite ? `${window.location.origin}/claim-invite?token=${encodeURIComponent(invite.claimToken)}` : "";

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Guardians</Typography>
					<Can anyOf={["GUARDIAN_MANAGE"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setAddOpen(true)}>
							Add guardian
						</Button>
					</Can>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{!rows && !error && (
					<Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
						<CircularProgress size={24} />
					</Box>
				)}

				{rows && rows.length === 0 && <Alert severity="info">No guardians linked yet.</Alert>}

				{rows && rows.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Name</TableCell>
									<TableCell>Relationship</TableCell>
									<TableCell>Flags</TableCell>
									<TableCell>Priority</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{rows.map(({ link, person }) => (
									<TableRow key={link.id}>
										<TableCell>{person ? `${person.firstName} ${person.lastName}` : `Guardian #${link.guardianId}`}</TableCell>
										<TableCell>{link.relationshipType}</TableCell>
										<TableCell>
											<Stack direction="row" spacing={0.5} sx={{ flexWrap: "wrap" }}>
												{link.primaryGuardian && <Chip label="Primary" size="small" color="primary" />}
												{link.emergencyContact && <Chip label="Emergency" size="small" />}
												{link.canPickup && <Chip label="Pickup OK" size="small" variant="outlined" />}
												{link.canReceiveCommunication && <Chip label="Comms" size="small" variant="outlined" />}
											</Stack>
										</TableCell>
										<TableCell>{link.contactPriority}</TableCell>
										<TableCell align="right">
											<Can anyOf={["GUARDIAN_MANAGE"]}>
												<Tooltip title="Edit relationship">
													<IconButton size="small" onClick={() => openEdit(link)}>
														<EditIcon fontSize="small" />
													</IconButton>
												</Tooltip>
												<Tooltip title="Grant portal access">
													<IconButton size="small" onClick={() => handleGrantPortalAccess(link.guardianId)}>
														<KeyIcon fontSize="small" />
													</IconButton>
												</Tooltip>
											</Can>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			{/* Add guardian */}
			<Dialog open={addOpen} onClose={() => setAddOpen(false)} component="form" onSubmit={handleAdd} fullWidth maxWidth="sm">
				<DialogTitle>Add guardian</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<Tabs value={addTab} onChange={(_, value) => setAddTab(value)}>
							<Tab label="New person" value="new" />
							<Tab label="Existing person" value="existing" />
						</Tabs>

						{addTab === "new" ? (
							<Stack spacing={2}>
								<Box sx={{ display: "flex", gap: 2 }}>
									<TextField label="First name" value={newFirstName} onChange={(e) => setNewFirstName(e.target.value)} required fullWidth />
									<TextField label="Last name" value={newLastName} onChange={(e) => setNewLastName(e.target.value)} required fullWidth />
								</Box>
								<Box sx={{ display: "flex", gap: 2 }}>
									<TextField label="Phone" value={newPhone} onChange={(e) => setNewPhone(e.target.value)} fullWidth />
									<TextField label="Email" type="email" value={newEmail} onChange={(e) => setNewEmail(e.target.value)} fullWidth />
								</Box>
							</Stack>
						) : (
							<TextField
								select
								label="Person"
								value={existingPersonId}
								onChange={(e) => setExistingPersonId(e.target.value)}
								required
								fullWidth
								helperText="Reuses this person's existing Guardian record if they already guardian another student."
							>
								{persons.map((p) => (
									<MenuItem key={p.id} value={p.id}>
										{p.firstName} {p.lastName} {p.email ? `— ${p.email}` : ""}
									</MenuItem>
								))}
							</TextField>
						)}

						<TextField label="Occupation" value={occupation} onChange={(e) => setOccupation(e.target.value)} fullWidth />

						<Divider />
						<Typography variant="subtitle2">Relationship to this student</Typography>
						<RelationshipFields value={relationship} onChange={setRelationship} />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAddOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			{/* Edit relationship */}
			<Dialog open={editTarget !== null} onClose={() => setEditTarget(null)} component="form" onSubmit={handleEditSave} fullWidth maxWidth="xs">
				<DialogTitle>Edit relationship</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<RelationshipFields value={editForm} onChange={setEditForm} />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setEditTarget(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>

			{/* Portal invite (shown once) */}
			<Dialog open={invite !== null} onClose={() => setInvite(null)} fullWidth maxWidth="sm">
				<DialogTitle>Guardian portal invite</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{inviteError && <Alert severity="error">{inviteError}</Alert>}
						<Alert severity="warning">
							This token is shown once and never stored - copy it now. Share it with the guardian along with your organisation's login
							slug; they'll enter both on the sign-up page to set their own password.
						</Alert>
						{invite && (
							<>
								<TextField label="Claim link" value={claimLinkHint} fullWidth slotProps={{ input: { readOnly: true } }} />
								<Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
									<TextField label="Raw token" value={invite.claimToken} fullWidth slotProps={{ input: { readOnly: true } }} />
									<Tooltip title="Copy token">
										<IconButton onClick={() => navigator.clipboard.writeText(invite.claimToken)}>
											<ContentCopyIcon fontSize="small" />
										</IconButton>
									</Tooltip>
								</Box>
								<Typography variant="caption" color="text.secondary">
									Expires {new Date(invite.expiresAt).toLocaleString()}
								</Typography>
							</>
						)}
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setInvite(null)}>Close</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}

function RelationshipFields({
	value,
	onChange,
}: {
	value: RelationshipFormState;
	onChange: (next: RelationshipFormState) => void;
}) {
	return (
		<>
			<Box sx={{ display: "flex", gap: 2 }}>
				<TextField
					select
					label="Relationship"
					value={value.relationshipType}
					onChange={(e) => onChange({ ...value, relationshipType: e.target.value as GuardianRelationshipType })}
					required
					fullWidth
				>
					{GUARDIAN_RELATIONSHIP_TYPES.map((type) => (
						<MenuItem key={type} value={type}>
							{type.replaceAll("_", " ")}
						</MenuItem>
					))}
				</TextField>
				<TextField
					label="Contact priority"
					type="number"
					value={value.contactPriority}
					onChange={(e) => onChange({ ...value, contactPriority: e.target.value })}
					fullWidth
				/>
			</Box>
			<Box sx={{ display: "flex", flexWrap: "wrap" }}>
				<FormControlLabel
					control={<Checkbox checked={value.primaryGuardian} onChange={(e) => onChange({ ...value, primaryGuardian: e.target.checked })} />}
					label="Primary guardian"
				/>
				<FormControlLabel
					control={<Checkbox checked={value.emergencyContact} onChange={(e) => onChange({ ...value, emergencyContact: e.target.checked })} />}
					label="Emergency contact"
				/>
				<FormControlLabel
					control={<Checkbox checked={value.canPickup} onChange={(e) => onChange({ ...value, canPickup: e.target.checked })} />}
					label="Can pick up"
				/>
				<FormControlLabel
					control={
						<Checkbox
							checked={value.canReceiveCommunication}
							onChange={(e) => onChange({ ...value, canReceiveCommunication: e.target.checked })}
						/>
					}
					label="Receives communication"
				/>
			</Box>
		</>
	);
}
