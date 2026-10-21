import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import { createContact, deleteContact, listContacts, updateContact, type ContactInput, type ContactResponse, type CrmOwner } from "../api/crm";
import { PlatformApiError } from "../api/platformClient";
import ContactsIcon from "@mui/icons-material/Contacts";
import { SectionCard } from "../../components/DashboardKit";

const EMPTY_FORM: ContactInput = { name: "", role: "", email: "", phone: "" };

interface ContactsSectionProps {
	owner: CrmOwner;
	/** Called after the list loads or changes, so a parent (e.g. lead conversion) can prefill from it. */
	onChange?: (contacts: ContactResponse[]) => void;
}

/** Named people on a lead or an organisation (decision-maker, admin, finance contact) -
 * shared by LeadDetailPage and OrganisationDetailPage, scoped by `owner`. */
export function ContactsSection({ owner, onChange }: ContactsSectionProps) {
	const ownerKey = "leadId" in owner ? `lead-${owner.leadId}` : `org-${owner.organisationId}`;
	const [contacts, setContacts] = useState<ContactResponse[] | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [editing, setEditing] = useState<ContactResponse | null>(null);
	const [form, setForm] = useState(EMPTY_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listContacts(owner)
			.then((list) => {
				setContacts(list);
				onChange?.(list);
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load contacts"));
	}

	useEffect(() => {
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [ownerKey]);

	function openCreate() {
		setEditing(null);
		setForm(EMPTY_FORM);
		setDialogOpen(true);
	}

	function openEdit(contact: ContactResponse) {
		setEditing(contact);
		setForm({ name: contact.name, role: contact.role ?? "", email: contact.email ?? "", phone: contact.phone ?? "" });
		setDialogOpen(true);
	}

	function field(key: keyof ContactInput) {
		return (e: React.ChangeEvent<HTMLInputElement>) => setForm((prev) => ({ ...prev, [key]: e.target.value }));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			if (editing) {
				await updateContact(editing.id, form);
			} else {
				await createContact(owner, form);
			}
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to save contact");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleDelete(contact: ContactResponse) {
		if (!window.confirm(`Remove ${contact.name}?`)) return;
		try {
			await deleteContact(contact.id);
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to remove contact");
		}
	}

	return (
		<SectionCard
			title="Contacts"
			icon={<ContactsIcon fontSize="small" />}
			tint="violet"
			headerExtra={
				<Button size="small" variant="outlined" startIcon={<AddIcon />} onClick={openCreate}>
					Add contact
				</Button>
			}
		>
			<Stack spacing={2}>
				{error && <Alert severity="error">{error}</Alert>}
				{contacts && contacts.length === 0 && <Alert severity="info">No contacts yet.</Alert>}

				{contacts && contacts.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Name</TableCell>
									<TableCell>Role</TableCell>
									<TableCell>Email</TableCell>
									<TableCell>Phone</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{contacts.map((contact) => (
									<TableRow key={contact.id}>
										<TableCell>{contact.name}</TableCell>
										<TableCell>{contact.role ?? "—"}</TableCell>
										<TableCell>{contact.email ?? "—"}</TableCell>
										<TableCell>{contact.phone ?? "—"}</TableCell>
										<TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
											<IconButton size="small" aria-label={`Edit ${contact.name}`} onClick={() => openEdit(contact)}>
												<EditIcon fontSize="small" />
											</IconButton>
											<IconButton size="small" aria-label={`Remove ${contact.name}`} onClick={() => handleDelete(contact)}>
												<DeleteIcon fontSize="small" />
											</IconButton>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>{editing ? "Edit contact" : "Add contact"}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Name" value={form.name} onChange={field("name")} required fullWidth />
						<TextField label="Role / title" value={form.role} onChange={field("role")} fullWidth />
						<TextField label="Email" type="email" value={form.email} onChange={field("email")} fullWidth />
						<TextField label="Phone" value={form.phone} onChange={field("phone")} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !form.name.trim()}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</SectionCard>
	);
}
