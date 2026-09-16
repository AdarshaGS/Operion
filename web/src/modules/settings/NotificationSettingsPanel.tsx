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
import Link from "@mui/material/Link";
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
import { ApiError } from "../../api/client";
import {
	createNotificationTemplate,
	listNotificationTemplates,
	type NotificationChannel,
	type NotificationTemplateResponse,
} from "../../api/notificationTemplates";

const CHANNELS_WITHOUT_SUBJECT: NotificationChannel[] = ["SMS", "WHATSAPP", "IN_APP"];

interface TemplateFormState {
	code: string;
	channel: NotificationChannel;
	subjectTemplate: string;
	bodyTemplate: string;
}

const EMPTY_TEMPLATE_FORM: TemplateFormState = { code: "", channel: "EMAIL", subjectTemplate: "", bodyTemplate: "" };

/** Notification settings (#144): the one piece of "channel/provider config, sender
 * identity, message templates, who may send" that had no UI anywhere - message templates.
 * The other three already live elsewhere rather than being duplicated here:
 * - Channel/provider config + sender identity (SMTP/SMS/WhatsApp credentials, from-name/
 *   address/number) is the existing generic Integrations & API panel
 *   (ExternalServicesPanel) - every channel's credentials are just another entry in that
 *   same BYOK mechanism (see com.operion.integration), not a separate config surface.
 * - Who may send is ANNOUNCEMENT_PUBLISH (Roles), the existing precedent this ticket's
 *   own text flagged as a decision rather than a given - no new per-channel permission
 *   was warranted since sending is channel-agnostic at the announcement level. */
export function NotificationSettingsPanel() {
	const navigate = useNavigate();
	const [templates, setTemplates] = useState<NotificationTemplateResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState<TemplateFormState>(EMPTY_TEMPLATE_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listNotificationTemplates()
			.then(setTemplates)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load notification templates"));
	}

	useEffect(refresh, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createNotificationTemplate({
				code: form.code,
				channel: form.channel,
				subjectTemplate: CHANNELS_WITHOUT_SUBJECT.includes(form.channel) ? null : form.subjectTemplate || null,
				bodyTemplate: form.bodyTemplate,
			});
			setForm(EMPTY_TEMPLATE_FORM);
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create notification template");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Stack spacing={3}>
			<Alert severity="info">
				Channel credentials (email, SMS, WhatsApp) and sender identity are configured under{" "}
				<Link component="button" variant="body2" onClick={() => navigate("/settings/integrations")}>
					Integrations &amp; API
				</Link>
				. Who may send announcements is governed by the Publish announcements permission under{" "}
				<Link component="button" variant="body2" onClick={() => navigate("/settings/roles")}>
					Roles
				</Link>
				.
			</Alert>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Message templates</Typography>
						<Can anyOf={["NOTIFICATION_TEMPLATE_MANAGE"]}>
							<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
								Add template
							</Button>
						</Can>
					</Box>

					{error && <Alert severity="error">{error}</Alert>}
					{templates.length === 0 && !error && (
						<Typography variant="body2" color="text.secondary">
							No system-generated notification templates yet.
						</Typography>
					)}

					{templates.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Code</TableCell>
										<TableCell>Channel</TableCell>
										<TableCell>Subject</TableCell>
										<TableCell>Body</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{templates.map((template) => (
										<TableRow key={template.id}>
											<TableCell>{template.code}</TableCell>
											<TableCell>
												<Chip label={template.channel} size="small" />
											</TableCell>
											<TableCell>{template.subjectTemplate ?? "—"}</TableCell>
											<TableCell sx={{ maxWidth: 360, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
												{template.bodyTemplate}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>Add notification template</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Code"
							placeholder="FEE_DUE_REMINDER"
							value={form.code}
							onChange={(e) => setForm((prev) => ({ ...prev, code: e.target.value }))}
							required
							autoFocus
							fullWidth
						/>
						<TextField
							select
							label="Channel"
							value={form.channel}
							onChange={(e) => setForm((prev) => ({ ...prev, channel: e.target.value as NotificationChannel }))}
							fullWidth
						>
							<MenuItem value="EMAIL">Email</MenuItem>
							<MenuItem value="SMS">SMS</MenuItem>
							<MenuItem value="WHATSAPP">WhatsApp</MenuItem>
							<MenuItem value="IN_APP">In-app</MenuItem>
						</TextField>
						{!CHANNELS_WITHOUT_SUBJECT.includes(form.channel) && (
							<TextField
								label="Subject"
								value={form.subjectTemplate}
								onChange={(e) => setForm((prev) => ({ ...prev, subjectTemplate: e.target.value }))}
								fullWidth
							/>
						)}
						<TextField
							label="Body"
							value={form.bodyTemplate}
							onChange={(e) => setForm((prev) => ({ ...prev, bodyTemplate: e.target.value }))}
							required
							multiline
							minRows={4}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
