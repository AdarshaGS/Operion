import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { me, type MeResponse } from "../../api/auth";
import { ApiError } from "../../api/client";
import {
	listOwnProfileChangeRequests,
	submitOwnProfileChangeRequest,
	type ProfileChangeRequestResponse,
} from "../../api/profileChangeRequests";
import { colors } from "../../theme";

const FIELD_ROWS: { label: string; value: (profile: MeResponse) => string }[] = [
	{ label: "User ID", value: (p) => String(p.userId) },
	{ label: "First name", value: (p) => p.firstName ?? "—" },
	{ label: "Last name", value: (p) => p.lastName ?? "—" },
	{ label: "Office", value: (p) => p.campusName ?? "Org-wide" },
	{ label: "Status", value: (p) => p.status ?? "—" },
	{ label: "Primary email", value: (p) => p.email ?? "—" },
	{ label: "Organisation", value: (p) => p.organisationName ?? "—" },
];

/** Operion has no separate login-username or per-user language setting (email is the
 * login identity, and there's no i18n yet) - this deliberately shows what the platform
 * actually has rather than fields borrowed from another product's profile page. */
export function ProfilePage() {
	const [profile, setProfile] = useState<MeResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	const [requests, setRequests] = useState<ProfileChangeRequestResponse[]>([]);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [phone, setPhone] = useState("");
	const [email, setEmail] = useState("");
	const [photoUrl, setPhotoUrl] = useState("");
	const [formError, setFormError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	function refreshRequests() {
		listOwnProfileChangeRequests()
			.then(setRequests)
			.catch(() => {});
	}

	useEffect(() => {
		me()
			.then(setProfile)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load profile"))
			.finally(() => setLoading(false));
		refreshRequests();
	}, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setFormError(null);
		if (!phone && !email && !photoUrl) {
			setFormError("Fill in at least one field to request a change");
			return;
		}
		setSubmitting(true);
		try {
			await submitOwnProfileChangeRequest({ phone: phone || null, email: email || null, photoUrl: photoUrl || null });
			setDialogOpen(false);
			setPhone("");
			setEmail("");
			setPhotoUrl("");
			refreshRequests();
		} catch (err) {
			setFormError(err instanceof ApiError ? err.message : "Failed to submit change request");
		} finally {
			setSubmitting(false);
		}
	}

	if (loading) {
		return <CircularProgress size={28} />;
	}

	return (
		<Stack spacing={3}>
			{error && <Alert severity="error">{error}</Alert>}

			{profile && (
				<>
					<Paper sx={{ p: 0 }}>
						<TableContainer>
							<Table size="small">
								<TableBody>
									{FIELD_ROWS.map((row) => (
										<TableRow key={row.label}>
											<TableCell sx={{ width: 200, color: colors.inkSoft, borderColor: colors.rule }}>{row.label}</TableCell>
											<TableCell sx={{ borderColor: colors.rule }}>{row.value(profile)}</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					</Paper>

					<Paper sx={{ p: 0 }}>
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Role</TableCell>
										<TableCell>Description</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{profile.roles.length === 0 ? (
										<TableRow>
											<TableCell colSpan={2} sx={{ color: colors.inkFaint }}>
												No active roles
											</TableCell>
										</TableRow>
									) : (
										profile.roles.map((role) => (
											<TableRow key={role.name}>
												<TableCell>
													<Chip label={role.name} size="small" />
												</TableCell>
												<TableCell sx={{ color: colors.inkSoft }}>{role.description ?? "—"}</TableCell>
											</TableRow>
										))
									)}
								</TableBody>
							</Table>
						</TableContainer>
					</Paper>

					<Paper sx={{ p: 3 }}>
						<Stack spacing={2}>
							<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
								<Typography variant="h6">Change requests</Typography>
								<Button size="small" onClick={() => setDialogOpen(true)}>
									Request a change
								</Button>
							</Box>

							{requests.length === 0 && <Alert severity="info">No change requests yet.</Alert>}

							{requests.length > 0 && (
								<TableContainer>
									<Table size="small">
										<TableHead>
											<TableRow>
												<TableCell>Phone</TableCell>
												<TableCell>Email</TableCell>
												<TableCell>Photo URL</TableCell>
												<TableCell>Status</TableCell>
											</TableRow>
										</TableHead>
										<TableBody>
											{requests.map((request) => (
												<TableRow key={request.id}>
													<TableCell>{request.phone ?? "—"}</TableCell>
													<TableCell>{request.email ?? "—"}</TableCell>
													<TableCell>{request.photoUrl ?? "—"}</TableCell>
													<TableCell>
														<Chip
															label={request.status}
															size="small"
															color={request.status === "APPROVED" ? "success" : request.status === "REJECTED" ? "error" : "default"}
														/>
													</TableCell>
												</TableRow>
											))}
										</TableBody>
									</Table>
								</TableContainer>
							)}
						</Stack>
					</Paper>
				</>
			)}

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Request a profile change</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<Typography variant="body2" color="text.secondary">
							Fill in only what you want changed - a staff member will review the request.
						</Typography>
						{formError && <Alert severity="error">{formError}</Alert>}
						<TextField label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} fullWidth />
						<TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} fullWidth />
						<TextField label="Photo URL" value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Submit
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
