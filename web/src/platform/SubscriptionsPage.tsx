import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Chip from "@mui/material/Chip";
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
import { listPlans, type PlanResponse } from "./api/plans";
import { PlatformApiError } from "./api/platformClient";
import { listAllSubscriptions, type SubscriptionResponse } from "./api/subscriptions";

const STATUS_COLOR: Record<string, "success" | "default"> = {
	ACTIVE: "success",
	CANCELLED: "default",
};

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

/** Cross-org browse view over GET /api/v1/platform/subscriptions - that endpoint already
 * existed purely to feed the dashboard's aggregate counts (see DashboardPage); this is the
 * first page that lets a platform admin actually look at the list. */
export function SubscriptionsPage() {
	const navigate = useNavigate();
	const [subscriptions, setSubscriptions] = useState<SubscriptionResponse[] | null>(null);
	const [organisations, setOrganisations] = useState<OrganisationResponse[]>([]);
	const [plans, setPlans] = useState<PlanResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		Promise.all([listAllSubscriptions(), listOrganisations(), listPlans()])
			.then(([subs, orgs, pl]) => {
				setSubscriptions(subs);
				setOrganisations(orgs);
				setPlans(pl);
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load subscriptions"));
	}, []);

	const orgName = (id: number) => organisations.find((org) => org.id === id)?.name ?? `#${id}`;
	const planName = (id: number) => plans.find((plan) => plan.id === id)?.name ?? `#${id}`;

	return (
		<Stack spacing={2}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Billing
				</Typography>
				<Typography variant="h4" component="h1">
					Subscriptions
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{subscriptions && subscriptions.length === 0 && <Alert severity="info">No subscriptions yet.</Alert>}

			{subscriptions && subscriptions.length > 0 && (
				<TableContainer component={Paper}>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Organisation</TableCell>
								<TableCell>Plan</TableCell>
								<TableCell>Price / student / year</TableCell>
								<TableCell>Start date</TableCell>
								<TableCell>End date</TableCell>
								<TableCell>Status</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{subscriptions
								.slice()
								.sort((a, b) => b.startDate.localeCompare(a.startDate))
								.map((subscription) => (
									<TableRow
										key={subscription.id}
										hover
										sx={{ cursor: "pointer" }}
										onClick={() => navigate(`/platform/organisations/${subscription.organisationId}`)}
									>
										<TableCell>{orgName(subscription.organisationId)}</TableCell>
										<TableCell>{planName(subscription.planId)}</TableCell>
										<TableCell>{currency(subscription.pricePerStudentPerYear)}</TableCell>
										<TableCell>{subscription.startDate}</TableCell>
										<TableCell>{subscription.endDate ?? "—"}</TableCell>
										<TableCell>
											<Chip label={subscription.status} size="small" color={STATUS_COLOR[subscription.status] ?? "default"} />
										</TableCell>
									</TableRow>
								))}
						</TableBody>
					</Table>
				</TableContainer>
			)}
		</Stack>
	);
}
