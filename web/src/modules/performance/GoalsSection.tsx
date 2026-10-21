import { useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import FlagOutlinedIcon from "@mui/icons-material/FlagOutlined";
import { ApiError } from "../../api/client";
import type { Goal, GoalStatus } from "../../api/performance";
import { EmptyNote, SectionCard } from "../../components/DashboardKit";
import { formatDate } from "./reviewFormat";

const GOAL_STATUS_LABEL: Record<GoalStatus, string> = { OPEN: "In progress", ACHIEVED: "Achieved", PARTLY_ACHIEVED: "Partly achieved", MISSED: "Missed" };
const GOAL_STATUS_COLOR: Record<GoalStatus, "default" | "success" | "info" | "error"> = { OPEN: "default", ACHIEVED: "success", PARTLY_ACHIEVED: "info", MISSED: "error" };
const GOAL_STATUSES: GoalStatus[] = ["OPEN", "ACHIEVED", "PARTLY_ACHIEVED", "MISSED"];

export interface GoalsSectionProps {
	goals: Goal[];
	staffFirstName: string;
	cycleOpen: boolean;
	canManage: boolean;
	onAdd: (title: string, description: string) => Promise<unknown>;
	onUpdate: (goal: Goal, patch: { title?: string; description?: string | null; status?: GoalStatus }) => Promise<unknown>;
}

/** Goals for one cycle. A goal has a title, details and an outcome status; new goals and wording changes need an open cycle, the outcome can be recorded after it closes. */
export function GoalsSection({ goals, staffFirstName, cycleOpen, canManage, onAdd, onUpdate }: GoalsSectionProps) {
	const [editing, setEditing] = useState<Goal | "new" | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [busyId, setBusyId] = useState<number | null>(null);

	function setStatus(goal: Goal, status: GoalStatus) {
		setBusyId(goal.id);
		setError(null);
		onUpdate(goal, { status })
			.catch((err) => setError(err instanceof ApiError ? err.message : "The goal could not be updated"))
			.finally(() => setBusyId(null));
	}

	const addButton = canManage && cycleOpen && (
		<Button size="small" variant="outlined" startIcon={<AddIcon />} onClick={() => setEditing("new")}>Add goal</Button>
	);

	return (
		<SectionCard title="Goals and development" icon={<FlagOutlinedIcon fontSize="small" />} tint="orange" headerExtra={goals.length > 0 ? addButton : undefined}>
			{error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
			{goals.length === 0 ? (
				<Stack sx={{ alignItems: "center" }}>
					<EmptyNote>
						{canManage && cycleOpen
							? `No goals have been set for ${staffFirstName} in this cycle. Goals describe what they are working towards — add the first one to start tracking it.`
							: cycleOpen
								? `No goals have been set for ${staffFirstName} in this cycle yet.`
								: `No goals were set for ${staffFirstName} in this cycle, and a closed cycle takes no new goals.`}
					</EmptyNote>
					{addButton}
				</Stack>
			) : (
				<Stack spacing={1.5} divider={<Divider flexItem />}>
					{goals.map((goal) => (
						<Stack key={goal.id} role="group" aria-label={goal.title} direction={{ xs: "column", sm: "row" }} spacing={1.5} sx={{ alignItems: { sm: "flex-start" } }}>
							<Stack sx={{ flexGrow: 1, minWidth: 0 }} spacing={0.25}>
								<Typography variant="body2" sx={{ fontWeight: 600 }}>{goal.title}</Typography>
								<Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "pre-wrap" }}>{goal.description ?? "No details added."}</Typography>
								<Typography variant="caption" color="text.secondary">Updated {formatDate(goal.updatedAt)}</Typography>
							</Stack>
							<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
								{canManage ? (
									<TextField select size="small" value={goal.status} disabled={busyId === goal.id} onChange={(e) => setStatus(goal, e.target.value as GoalStatus)}
										sx={{ minWidth: 160 }} slotProps={{ htmlInput: { "aria-label": `Status of ${goal.title}` } }}>
										{GOAL_STATUSES.map((s) => <MenuItem key={s} value={s}>{GOAL_STATUS_LABEL[s]}</MenuItem>)}
									</TextField>
								) : (
									<Chip size="small" color={GOAL_STATUS_COLOR[goal.status]} variant="outlined" label={GOAL_STATUS_LABEL[goal.status]} />
								)}
								{canManage && cycleOpen && <Button size="small" onClick={() => setEditing(goal)}>Edit</Button>}
							</Stack>
						</Stack>
					))}
				</Stack>
			)}
			{editing && (
				<GoalDialog goal={editing === "new" ? null : editing} onClose={() => setEditing(null)}
					onSave={(title, description) => (editing === "new" ? onAdd(title, description) : onUpdate(editing, { title, description })).then(() => setEditing(null))} />
			)}
		</SectionCard>
	);
}

function GoalDialog({ goal, onClose, onSave }: { goal: Goal | null; onClose: () => void; onSave: (title: string, description: string) => Promise<unknown> }) {
	const [title, setTitle] = useState(goal?.title ?? "");
	const [description, setDescription] = useState(goal?.description ?? "");
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState<string | null>(null);

	function save() {
		setSaving(true);
		setError(null);
		onSave(title, description).catch((err) => {
			setError(err instanceof ApiError ? err.message : "The goal could not be saved");
			setSaving(false);
		});
	}

	return (
		<Dialog open onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
			<DialogTitle>{goal ? "Edit goal" : "Add goal"}</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ pt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField label="Goal" value={title} onChange={(e) => setTitle(e.target.value)} autoFocus required fullWidth placeholder="e.g. Run a weekly reading club" />
					<TextField label="Details" value={description} onChange={(e) => setDescription(e.target.value)} multiline minRows={3} fullWidth
						helperText="What does success look like? Optional." />
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={saving}>Cancel</Button>
				<Button variant="contained" onClick={save} disabled={saving || title.trim() === ""}>{saving ? "Saving…" : goal ? "Save changes" : "Add goal"}</Button>
			</DialogActions>
		</Dialog>
	);
}
