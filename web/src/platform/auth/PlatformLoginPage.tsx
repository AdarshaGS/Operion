import { useState, type FormEvent } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { Wordmark } from "../../branding/Wordmark";
import { colors } from "../../theme";
import { PlatformApiError } from "../api/platformClient";
import { usePlatformAuth } from "./PlatformAuthContext";

export function PlatformLoginPage() {
	const { isAuthenticated, login } = usePlatformAuth();
	const navigate = useNavigate();

	const [email, setEmail] = useState("");
	const [password, setPassword] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	if (isAuthenticated) {
		return <Navigate to="/platform/organisations" replace />;
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSubmitting(true);
		try {
			await login(email.trim(), password);
			navigate("/platform/organisations", { replace: true });
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Login failed - please try again");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", bgcolor: colors.ink }}>
			<Paper
				component="form"
				onSubmit={handleSubmit}
				variant="outlined"
				sx={{ p: 4, width: 380, borderColor: colors.rule, boxShadow: "0 1px 2px rgba(22,35,58,0.09)" }}
			>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
						<Wordmark tagline="Operator console" />
						<Chip label="PLATFORM" size="small" variant="outlined" sx={{ borderColor: colors.accent, color: colors.accent }} />
					</Box>
					<Typography variant="body2" color="text.secondary">
						Internal — Operion staff only. Schools sign in from their own portal.
					</Typography>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField
						label="Email"
						type="email"
						value={email}
						onChange={(e) => setEmail(e.target.value)}
						required
						autoFocus
						autoComplete="username"
					/>
					<TextField
						label="Password"
						type="password"
						value={password}
						onChange={(e) => setPassword(e.target.value)}
						required
						autoComplete="current-password"
					/>
					<Button type="submit" variant="contained" disabled={submitting}>
						{submitting ? "Signing in..." : "Sign in"}
					</Button>
				</Stack>
			</Paper>
		</Box>
	);
}
