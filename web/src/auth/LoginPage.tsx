import { useState, type FormEvent } from "react";
import { Link as RouterLink, Navigate, useLocation, useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Link from "@mui/material/Link";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../api/client";
import { Wordmark } from "../branding/Wordmark";
import { colors } from "../theme";
import { useAuth } from "./AuthContext";

export function LoginPage() {
	const { isAuthenticated, login } = useAuth();
	const navigate = useNavigate();
	const location = useLocation();

	const [organisationSlug, setOrganisationSlug] = useState("");
	const [email, setEmail] = useState("");
	const [password, setPassword] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	if (isAuthenticated) {
		// "/" (not a hardcoded module) so IndexRedirect's own ORGANISATION_MANAGE-aware
		// logic decides Dashboard vs Students - see the comment on the navigate() call below.
		const redirectTo = (location.state as { from?: string } | null)?.from ?? "/";
		return <Navigate to={redirectTo} replace />;
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSubmitting(true);
		try {
			// Mobile keyboards routinely append a trailing space when a predictive-text
			// suggestion is tapped (seen on org slug in the wild) - trim defensively rather
			// than trust every mobile browser/keyboard combination to behave.
			await login(organisationSlug.trim(), email.trim(), password);
			// "/" rather than a hardcoded module - IndexRedirect decides Dashboard (anyone
			// with ORGANISATION_MANAGE) vs Students (everyone else), see IndexRedirect.tsx.
			navigate("/", { replace: true });
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Login failed - please try again");
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
				sx={{ p: 4, width: 360, borderColor: colors.rule, boxShadow: "0 1px 2px rgba(22,35,58,0.09)" }}
			>
				<Stack spacing={2}>
					<Box sx={{ mb: 0.5 }}>
						<Wordmark tagline="School Administration" />
					</Box>
					<Typography variant="body2" color="text.secondary">
						Sign in to your school's admin portal
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
					<TextField
						label="Password"
						type="password"
						value={password}
						onChange={(e) => setPassword(e.target.value)}
						required
						autoComplete="current-password"
						autoCapitalize="off"
						autoCorrect="off"
						spellCheck={false}
					/>
					<Button type="submit" variant="contained" disabled={submitting}>
						{submitting ? "Signing in..." : "Sign in"}
					</Button>
					<Link component={RouterLink} to="/forgot-password" variant="body2" sx={{ alignSelf: "center" }}>
						Forgot password?
					</Link>
				</Stack>
			</Paper>
		</Box>
	);
}
