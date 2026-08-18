import { useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import { changePassword } from "../api/auth";
import { ApiError } from "../api/client";

/** Self-service password change, reached from ProfileMenu rather than Settings - the
 * userId comes from the caller's own bearer token on the backend
 * (TenantContext.getActorId()), not a prop, so this needs no identity of its own beyond
 * "you are signed in". */
export function ChangePasswordDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
	const [currentPassword, setCurrentPassword] = useState("");
	const [newPassword, setNewPassword] = useState("");
	const [confirmNewPassword, setConfirmNewPassword] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [success, setSuccess] = useState(false);
	const [submitting, setSubmitting] = useState(false);

	function reset() {
		setCurrentPassword("");
		setNewPassword("");
		setConfirmNewPassword("");
		setError(null);
		setSuccess(false);
	}

	function handleClose() {
		reset();
		onClose();
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSuccess(false);
		if (newPassword !== confirmNewPassword) {
			setError("New passwords don't match");
			return;
		}
		setSubmitting(true);
		try {
			await changePassword({ currentPassword, newPassword });
			setCurrentPassword("");
			setNewPassword("");
			setConfirmNewPassword("");
			setSuccess(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to change password");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Dialog open={open} onClose={handleClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
			<DialogTitle>Change password</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					{success && <Alert severity="success">Password updated</Alert>}
					<TextField
						label="Current password"
						type="password"
						value={currentPassword}
						onChange={(e) => setCurrentPassword(e.target.value)}
						required
						autoFocus
						autoComplete="current-password"
						fullWidth
					/>
					<TextField
						label="New password"
						type="password"
						value={newPassword}
						onChange={(e) => setNewPassword(e.target.value)}
						required
						autoComplete="new-password"
						fullWidth
					/>
					<TextField
						label="Confirm new password"
						type="password"
						value={confirmNewPassword}
						onChange={(e) => setConfirmNewPassword(e.target.value)}
						required
						autoComplete="new-password"
						fullWidth
					/>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={handleClose}>Close</Button>
				<Button type="submit" variant="contained" disabled={submitting}>
					Change password
				</Button>
			</DialogActions>
		</Dialog>
	);
}
