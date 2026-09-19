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
import { listOrganisations, type OrganisationResponse } from "./api/organisations";
import { PlatformApiError } from "./api/platformClient";
import { createSupportTicket, listSupportTickets, type SupportTicketResponse } from "./api/supportTickets";

const STATUS_COLOR: Record<string, "warning" | "info" | "success" | "default"> = {
	OPEN: "warning",
	IN_PROGRESS: "info",
	RESOLVED: "success",
	CLOSED: "default",
};

const PRIORITY_COLOR: Record<string, "default" | "info" | "warning" | "error"> = {
	LOW: "default",
	MEDIUM: "info",
	HIGH: "warning",
	URGENT: "error",
};

const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const EMPTY_FORM = { organisationId: "", subject: "", description: "", priority: "MEDIUM", requesterEmail: "" };

/** MVP list/create for the Support/Issues tracker (#277) - a single description field
 * stands in for a message thread, see SupportTicket's javadoc for the full-thread
 * follow-up this is deliberately not building yet. */
export function SupportTicketsPage() {
	const navigate = useNavigate();
	const [tickets, setTickets] = useState<SupportTicketResponse[] | null>(null);
	const [organisations, setOrganisations] = useState<OrganisationResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState(EMPTY_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listSupportTickets()
			.then(setTickets)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load support tickets"));
	}

	useEffect(() => {
		refresh();
		listOrganisations().then(setOrganisations).catch(() => {});
	}, []);

	const orgName = (id: number) => organisations.find((org) => org.id === id)?.name ?? `#${id}`;

	function field(key: keyof typeof form) {
		return (e: React.ChangeEvent<HTMLInputElement>) => setForm((prev) => ({ ...prev, [key]: e.target.value }));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createSupportTicket({
				organisationId: Number(form.organisationId),
				subject: form.subject,
				description: form.description || null,
				priority: form.priority,
				requesterEmail: form.requesterEmail,
			});
			setDialogOpen(false);
			setForm(EMPTY_FORM);
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to create support ticket");
		} finally {
			setSubmitting(false);
		}
	}

	const canSubmit = form.organisationId && form.subject && form.requesterEmail;

	return (
		<Stack spacing={2}>
			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end" }}>
				<Stack spacing={0.5}>
					<Typography variant="overline" color="text.secondary">
						Support
					</Typography>
					<Typography variant="h4" component="h1">
						Support / Issues
					</Typography>
				</Stack>
				<Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)} disabled={organisations.length === 0}>
					New ticket
				</Button>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			{tickets && tickets.length === 0 && <Alert severity="info">No support tickets yet.</Alert>}

			{tickets && tickets.length > 0 && (
				<TableContainer component={Paper}>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Subject</TableCell>
								<TableCell>Organisation</TableCell>
								<TableCell>Priority</TableCell>
								<TableCell>Status</TableCell>
								<TableCell>Assignee</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{tickets.map((ticket) => (
								<TableRow key={ticket.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/platform/support/${ticket.id}`)}>
									<TableCell>{ticket.subject}</TableCell>
									<TableCell>{orgName(ticket.organisationId)}</TableCell>
									<TableCell>
										<Chip label={ticket.priority} size="small" color={PRIORITY_COLOR[ticket.priority] ?? "default"} />
									</TableCell>
									<TableCell>
										<Chip label={ticket.status} size="small" color={STATUS_COLOR[ticket.status] ?? "default"} />
									</TableCell>
									<TableCell>{ticket.assigneeEmail ?? "Unassigned"}</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			)}

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>New support ticket</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Organisation" value={form.organisationId} onChange={field("organisationId")} required fullWidth>
							{organisations.map((org) => (
								<MenuItem key={org.id} value={org.id}>
									{org.name}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Subject" value={form.subject} onChange={field("subject")} required fullWidth />
						<TextField
							label="Description"
							value={form.description}
							onChange={field("description")}
							multiline
							minRows={3}
							fullWidth
						/>
						<TextField select label="Priority" value={form.priority} onChange={field("priority")} required fullWidth>
							{PRIORITIES.map((priority) => (
								<MenuItem key={priority} value={priority}>
									{priority}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Requester email"
							type="email"
							value={form.requesterEmail}
							onChange={field("requesterEmail")}
							required
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !canSubmit}>
						Create
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
