import { useState, type FormEvent } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../api/client";
import { Wordmark } from "../branding/Wordmark";
import { colors } from "../theme";
import { useAuth } from "./AuthContext";

/** Public, unauthenticated - same trust tier as ClaimInvitePage. Reached via the one-time
 * link an admin hands a new hire after UserController.invite() issues one - see
 * StaffInviteService. */
export function ClaimStaffInvitePage() {
	const { isAuthenticated, claimStaffInvite } = useAuth();
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();

	const [organisationSlug, setOrganisationSlug] = useState("");
	const [token, setToken] = useState(searchParams.get("token") ?? "");
	const [password, setPassword] = useState("");
	const [confirmPassword, setConfirmPassword] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	if (isAuthenticated) {
		return <Navigate to="/students" replace />;
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		if (password !== confirmPassword) {
			setError("Passwords don't match");
			return;
		}
		setSubmitting(true);
		try {
			await claimStaffInvite(organisationSlug.trim(), token.trim(), password);
			navigate("/students", { replace: true });
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Couldn't set up your account - the link may have expired");
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
						<Wordmark tagline="Staff Onboarding" />
					</Box>
					<Typography variant="body2" color="text.secondary">
						Set up your account using the invite token your admin gave you
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
						helperText="The school's login identifier - ask them if you don't have it"
					/>
					<TextField
						label="Invite token"
						value={token}
						onChange={(e) => setToken(e.target.value)}
						required
						autoComplete="off"
						autoCapitalize="off"
						autoCorrect="off"
						spellCheck={false}
					/>
					<TextField
						label="Choose a password"
						type="password"
						value={password}
						onChange={(e) => setPassword(e.target.value)}
						required
						autoComplete="new-password"
					/>
					<TextField
						label="Confirm password"
						type="password"
						value={confirmPassword}
						onChange={(e) => setConfirmPassword(e.target.value)}
						required
						autoComplete="new-password"
					/>
					<Button type="submit" variant="contained" disabled={submitting}>
						{submitting ? "Setting up..." : "Set up account"}
					</Button>
				</Stack>
			</Paper>
		</Box>
	);
}
