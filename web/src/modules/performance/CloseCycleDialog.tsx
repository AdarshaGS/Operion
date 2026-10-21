import { useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import { ApiError } from "../../api/client";
import { closeCycle, type AppraisalCycle } from "../../api/performance";

/** Closing is the one irreversible step of the workflow, so it is always confirmed. */
export function CloseCycleDialog({ cycle, pending, onClose, onClosed }: { cycle: AppraisalCycle; pending: number | null; onClose: () => void; onClosed: () => void }) {
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState<string | null>(null);

	function confirm() {
		setSaving(true);
		setError(null);
		closeCycle(cycle.id)
			.then(onClosed)
			.catch((err) => {
				setError(err instanceof ApiError ? err.message : "The cycle could not be closed");
				setSaving(false);
			});
	}

	return (
		<Dialog open onClose={saving ? undefined : onClose} fullWidth maxWidth="xs">
			<DialogTitle>Close {cycle.name}?</DialogTitle>
			<DialogContent>
				{error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
				<DialogContentText>
					Closing finalizes this cycle for everyone in it. Ratings can no longer be added or changed and no new goals can be set. This cannot be undone.
				</DialogContentText>
				{pending !== null && pending > 0 && (
					<Alert severity="warning" sx={{ mt: 2 }}>
						{pending} {pending === 1 ? "review has" : "reviews have"} no manager rating yet and will be marked “Not reviewed”.
					</Alert>
				)}
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={saving}>Cancel</Button>
				<Button color="warning" variant="contained" onClick={confirm} disabled={saving}>{saving ? "Closing…" : "Close cycle"}</Button>
			</DialogActions>
		</Dialog>
	);
}
