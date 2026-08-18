import { useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { confirmPasswordReset } from "../api/auth";
import { ApiError } from "../api/client";
import { Wordmark } from "../branding/Wordmark";
import { colors } from "../theme";

/** Public, unauthenticated. Reached via the link ForgotPasswordPage's request triggers -
 * see PasswordResetService.confirmReset(). */
export function ResetPasswordPage() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();

	const [organisationSlug, setOrganisationSlug] = useState(searchParams.get("org") ?? "");
	const [token, setToken] = useState(searchParams.get("token") ?? "");
	const [newPassword, setNewPassword] = useState("");
	const [confirmNewPassword, setConfirmNewPassword] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		if (newPassword !== confirmNewPassword) {
			setError("Passwords don't match");
			return;
		}
		setSubmitting(true);
		try {
			await confirmPasswordReset({ organisationSlug: organisationSlug.trim(), token: token.trim(), newPassword });
			navigate("/login", { replace: true });
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Couldn't reset your password - the link may have expired");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", bgcolor: colors.paper }}>
			<Paper
				component="form"
				onSubmit={handleSubmit}
				variant="outlined"
				sx={{ p: 4, width: 380, borderColor: colors.rule, boxShadow: "0 1px 2px rgba(22,35,58,0.09)" }}
			>
				<Stack spacing={2}>
					<Box sx={{ mb: 0.5 }}>
						<Wordmark tagline="Reset your password" />
					</Box>
					<Typography variant="body2" color="text.secondary">
						Choose a new password using the reset link's token
					</Typography>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField
						label="Organisation slug"
						value={organisationSlug}
						onChange={(e) => setOrganisationSlug(e.target.value)}
						required
						autoFocus
						autoComplete="off"
						autoCapitalize="off"
						autoCorrect="off"
						spellCheck={false}
					/>
					<TextField
						label="Reset token"
						value={token}
						onChange={(e) => setToken(e.target.value)}
						required
						autoComplete="off"
						autoCapitalize="off"
						autoCorrect="off"
						spellCheck={false}
					/>
					<TextField
						label="New password"
						type="password"
						value={newPassword}
						onChange={(e) => setNewPassword(e.target.value)}
						required
						autoComplete="new-password"
					/>
					<TextField
						label="Confirm new password"
						type="password"
						value={confirmNewPassword}
						onChange={(e) => setConfirmNewPassword(e.target.value)}
						required
						autoComplete="new-password"
					/>
					<Button type="submit" variant="contained" disabled={submitting}>
						{submitting ? "Resetting..." : "Reset password"}
					</Button>
				</Stack>
			</Paper>
		</Box>
	);
}
