import Alert from "@mui/material/Alert";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useAuth } from "../auth/AuthContext";

/** #207 fills this in with real attendance/fees/results/announcements data per child;
 * #210 only needs the route to exist and show who the session is scoped to. */
export function GuardianDashboardPage() {
	const { portalIdentity } = useAuth();
	const context = portalIdentity?.portalContexts.find((c) => c.type === "GUARDIAN");
	const children = context?.children ?? [];

	return (
		<Stack spacing={2}>
			<Typography variant="h5">Parent portal</Typography>
			{children.length === 0 ? (
				<Alert severity="info">No children are linked to your account yet.</Alert>
			) : (
				<Typography variant="body1">
					Linked children: {children.map((child) => child.name).join(", ")}
				</Typography>
			)}
		</Stack>
	);
}
