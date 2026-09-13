import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { listOrganisations, type OrganisationResponse } from "./api/organisations";
import { PlatformApiError } from "./api/platformClient";
import { listUsage, type UsageResponse } from "./api/usage";

/** "Usage" is approximated as each org's current active-student headcount - see
 * BillingService.usageByOrganisation() for why (no metering/tracking concept exists in
 * this codebase yet, and this is the simplest real signal already available). */
export function UsagePage() {
	const navigate = useNavigate();
	const [usage, setUsage] = useState<UsageResponse[] | null>(null);
	const [organisations, setOrganisations] = useState<OrganisationResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		Promise.all([listUsage(), listOrganisations()])
			.then(([u, orgs]) => {
				setUsage(u);
				setOrganisations(orgs);
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load usage"));
	}, []);

	const orgName = (id: number) => organisations.find((org) => org.id === id)?.name ?? `#${id}`;

	return (
		<Stack spacing={2}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Operations
				</Typography>
				<Typography variant="h4" component="h1">
					Usage
				</Typography>
				<Typography variant="body2" color="text.secondary">
					Active students per organisation.
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{usage && usage.length === 0 && <Alert severity="info">No organisations yet.</Alert>}

			{usage && usage.length > 0 && (
				<TableContainer component={Paper}>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Organisation</TableCell>
								<TableCell align="right">Active students</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{usage
								.slice()
								.sort((a, b) => b.activeStudentCount - a.activeStudentCount)
								.map((row) => (
									<TableRow
										key={row.organisationId}
										hover
										sx={{ cursor: "pointer" }}
										onClick={() => navigate(`/platform/organisations/${row.organisationId}`)}
									>
										<TableCell>{orgName(row.organisationId)}</TableCell>
										<TableCell align="right">{row.activeStudentCount}</TableCell>
									</TableRow>
								))}
						</TableBody>
					</Table>
				</TableContainer>
			)}
		</Stack>
	);
}
