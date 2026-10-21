import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { getSecretsReport, type SecretsReport } from "./api/systemHealth";
import { PlatformApiError } from "./api/platformClient";

/** SecretsGuard's findings: which environment variables still hold a published default. Names only, never values.
 * The report's warnings (backups off) are left out - the Backups panel below already says so. */
export function SecretsPanel() {
	const [report, setReport] = useState<SecretsReport | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getSecretsReport()
			.then(setReport)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load the secrets check"));
	}, []);

	return (
		<Paper variant="outlined" sx={{ p: 2.5 }}>
			<Stack spacing={2}>
				<Stack direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap" }}>
					<Typography variant="h6" component="h2">
						Secrets
					</Typography>
					{report && (
						<Chip
							size="small"
							label={report.problems.length === 0 ? "All set" : `${report.problems.length} to fix`}
							color={report.problems.length === 0 ? "success" : "error"}
						/>
					)}
				</Stack>

				{error && <Alert severity="error">{error}</Alert>}

				{report && (
					<>
						{report.problems.length === 0 ? (
							<Typography variant="body2" color="text.secondary">
								No secret is left at a built-in or published default.
							</Typography>
						) : (
							<>
								<Alert severity={report.enforced ? "error" : "warning"}>
									{report.enforced
										? "This server is in production mode and should not have started with these - restart it after setting them."
										: "Fine for development. A production server (SPRING_PROFILES_ACTIVE=prod) refuses to start until these are set."}
								</Alert>
								<Table size="small" aria-label="Secrets to set">
									<TableHead>
										<TableRow>
											<TableCell>Environment variable</TableCell>
											<TableCell>Problem</TableCell>
										</TableRow>
									</TableHead>
									<TableBody>
										{report.problems.map((finding) => (
											<TableRow key={`${finding.setting}-${finding.problem}`}>
												<TableCell sx={{ fontFamily: "monospace" }}>{finding.setting}</TableCell>
												<TableCell>{finding.problem}</TableCell>
											</TableRow>
										))}
									</TableBody>
								</Table>
							</>
						)}
					</>
				)}
			</Stack>
		</Paper>
	);
}
