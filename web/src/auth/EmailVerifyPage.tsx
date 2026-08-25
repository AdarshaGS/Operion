import { useState, type FormEvent } from "react";
import { Link as RouterLink, useSearchParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Link from "@mui/material/Link";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { verifyEmail } from "../api/auth";
import { ApiError } from "../api/client";
import { Wordmark } from "../branding/Wordmark";
import { colors } from "../theme";

/** Public, unauthenticated. Reached via the link EmailVerificationService.issue() logs
 * server-side on user creation (see its own javadoc for why there's no real email
 * delivery yet) - confirm() just marks the account verified, it doesn't log anyone in. */
export function EmailVerifyPage() {
	const [searchParams] = useSearchParams();

	const [organisationSlug, setOrganisationSlug] = useState(searchParams.get("org") ?? "");
	const [token, setToken] = useState(searchParams.get("token") ?? "");
	const [error, setError] = useState<string | null>(null);
	const [verified, setVerified] = useState(false);
	const [submitting, setSubmitting] = useState(false);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSubmitting(true);
		try {
			await verifyEmail({ organisationSlug: organisationSlug.trim(), token: token.trim() });
			setVerified(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Couldn't verify your email - the link may have expired");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", bgcolor: colors.paper }}>
			<Paper
				component={verified ? "div" : "form"}
				onSubmit={verified ? undefined : handleSubmit}
				variant="outlined"
				sx={{ p: 4, width: 360, borderColor: colors.rule, boxShadow: "0 1px 2px rgba(22,35,58,0.09)" }}
			>
				<Stack spacing={2}>
					<Box sx={{ mb: 0.5 }}>
						<Wordmark tagline="Verify your email" />
					</Box>
					{verified ? (
						<Alert severity="success">Email verified.</Alert>
					) : (
						<>
							<Typography variant="body2" color="text.secondary">
								Confirm your email using the link's token
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
								label="Verification token"
								value={token}
								onChange={(e) => setToken(e.target.value)}
								required
								autoComplete="off"
								autoCapitalize="off"
								autoCorrect="off"
								spellCheck={false}
							/>
							<Button type="submit" variant="contained" disabled={submitting}>
								{submitting ? "Verifying..." : "Verify email"}
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
