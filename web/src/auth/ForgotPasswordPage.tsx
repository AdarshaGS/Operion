import { useState, type FormEvent } from "react";
import { Link as RouterLink } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Link from "@mui/material/Link";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { requestPasswordReset } from "../api/auth";
import { ApiError } from "../api/client";
import { Wordmark } from "../branding/Wordmark";
import { colors } from "../theme";

/** Public, unauthenticated. Always shows the same generic "sent" confirmation regardless
 * of whether the org/email actually matched anything - see PasswordResetService, which is
 * what makes this flow non-enumerable. There is no real mail delivery yet in this codebase
 * (v1 - see PasswordResetService's own javadoc), so in practice the link only reaches
 * whoever can read the backend's logs. */
export function ForgotPasswordPage() {
	const [organisationSlug, setOrganisationSlug] = useState("");
	const [email, setEmail] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitted, setSubmitted] = useState(false);
	const [submitting, setSubmitting] = useState(false);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSubmitting(true);
		try {
			await requestPasswordReset({ organisationSlug: organisationSlug.trim(), email: email.trim() });
			setSubmitted(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Something went wrong - please try again");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", bgcolor: colors.paper }}>
			<Paper
				component={submitted ? "div" : "form"}
				onSubmit={submitted ? undefined : handleSubmit}
				variant="outlined"
				sx={{ p: 4, width: 360, borderColor: colors.rule, boxShadow: "0 1px 2px rgba(22,35,58,0.09)" }}
			>
				<Stack spacing={2}>
					<Box sx={{ mb: 0.5 }}>
						<Wordmark tagline="Reset your password" />
					</Box>
					{submitted ? (
						<Alert severity="success">If that account exists, a reset link has been sent. Ask your admin if it doesn't arrive.</Alert>
					) : (
						<>
							<Typography variant="body2" color="text.secondary">
								Enter your organisation and email - we'll send a reset link if an account matches.
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
								label="Email"
								type="email"
								value={email}
								onChange={(e) => setEmail(e.target.value)}
								required
								autoComplete="username"
								autoCapitalize="off"
								autoCorrect="off"
								spellCheck={false}
							/>
							<Button type="submit" variant="contained" disabled={submitting}>
								{submitting ? "Sending..." : "Send reset link"}
							</Button>
						</>
					)}
					<Link component={RouterLink} to="/login" variant="body2">
						Back to sign in
					</Link>
				</Stack>
			</Paper>
		</Box>
	);
}
