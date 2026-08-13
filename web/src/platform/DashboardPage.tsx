import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { listOrganisations, type OrganisationResponse } from "./api/organisations";
import { listAllInvoices, type PlatformInvoiceResponse } from "./api/platformInvoices";
import { listPlans, type PlanResponse } from "./api/plans";
import { PlatformApiError } from "./api/platformClient";
import { listAllSubscriptions, type SubscriptionResponse } from "./api/subscriptions";

const ORG_STATUSES = ["TRIAL", "ACTIVE", "SUSPENDED", "ARCHIVED"] as const;

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

function StatTile({ label, value, onClick }: { label: string; value: string; onClick?: () => void }) {
	return (
		<Paper
			sx={{ p: 2.5, flex: "1 1 180px", cursor: onClick ? "pointer" : "default" }}
			onClick={onClick}
			variant="outlined"
		>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					{label}
				</Typography>
				<Typography variant="h4" component="div">
					{value}
				</Typography>
			</Stack>
		</Paper>
	);
}

/** Landing page for the platform-admin plane - aggregate counts pulled from the same
 * cross-org endpoints OrganisationsPage/PlansPage already use, plus the two new
 * cross-org subscription/invoice endpoints added alongside this page (issues#22). No
 * new backend aggregation - just sums/counts over lists small enough at this scale to
 * compute client-side, same "don't optimize for scale the product doesn't have yet"
 * call as the rest of this frontend. */
export function DashboardPage() {
	const navigate = useNavigate();
	const [organisations, setOrganisations] = useState<OrganisationResponse[] | null>(null);
	const [subscriptions, setSubscriptions] = useState<SubscriptionResponse[] | null>(null);
	const [invoices, setInvoices] = useState<PlatformInvoiceResponse[] | null>(null);
	const [plans, setPlans] = useState<PlanResponse[] | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		Promise.all([listOrganisations(), listAllSubscriptions(), listAllInvoices(), listPlans()])
			.then(([orgs, subs, inv, pl]) => {
				setOrganisations(orgs);
				setSubscriptions(subs);
				setInvoices(inv);
				setPlans(pl);
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load dashboard"));
	}, []);

	const loading = !organisations || !subscriptions || !invoices || !plans;

	const orgCountByStatus = ORG_STATUSES.map((status) => ({
		status,
		count: organisations?.filter((org) => org.status === status).length ?? 0,
	}));

	const activeSubscriptions = subscriptions?.filter((sub) => sub.status === "ACTIVE").length ?? 0;
	const outstandingInvoices = invoices?.filter((inv) => inv.status === "ISSUED") ?? [];
	const paidInvoices = invoices?.filter((inv) => inv.status === "PAID") ?? [];
	const outstandingTotal = outstandingInvoices.reduce((sum, inv) => sum + inv.amount, 0);
	const paidTotal = paidInvoices.reduce((sum, inv) => sum + inv.amount, 0);
	const activePlans = plans?.filter((plan) => plan.status === "ACTIVE").length ?? 0;

	return (
		<Stack spacing={3}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Platform
				</Typography>
				<Typography variant="h4" component="h1">
					Dashboard
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{!loading && (
				<>
					<Box>
						<Typography variant="h6" sx={{ mb: 1.5 }}>
							Organisations
						</Typography>
						<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
							<StatTile label="Total" value={String(organisations!.length)} onClick={() => navigate("/platform/organisations")} />
							{orgCountByStatus.map(({ status, count }) => (
								<StatTile key={status} label={status} value={String(count)} onClick={() => navigate("/platform/organisations")} />
							))}
						</Stack>
					</Box>

					<Box>
						<Typography variant="h6" sx={{ mb: 1.5 }}>
							Billing
						</Typography>
						<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
							<StatTile label="Active subscriptions" value={String(activeSubscriptions)} />
							<StatTile label="Outstanding invoices" value={String(outstandingInvoices.length)} />
							<StatTile label="Outstanding amount" value={currency(outstandingTotal)} />
							<StatTile label="Collected revenue" value={currency(paidTotal)} />
							<StatTile label="Active plans" value={String(activePlans)} onClick={() => navigate("/platform/plans")} />
						</Stack>
					</Box>
				</>
			)}
		</Stack>
	);
}
