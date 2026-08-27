import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { me, type MeResponse } from "../../api/auth";
import { ApiError } from "../../api/client";
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

	useEffect(() => {
		me()
			.then(setProfile)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load profile"))
			.finally(() => setLoading(false));
	}, []);

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
				</>
			)}
		</Stack>
	);
}
