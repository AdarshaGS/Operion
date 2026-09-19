import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { getOrganisation, type OrganisationResponse } from "./api/organisations";
import { PlatformApiError } from "./api/platformClient";
import {
	assignSupportTicket,
	changeSupportTicketStatus,
	getSupportTicket,
	type SupportTicketResponse,
} from "./api/supportTickets";

const STATUS_COLOR: Record<string, "warning" | "info" | "success" | "default"> = {
	OPEN: "warning",
	IN_PROGRESS: "info",
	RESOLVED: "success",
	CLOSED: "default",
};

const STATUSES = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"];

export function SupportTicketDetailPage() {
	const { ticketId } = useParams<{ ticketId: string }>();
	const id = Number(ticketId);

	const [ticket, setTicket] = useState<SupportTicketResponse | null>(null);
	const [organisation, setOrganisation] = useState<OrganisationResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [assigneeInput, setAssigneeInput] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		getSupportTicket(id)
			.then((t) => {
				setTicket(t);
				setAssigneeInput(t.assigneeEmail ?? "");
				getOrganisation(t.organisationId).then(setOrganisation).catch(() => {});
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load support ticket"));
	}

	useEffect(() => {
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [id]);

	async function handleStatusChange(status: string) {
		try {
			const updated = await changeSupportTicketStatus(id, status);
			setTicket(updated);
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to change status");
		}
	}

	async function handleAssign(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			const updated = await assignSupportTicket(id, assigneeInput);
			setTicket(updated);
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to assign ticket");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Stack spacing={3}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Support ticket
				</Typography>
				<Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
					<Typography variant="h4" component="h1">
						{ticket?.subject ?? "—"}
					</Typography>
					{ticket && <Chip label={ticket.status} size="small" color={STATUS_COLOR[ticket.status] ?? "default"} />}
				</Box>
				<Typography variant="body2" color="text.secondary">
					{organisation?.name ?? `Organisation #${ticket?.organisationId ?? ""}`} · {ticket?.priority} priority · raised by{" "}
					{ticket?.requesterEmail}
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{ticket && (
				<Paper sx={{ p: 3 }}>
					<Stack spacing={2}>
						<Typography variant="h6">Description</Typography>
						<Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
							{ticket.description || "No description provided."}
						</Typography>
					</Stack>
				</Paper>
			)}

			{ticket && (
				<Paper sx={{ p: 3 }}>
					<Stack spacing={2}>
						<Typography variant="h6">Status</Typography>
						<Stack direction="row" spacing={1}>
							{STATUSES.map((status) => (
								<Button
									key={status}
									size="small"
									variant={ticket.status === status ? "contained" : "outlined"}
									onClick={() => handleStatusChange(status)}
									disabled={ticket.status === status}
								>
									{status}
								</Button>
							))}
						</Stack>
					</Stack>
				</Paper>
			)}

			{ticket && (
				<Paper sx={{ p: 3 }} component="form" onSubmit={handleAssign}>
					<Stack spacing={2}>
						<Typography variant="h6">Assignee</Typography>
						<Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
							<TextField
								label="Assignee email"
								type="email"
								value={assigneeInput}
								onChange={(e) => setAssigneeInput(e.target.value)}
								size="small"
								fullWidth
							/>
							<Button type="submit" variant="contained" disabled={submitting}>
								Save
							</Button>
						</Stack>
					</Stack>
				</Paper>
			)}
		</Stack>
	);
}
