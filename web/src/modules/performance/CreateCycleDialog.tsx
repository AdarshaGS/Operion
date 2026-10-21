import { useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import { ApiError } from "../../api/client";
import { createCycle, type AppraisalCycle } from "../../api/performance";

/** Name and dates for a new appraisal cycle. Everything else about a cycle (who is in it, what is scored) comes from existing records. */
export function CreateCycleDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (cycle: AppraisalCycle) => void }) {
	const [name, setName] = useState("");
	const [startDate, setStartDate] = useState("");
	const [endDate, setEndDate] = useState("");
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const datesInvalid = startDate !== "" && endDate !== "" && endDate < startDate;
	const canSave = name.trim() !== "" && startDate !== "" && endDate !== "" && !datesInvalid && !saving;

	function save() {
		setSaving(true);
		setError(null);
		createCycle({ name: name.trim(), startDate, endDate })
			.then(onCreated)
			.catch((err) => {
				setError(err instanceof ApiError ? err.message : "The cycle could not be created");
				setSaving(false);
			});
	}

	return (
		<Dialog open onClose={saving ? undefined : onClose} fullWidth maxWidth="xs">
			<DialogTitle>Create appraisal cycle</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ pt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField label="Cycle name" placeholder="e.g. Term 1 2026" value={name} onChange={(e) => setName(e.target.value)} autoFocus required fullWidth />
					<Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
						<TextField label="Review period starts" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required fullWidth slotProps={{ inputLabel: { shrink: true } }} />
						<TextField label="Review period ends" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} required fullWidth error={datesInvalid}
							helperText={datesInvalid ? "The end cannot be before the start" : undefined} slotProps={{ inputLabel: { shrink: true } }} />
					</Stack>
					<Alert severity="info" variant="outlined">
						Scores for the cycle are read from records within these dates. Ratings and goals can be added until the cycle is closed.
					</Alert>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={saving}>Cancel</Button>
				<Button variant="contained" onClick={save} disabled={!canSave}>{saving ? "Creating…" : "Create cycle"}</Button>
			</DialogActions>
		</Dialog>
	);
}
