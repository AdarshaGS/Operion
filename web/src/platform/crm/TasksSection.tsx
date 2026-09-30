import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { completeTask, createTask, listCrmAdmins, listTasks, type CrmAdminResponse, type CrmOwner, type CrmTaskResponse } from "../api/crm";
import { PlatformApiError } from "../api/platformClient";
import { formatDateTime, isOverdue, tomorrowMorningInput } from "./crmFormat";

/** Follow-up reminders on a lead or organisation. Open tasks first (soonest due), completed
 * ones below, struck through. Tasks can't be un-completed - the backend has no such endpoint. */
export function TasksSection({ owner }: { owner: CrmOwner }) {
	const ownerKey = "leadId" in owner ? `lead-${owner.leadId}` : `org-${owner.organisationId}`;
	const [tasks, setTasks] = useState<CrmTaskResponse[] | null>(null);
	const [admins, setAdmins] = useState<CrmAdminResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [title, setTitle] = useState("");
	const [dueAt, setDueAt] = useState(tomorrowMorningInput());
	const [assigneeId, setAssigneeId] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listTasks(owner)
			.then(setTasks)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load tasks"));
	}

	useEffect(() => {
		refresh();
		listCrmAdmins().then(setAdmins).catch(() => {});
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [ownerKey]);

	function openCreate() {
		setTitle("");
		setDueAt(tomorrowMorningInput());
		setAssigneeId("");
		setDialogOpen(true);
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createTask(owner, title, new Date(dueAt).toISOString(), assigneeId ? Number(assigneeId) : null);
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to add task");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleComplete(task: CrmTaskResponse) {
		try {
			await completeTask(task.id);
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to complete task");
		}
	}

	const open = tasks?.filter((t) => !t.completedAt) ?? [];
	const done = tasks?.filter((t) => t.completedAt) ?? [];

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Follow-ups</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={openCreate}>
						Add task
					</Button>
				</Stack>

				{error && <Alert severity="error">{error}</Alert>}
				{tasks && tasks.length === 0 && <Alert severity="info">No follow-ups scheduled.</Alert>}

				<Stack spacing={0.5}>
					{[...open, ...done].map((task) => (
						<Box key={task.id} sx={{ display: "flex", alignItems: "center", gap: 1 }}>
							<Checkbox
								size="small"
								checked={!!task.completedAt}
								disabled={!!task.completedAt}
								onChange={() => handleComplete(task)}
								slotProps={{ input: { "aria-label": `Complete ${task.title}` } }}
							/>
							<Box sx={{ minWidth: 0 }}>
								<Typography variant="body2" sx={{ textDecoration: task.completedAt ? "line-through" : "none" }}>
									{task.title}
								</Typography>
								<Typography variant="caption" color={isOverdue(task) ? "error" : "text.secondary"}>
									{task.completedAt ? `Completed ${formatDateTime(task.completedAt)}` : `${isOverdue(task) ? "Overdue · " : "Due "}${formatDateTime(task.dueAt)}`}
									{task.assigneeName ? ` · ${task.assigneeName}` : ""}
								</Typography>
							</Box>
						</Box>
					))}
				</Stack>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add follow-up</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required fullWidth />
						<TextField
							label="Due"
							type="datetime-local"
							value={dueAt}
							onChange={(e) => setDueAt(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField
							select
							label="Assignee"
							value={assigneeId}
							onChange={(e) => setAssigneeId(e.target.value)}
							helperText="Defaults to you"
							fullWidth
						>
							<MenuItem value="">Me</MenuItem>
							{admins.map((admin) => (
								<MenuItem key={admin.id} value={admin.id}>
									{admin.name}
								</MenuItem>
							))}
						</TextField>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !title.trim() || !dueAt}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
