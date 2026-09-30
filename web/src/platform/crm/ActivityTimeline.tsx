import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ACTIVITY_TYPES, listActivities, logActivity, type ActivityResponse, type CrmOwner } from "../api/crm";
import { PlatformApiError } from "../api/platformClient";
import { formatDateTime, titleCase } from "./crmFormat";

const TYPE_COLOR: Record<string, "default" | "info" | "secondary" | "warning"> = {
	CALL: "info",
	EMAIL: "secondary",
	MEETING: "warning",
	NOTE: "default",
};

/** Insert-only relationship history (call/email/meeting/note) for a lead or organisation -
 * there's deliberately no edit/delete, the backend treats it as an audit trail. */
export function ActivityTimeline({ owner }: { owner: CrmOwner }) {
	const ownerKey = "leadId" in owner ? `lead-${owner.leadId}` : `org-${owner.organisationId}`;
	const [activities, setActivities] = useState<ActivityResponse[] | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [type, setType] = useState<string>("NOTE");
	const [body, setBody] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listActivities(owner)
			.then(setActivities)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load activity"));
	}

	useEffect(() => {
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [ownerKey]);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await logActivity(owner, type, body);
			setBody("");
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to log activity");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Activity</Typography>

				<Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} component="form" onSubmit={handleSubmit} sx={{ alignItems: "flex-start" }}>
					<TextField select label="Type" value={type} onChange={(e) => setType(e.target.value)} size="small" sx={{ minWidth: 130 }}>
						{ACTIVITY_TYPES.map((t) => (
							<MenuItem key={t} value={t}>
								{titleCase(t)}
							</MenuItem>
						))}
					</TextField>
					<TextField
						label="What happened?"
						value={body}
						onChange={(e) => setBody(e.target.value)}
						size="small"
						multiline
						minRows={1}
						fullWidth
					/>
					<Button type="submit" variant="contained" disabled={submitting || !body.trim()}>
						Log
					</Button>
				</Stack>

				{error && <Alert severity="error">{error}</Alert>}
				{activities && activities.length === 0 && <Alert severity="info">Nothing logged yet.</Alert>}

				<Stack spacing={1.5}>
					{activities?.map((activity) => (
						<Box key={activity.id} sx={{ display: "flex", gap: 1.5, alignItems: "flex-start" }}>
							<Chip label={titleCase(activity.type)} size="small" color={TYPE_COLOR[activity.type] ?? "default"} sx={{ mt: 0.25 }} />
							<Box sx={{ minWidth: 0 }}>
								<Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
									{activity.body}
								</Typography>
								<Typography variant="caption" color="text.secondary">
									{formatDateTime(activity.occurredAt)}
									{activity.authorName ? ` · ${activity.authorName}` : ""}
								</Typography>
							</Box>
						</Box>
					))}
				</Stack>
			</Stack>
		</Paper>
	);
}
