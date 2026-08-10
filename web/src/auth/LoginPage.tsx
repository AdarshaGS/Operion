import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../api/client";
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
		const redirectTo = (location.state as { from?: string } | null)?.from ?? "/students";
		return <Navigate to={redirectTo} replace />;
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSubmitting(true);
		try {
			await login(organisationSlug, email, password);
			navigate("/students", { replace: true });
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Login failed - please try again");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", bgcolor: "grey.100" }}>
			<Paper component="form" onSubmit={handleSubmit} elevation={3} sx={{ p: 4, width: 360 }}>
				<Stack spacing={2}>
					<Typography variant="h5" component="h1">
						Operion
					</Typography>
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
					/>
					<TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
					<TextField
						label="Password"
						type="password"
						value={password}
						onChange={(e) => setPassword(e.target.value)}
						required
					/>
					<Button type="submit" variant="contained" disabled={submitting}>
						{submitting ? "Signing in..." : "Sign in"}
					</Button>
				</Stack>
			</Paper>
		</Box>
	);
}
