import { useEffect, useState, type FormEvent } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import EditIcon from "@mui/icons-material/Edit";
import {
	changeLeadStage,
	convertLead,
	getLead,
	LEAD_SOURCES,
	LEAD_STAGES,
	listCrmAdmins,
	updateLead,
	type ContactResponse,
	type CrmAdminResponse,
	type LeadResponse,
	type LeadStage,
} from "../api/crm";
import { PlatformApiError } from "../api/platformClient";
import { ActivityTimeline } from "./ActivityTimeline";
import { ContactsSection } from "./ContactsSection";
import { STAGE_COLOR, STAGE_LABEL, titleCase } from "./crmFormat";
import { TasksSection } from "./TasksSection";

function slugify(value: string): string {
	return value
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, "-")
		.replace(/^-+|-+$/g, "");
}

const EMPTY_CONVERT = { name: "", legalName: "", slug: "", adminEmail: "", adminPassword: "", adminFirstName: "", adminLastName: "" };

/** Working a lead: its people, history and next actions in one place, plus the hand-off into
 * onboarding ("Convert to Organisation") once it's WON. */
export function LeadDetailPage() {
	const { leadId } = useParams<{ leadId: string }>();
	const id = Number(leadId);

	const [lead, setLead] = useState<LeadResponse | null>(null);
	const [admins, setAdmins] = useState<CrmAdminResponse[]>([]);
	const [contacts, setContacts] = useState<ContactResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [editOpen, setEditOpen] = useState(false);
	const [editForm, setEditForm] = useState({ schoolName: "", source: "", estimatedStudentCount: "", ownerId: "", notes: "" });

	const [convertOpen, setConvertOpen] = useState(false);
	const [convertForm, setConvertForm] = useState(EMPTY_CONVERT);

	useEffect(() => {
		getLead(id)
			.then(setLead)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load lead"));
		listCrmAdmins().then(setAdmins).catch(() => {});
	}, [id]);

	function openEdit() {
		if (!lead) return;
		setEditForm({
			schoolName: lead.schoolName,
			source: lead.source,
			estimatedStudentCount: lead.estimatedStudentCount != null ? String(lead.estimatedStudentCount) : "",
			ownerId: lead.ownerId != null ? String(lead.ownerId) : "",
			notes: lead.notes ?? "",
		});
		setEditOpen(true);
	}

	async function handleEdit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			setLead(
				await updateLead(id, {
					schoolName: editForm.schoolName,
					source: editForm.source,
					estimatedStudentCount: editForm.estimatedStudentCount ? Number(editForm.estimatedStudentCount) : null,
					ownerId: editForm.ownerId ? Number(editForm.ownerId) : null,
					notes: editForm.notes,
				}),
			);
			setEditOpen(false);
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to update lead");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleStageChange(stage: LeadStage) {
		try {
			setLead(await changeLeadStage(id, stage));
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to change stage");
		}
	}

	function openConvert() {
		if (!lead) return;
		// Prefill what the lead already knows; the first contact is the natural org admin.
		const first = contacts[0];
		const [firstName = "", ...rest] = (first?.name ?? "").split(" ");
		setConvertForm({
			name: lead.schoolName,
			legalName: lead.schoolName,
			slug: slugify(lead.schoolName),
			adminEmail: first?.email ?? "",
			adminPassword: "",
			adminFirstName: firstName,
			adminLastName: rest.join(" "),
		});
		setConvertOpen(true);
	}

	async function handleConvert(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			setLead(await convertLead(id, convertForm));
			setConvertOpen(false);
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to convert lead");
		} finally {
			setSubmitting(false);
		}
	}

	function convertField(key: keyof typeof convertForm) {
		return (e: React.ChangeEvent<HTMLInputElement>) => setConvertForm((prev) => ({ ...prev, [key]: e.target.value }));
	}

	function editField(key: keyof typeof editForm) {
		return (e: React.ChangeEvent<HTMLInputElement>) => setEditForm((prev) => ({ ...prev, [key]: e.target.value }));
	}

	const canConvert = lead?.stage === "WON" && lead.convertedOrganisationId == null;
	const canSubmitConvert = Object.values(convertForm).every((value) => value.trim());

	return (
		<Stack spacing={3}>
			<Stack spacing={0.5}>
				<Link component={RouterLink} to="/platform/crm/leads" variant="overline" underline="hover">
					← Leads
				</Link>
				<Box sx={{ display: "flex", alignItems: "center", gap: 1.5, flexWrap: "wrap" }}>
					<Typography variant="h4" component="h1">
						{lead?.schoolName ?? "—"}
					</Typography>
					{lead && <Chip label={STAGE_LABEL[lead.stage]} size="small" color={STAGE_COLOR[lead.stage]} />}
					{lead?.convertedOrganisationId != null && <Chip label="Converted" size="small" color="success" />}
				</Box>
				{lead && (
					<Typography variant="body2" color="text.secondary">
						{titleCase(lead.source)} source
						{lead.estimatedStudentCount != null ? ` · ~${lead.estimatedStudentCount.toLocaleString("en-IN")} students` : ""}
						{` · ${lead.ownerName ? `owned by ${lead.ownerName}` : "unowned"}`}
					</Typography>
				)}
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{lead && (
				<Paper sx={{ p: 3 }}>
					<Stack spacing={2}>
						<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
							<Typography variant="h6">Pipeline</Typography>
							<Stack direction="row" spacing={1}>
								<Button size="small" startIcon={<EditIcon fontSize="small" />} onClick={openEdit}>
									Edit
								</Button>
								{canConvert && (
									<Button size="small" variant="contained" onClick={openConvert}>
										Convert to Organisation
									</Button>
								)}
							</Stack>
						</Stack>
						<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", gap: 1 }}>
							{LEAD_STAGES.map((stage) => (
								<Button
									key={stage}
									size="small"
									variant={lead.stage === stage ? "contained" : "outlined"}
									disabled={lead.stage === stage}
									onClick={() => handleStageChange(stage)}
								>
									{STAGE_LABEL[stage]}
								</Button>
							))}
						</Stack>
						{lead.convertedOrganisationId != null && (
							<Alert severity="success">
								Converted —{" "}
								<Link component={RouterLink} to={`/platform/organisations/${lead.convertedOrganisationId}`}>
									view the organisation
								</Link>
								.
							</Alert>
						)}
						{lead.stage === "WON" && lead.convertedOrganisationId == null && (
							<Alert severity="info">This lead is won — convert it to start onboarding the school.</Alert>
						)}
						{lead.notes && (
							<Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
								{lead.notes}
							</Typography>
						)}
					</Stack>
				</Paper>
			)}

			{lead && <ContactsSection owner={{ leadId: id }} onChange={setContacts} />}
			{lead && <TasksSection owner={{ leadId: id }} />}
			{/* Keyed on conversion so the system note the backend logs on convert shows up without a manual reload. */}
			{lead && <ActivityTimeline key={lead.convertedOrganisationId ?? "open"} owner={{ leadId: id }} />}

			<Dialog open={editOpen} onClose={() => setEditOpen(false)} component="form" onSubmit={handleEdit} fullWidth maxWidth="sm">
				<DialogTitle>Edit lead</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="School name" value={editForm.schoolName} onChange={editField("schoolName")} required fullWidth />
						<TextField select label="Source" value={editForm.source} onChange={editField("source")} required fullWidth>
							{LEAD_SOURCES.map((source) => (
								<MenuItem key={source} value={source}>
									{titleCase(source)}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Estimated students"
							type="number"
							value={editForm.estimatedStudentCount}
							onChange={editField("estimatedStudentCount")}
							slotProps={{ htmlInput: { min: 0 } }}
							fullWidth
						/>
						<TextField select label="Owner" value={editForm.ownerId} onChange={editField("ownerId")} fullWidth>
							{admins.map((admin) => (
								<MenuItem key={admin.id} value={admin.id}>
									{admin.name}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Notes" value={editForm.notes} onChange={editField("notes")} multiline minRows={2} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setEditOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !editForm.schoolName.trim()}>
						Save
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={convertOpen} onClose={() => setConvertOpen(false)} component="form" onSubmit={handleConvert} fullWidth maxWidth="sm">
				<DialogTitle>Convert to Organisation</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<Typography variant="body2" color="text.secondary">
							Creates the organisation and its first admin login. The lead's first contact becomes the organisation's primary
							contact. More details (address, academic year, plan) can be added from the organisation afterwards.
						</Typography>
						<TextField label="Organisation name" value={convertForm.name} onChange={convertField("name")} required fullWidth />
						<TextField label="Legal name" value={convertForm.legalName} onChange={convertField("legalName")} required fullWidth />
						<TextField label="Slug" value={convertForm.slug} onChange={convertField("slug")} required fullWidth />
						<Stack direction="row" spacing={2}>
							<TextField label="Admin first name" value={convertForm.adminFirstName} onChange={convertField("adminFirstName")} required fullWidth />
							<TextField label="Admin last name" value={convertForm.adminLastName} onChange={convertField("adminLastName")} required fullWidth />
						</Stack>
						<TextField label="Admin email" type="email" value={convertForm.adminEmail} onChange={convertField("adminEmail")} required fullWidth />
						<TextField
							label="Admin password"
							type="password"
							value={convertForm.adminPassword}
							onChange={convertField("adminPassword")}
							required
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setConvertOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !canSubmitConvert}>
						Convert
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
