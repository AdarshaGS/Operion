import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { listRecentActivity, type ActivityResponse } from "./api/activity";
import { describeActivity } from "./activityDescriptions";
import { listOrganisations, type OrganisationResponse } from "./api/organisations";
import { PlatformApiError } from "./api/platformClient";
import { listAllInvoices, type PlatformInvoiceResponse } from "./api/platformInvoices";
import { listPlans, type PlanResponse } from "./api/plans";
import { listAllSubscriptions, type SubscriptionResponse } from "./api/subscriptions";

function timeAgo(iso: string): string {
	const ms = Date.now() - new Date(iso).getTime();
	const hours = Math.round(ms / 3_600_000);
	if (hours < 1) return "just now";
	if (hours < 24) return `${hours} hour${hours === 1 ? "" : "s"} ago`;
	const days = Math.round(hours / 24);
	return `${days} day${days === 1 ? "" : "s"} ago`;
}

/** Full view over GET /api/v1/platform/activity - the same feed DashboardPage's "Recent
 * activity" panel shows a handful of, in one place. */
export function ActivityPage() {
	const [activity, setActivity] = useState<ActivityResponse[] | null>(null);
	const [organisations, setOrganisations] = useState<OrganisationResponse[]>([]);
	const [plans, setPlans] = useState<PlanResponse[]>([]);
	const [subscriptions, setSubscriptions] = useState<SubscriptionResponse[]>([]);
	const [invoices, setInvoices] = useState<PlatformInvoiceResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		Promise.all([listRecentActivity(), listOrganisations(), listPlans(), listAllSubscriptions(), listAllInvoices()])
			.then(([act, orgs, pl, subs, inv]) => {
				setActivity(act);
				setOrganisations(orgs);
				setPlans(pl);
				setSubscriptions(subs);
				setInvoices(inv);
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load activity"));
	}, []);

	return (
		<Stack spacing={2}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Operations
				</Typography>
				<Typography variant="h4" component="h1">
					Activity
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{activity && activity.length === 0 && <Alert severity="info">Nothing has happened yet.</Alert>}

			{activity && activity.length > 0 && (
				<Paper variant="outlined">
					<Stack divider={<Stack sx={{ borderBottom: "1px solid", borderColor: "divider" }} />}>
						{activity.map((entry) => (
							<Stack
								key={entry.id}
								direction="row"
								spacing={2}
								sx={{ px: 2, py: 1.5, alignItems: "center", justifyContent: "space-between" }}
							>
								<Typography variant="body2">{describeActivity(entry, { organisations, plans, subscriptions, invoices })}</Typography>
								<Typography variant="caption" color="text.secondary" sx={{ whiteSpace: "nowrap" }}>
									{timeAgo(entry.occurredAt)}
								</Typography>
							</Stack>
						))}
					</Stack>
				</Paper>
			)}
		</Stack>
	);
}
