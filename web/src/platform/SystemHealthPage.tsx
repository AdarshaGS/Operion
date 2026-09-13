import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { getSystemHealth, type SystemHealthComponent } from "./api/systemHealth";
import { PlatformApiError } from "./api/platformClient";

/** Deliberately just API + Database - see SystemHealthController's javadoc for why the
 * mockup's other chips (Auth/Background Jobs/Email) aren't here: there's no scheduler/
 * queue to check for the latter, and no real per-request signal for the former beyond
 * "the API responded" (already implied by API showing at all). */
export function SystemHealthPage() {
	const [components, setComponents] = useState<SystemHealthComponent[] | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getSystemHealth()
			.then(setComponents)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load system health"));
	}, []);

	const allOperational = components?.every((c) => c.operational) ?? false;

	return (
		<Stack spacing={2}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Operations
				</Typography>
				<Typography variant="h4" component="h1">
					System Health
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{components && (
				<Paper variant="outlined" sx={{ p: 2.5 }}>
					<Stack spacing={2}>
						<Typography variant="body2" color={allOperational ? "success.main" : "error.main"}>
							{allOperational ? "All monitored systems are operational" : "One or more systems need attention"}
						</Typography>
						<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
							{components.map((component) => (
								<Stack key={component.name} spacing={0.5}>
									<Typography variant="overline" color="text.secondary">
										{component.name}
									</Typography>
									<Chip
										label={component.operational ? "Operational" : "Down"}
										size="small"
										color={component.operational ? "success" : "error"}
									/>
								</Stack>
							))}
						</Stack>
					</Stack>
				</Paper>
			)}
		</Stack>
	);
}
