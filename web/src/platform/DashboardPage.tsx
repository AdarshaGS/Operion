import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import FormControl from "@mui/material/FormControl";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import {
	Bar,
	BarChart,
	CartesianGrid,
	Line,
	LineChart,
	ResponsiveContainer,
	Tooltip as RechartsTooltip,
	XAxis,
	YAxis,
} from "recharts";
import { listRecentActivity, type ActivityResponse } from "./api/activity";
import { listOrganisations, type OrganisationResponse } from "./api/organisations";
import { listAllInvoices, type PlatformInvoiceResponse } from "./api/platformInvoices";
import { listPlans, type PlanResponse } from "./api/plans";
import { PlatformApiError } from "./api/platformClient";
import { getMrr, listAllSubscriptions, type SubscriptionResponse } from "./api/subscriptions";
import { listUsage, type UsageResponse } from "./api/usage";
import { describeActivity } from "./activityDescriptions";
import { colors } from "../theme";

const ORG_STATUSES = ["TRIAL", "ACTIVE", "SUSPENDED", "ARCHIVED"] as const;
const GROWTH_WINDOW_OPTIONS = [3, 6, 12] as const;
const INACTIVE_AFTER_DAYS = 14;

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

function greeting(): string {
	const hour = new Date().getHours();
	if (hour < 12) return "Good morning";
	if (hour < 17) return "Good afternoon";
	return "Good evening";
}

function timeAgo(iso: string): string {
	const ms = Date.now() - new Date(iso).getTime();
	const hours = Math.round(ms / 3_600_000);
	if (hours < 1) return "just now";
	if (hours < 24) return `${hours} hour${hours === 1 ? "" : "s"} ago`;
	const days = Math.round(hours / 24);
	return `${days} day${days === 1 ? "" : "s"} ago`;
}

/** Buckets by calendar month of createdAt, oldest to newest, for the trailing `months`
 * months including the current one - same "small enough to compute client-side" call the
 * rest of this dashboard already makes rather than adding a backend aggregation endpoint. */
function bucketByMonth(organisations: OrganisationResponse[], months: number): { month: string; count: number }[] {
	const now = new Date();
	const buckets: { key: string; month: string; count: number }[] = [];
	for (let i = months - 1; i >= 0; i--) {
		const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
		buckets.push({ key: `${d.getFullYear()}-${d.getMonth()}`, month: d.toLocaleDateString("en-US", { month: "short" }), count: 0 });
	}
	const byKey = new Map(buckets.map((b) => [b.key, b]));
	for (const org of organisations) {
		const created = new Date(org.createdAt);
		const key = `${created.getFullYear()}-${created.getMonth()}`;
		const bucket = byKey.get(key);
		if (bucket) bucket.count += 1;
	}
	return buckets.map(({ month, count }) => ({ month, count }));
}

/** Same client-side bucketing as bucketByMonth, over PlatformInvoice.amount by issuedAt
 * instead of Organisation.createdAt - this is billed revenue by month, not literal MRR-
 * over-time (there's no MRR snapshot history to chart), see the class doc below. */
function bucketRevenueByMonth(invoices: PlatformInvoiceResponse[], months: number): { month: string; amount: number }[] {
	const now = new Date();
	const buckets: { key: string; month: string; amount: number }[] = [];
	for (let i = months - 1; i >= 0; i--) {
		const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
		buckets.push({ key: `${d.getFullYear()}-${d.getMonth()}`, month: d.toLocaleDateString("en-US", { month: "short" }), amount: 0 });
	}
	const byKey = new Map(buckets.map((b) => [b.key, b]));
	for (const invoice of invoices) {
		const issued = new Date(invoice.issuedAt);
		const key = `${issued.getFullYear()}-${issued.getMonth()}`;
		const bucket = byKey.get(key);
		if (bucket) bucket.amount += invoice.amount;
	}
	return buckets.map(({ month, amount }) => ({ month, amount }));
}

function StatTile({ label, value, hint, onClick }: { label: string; value: string; hint?: string; onClick?: () => void }) {
	return (
		<Paper sx={{ p: 2.5, flex: "1 1 180px", cursor: onClick ? "pointer" : "default" }} onClick={onClick} variant="outlined">
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					{label}
				</Typography>
				<Typography variant="h4" component="div">
					{value}
				</Typography>
				{hint && (
					<Typography variant="caption" color="text.secondary">
						{hint}
					</Typography>
				)}
			</Stack>
		</Paper>
	);
}

/** Landing page for the platform-admin plane - aggregate counts pulled from the same
 * cross-org endpoints OrganisationsPage/PlansPage already use, plus the cross-org
 * subscription/invoice/activity/usage endpoints added alongside this page. No new backend
 * aggregation for the stat tiles/charts - just sums/counts/buckets over lists small enough
 * at this scale to compute client-side, same "don't optimize for scale the product doesn't
 * have yet" call as the rest of this frontend.
 *
 * "Needs attention" also flags inactive organisations (no AuditLog activity in
 * INACTIVE_AFTER_DAYS, ACTIVE orgs only - see BillingService.usageByOrganisation's
 * lastActivityAt) and incomplete onboarding (zero active students, TRIAL/ACTIVE orgs).
 * Onboarding-incomplete is deliberately just the "zero students" half of the ticket's
 * proposed definition - "no modules configured" has no backing data model in this
 * codebase (no Module/OrganisationModule entity exists), flagged back to the ticket. */
export function DashboardPage() {
	const navigate = useNavigate();
	const [organisations, setOrganisations] = useState<OrganisationResponse[] | null>(null);
	const [subscriptions, setSubscriptions] = useState<SubscriptionResponse[] | null>(null);
	const [invoices, setInvoices] = useState<PlatformInvoiceResponse[] | null>(null);
	const [plans, setPlans] = useState<PlanResponse[] | null>(null);
	const [activity, setActivity] = useState<ActivityResponse[] | null>(null);
	const [usage, setUsage] = useState<UsageResponse[] | null>(null);
	const [mrr, setMrr] = useState<number | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [growthWindow, setGrowthWindow] = useState<(typeof GROWTH_WINDOW_OPTIONS)[number]>(6);
	const [revenueWindow, setRevenueWindow] = useState<(typeof GROWTH_WINDOW_OPTIONS)[number]>(6);

	useEffect(() => {
		Promise.all([
			listOrganisations(),
			listAllSubscriptions(),
			listAllInvoices(),
			listPlans(),
			listRecentActivity(),
			getMrr(),
			listUsage(),
		])
			.then(([orgs, subs, inv, pl, act, mrrResponse, usg]) => {
				setOrganisations(orgs);
				setSubscriptions(subs);
				setInvoices(inv);
				setPlans(pl);
				setActivity(act);
				setMrr(mrrResponse.mrr);
				setUsage(usg);
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load dashboard"));
	}, []);

	const loading = !organisations || !subscriptions || !invoices || !plans || !activity || !usage || mrr === null;

	const orgCountByStatus = Object.fromEntries(
		ORG_STATUSES.map((status) => [status, organisations?.filter((org) => org.status === status).length ?? 0]),
	);

	const activeSubscriptions = subscriptions?.filter((sub) => sub.status === "ACTIVE").length ?? 0;
	const outstandingInvoices = invoices?.filter((inv) => inv.status === "ISSUED") ?? [];
	const paidInvoices = invoices?.filter((inv) => inv.status === "PAID") ?? [];
	const outstandingTotal = outstandingInvoices.reduce((sum, inv) => sum + inv.amount, 0);
	const paidTotal = paidInvoices.reduce((sum, inv) => sum + inv.amount, 0);
	const activePlans = plans?.filter((plan) => plan.status === "ACTIVE").length ?? 0;
	const collectionRate = paidTotal + outstandingTotal > 0 ? (paidTotal / (paidTotal + outstandingTotal)) * 100 : null;

	const today = new Date().toISOString().slice(0, 10);
	const overdueInvoices = outstandingInvoices.filter((inv) => inv.dueDate < today);

	const in7Days = Date.now() + 7 * 86_400_000;
	const trialsExpiringSoon = (organisations ?? []).filter(
		(org) => org.status === "TRIAL" && new Date(org.trialEndsAt).getTime() <= in7Days,
	);

	const usageByOrgId = new Map((usage ?? []).map((u) => [u.organisationId, u]));
	const inactiveSince = Date.now() - INACTIVE_AFTER_DAYS * 86_400_000;
	const inactiveOrgs = (organisations ?? []).filter((org) => {
		if (org.status !== "ACTIVE") return false;
		const lastActivityAt = usageByOrgId.get(org.id)?.lastActivityAt;
		return !lastActivityAt || new Date(lastActivityAt).getTime() < inactiveSince;
	});
	const incompleteOnboardingOrgs = (organisations ?? []).filter(
		(org) => (org.status === "TRIAL" || org.status === "ACTIVE") && (usageByOrgId.get(org.id)?.activeStudentCount ?? 0) === 0,
	);

	const needsAttentionCount =
		overdueInvoices.length + trialsExpiringSoon.length + inactiveOrgs.length + incompleteOnboardingOrgs.length;

	const growthData = useMemo(() => bucketByMonth(organisations ?? [], growthWindow), [organisations, growthWindow]);
	const revenueData = useMemo(() => bucketRevenueByMonth(invoices ?? [], revenueWindow), [invoices, revenueWindow]);

	return (
		<Stack spacing={3}>
			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: 2 }}>
				<Stack spacing={0.5}>
					<Typography variant="h4" component="h1">
						{greeting()}
					</Typography>
					<Typography variant="body2" color="text.secondary">
						Here&apos;s what&apos;s happening across Operion.
					</Typography>
				</Stack>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			{!loading && (
				<>
					<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
						<StatTile
							label="Total organisations"
							value={String(organisations!.length)}
							onClick={() => navigate("/platform/organisations")}
						/>
						<StatTile
							label="Active organisations"
							value={String(orgCountByStatus.ACTIVE)}
							hint={organisations!.length ? `${Math.round((orgCountByStatus.ACTIVE / organisations!.length) * 100)}% of total` : undefined}
							onClick={() => navigate("/platform/organisations")}
						/>
						<StatTile
							label="Trial organisations"
							value={String(orgCountByStatus.TRIAL)}
							hint={organisations!.length ? `${Math.round((orgCountByStatus.TRIAL / organisations!.length) * 100)}% of total` : undefined}
							onClick={() => navigate("/platform/organisations")}
						/>
						<StatTile
							label="Outstanding"
							value={currency(outstandingTotal)}
							hint={`${outstandingInvoices.length} invoice${outstandingInvoices.length === 1 ? "" : "s"}`}
							onClick={() => navigate("/platform/invoices")}
						/>
						<StatTile label="Collection rate" value={collectionRate === null ? "—" : `${collectionRate.toFixed(1)}%`} />
						<StatTile
							label="MRR"
							value={currency(mrr!)}
							hint="rate × active students ÷ 12"
							onClick={() => navigate("/platform/subscriptions")}
						/>
					</Stack>

					<Stack direction={{ xs: "column", md: "row" }} spacing={2}>
						<Paper variant="outlined" sx={{ p: 2.5, flex: 1, minWidth: 0 }}>
							<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 1 }}>
								<Box>
									<Typography variant="h6">Organisation growth</Typography>
									<Typography variant="caption" color="text.secondary">
										New organisations created each month
									</Typography>
								</Box>
								<FormControl size="small">
									<Select value={growthWindow} onChange={(e) => setGrowthWindow(Number(e.target.value) as typeof growthWindow)}>
										{GROWTH_WINDOW_OPTIONS.map((months) => (
											<MenuItem key={months} value={months}>
												Last {months} months
											</MenuItem>
										))}
									</Select>
								</FormControl>
							</Stack>
							<Box sx={{ height: 260 }}>
								<ResponsiveContainer width="100%" height="100%">
									<BarChart data={growthData}>
										<CartesianGrid strokeDasharray="3 3" vertical={false} />
										<XAxis dataKey="month" tickLine={false} axisLine={false} />
										<YAxis allowDecimals={false} tickLine={false} axisLine={false} width={28} />
										<RechartsTooltip />
										<Bar dataKey="count" name="New organisations" fill={colors.accent} radius={[4, 4, 0, 0]} />
									</BarChart>
								</ResponsiveContainer>
							</Box>
						</Paper>

						<Paper variant="outlined" sx={{ p: 2.5, flex: 1, minWidth: 0 }}>
							<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 1 }}>
								<Box>
									<Typography variant="h6">Billed revenue</Typography>
									<Typography variant="caption" color="text.secondary">
										Invoice amount by month issued
									</Typography>
								</Box>
								<FormControl size="small">
									<Select value={revenueWindow} onChange={(e) => setRevenueWindow(Number(e.target.value) as typeof revenueWindow)}>
										{GROWTH_WINDOW_OPTIONS.map((months) => (
											<MenuItem key={months} value={months}>
												Last {months} months
											</MenuItem>
										))}
									</Select>
								</FormControl>
							</Stack>
							<Box sx={{ height: 260 }}>
								<ResponsiveContainer width="100%" height="100%">
									<LineChart data={revenueData}>
										<CartesianGrid strokeDasharray="3 3" vertical={false} />
										<XAxis dataKey="month" tickLine={false} axisLine={false} />
										<YAxis allowDecimals={false} tickLine={false} axisLine={false} width={28} />
										<RechartsTooltip formatter={(value) => currency(Number(value))} />
										<Line type="monotone" dataKey="amount" name="Billed revenue" stroke={colors.accent} strokeWidth={2} dot={false} />
									</LineChart>
								</ResponsiveContainer>
							</Box>
						</Paper>
					</Stack>

					<Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ alignItems: "stretch" }}>
						<Paper variant="outlined" sx={{ p: 2.5, flex: 1, minWidth: 0 }}>
							<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 1 }}>
								<Typography variant="h6">Needs attention</Typography>
								<Chip label={needsAttentionCount} size="small" color={needsAttentionCount ? "error" : "default"} />
							</Stack>
							{needsAttentionCount === 0 && (
								<Typography variant="body2" color="text.secondary">
									Nothing needs attention right now.
								</Typography>
							)}
							<Stack spacing={1}>
								{trialsExpiringSoon.slice(0, 6).map((org) => {
									const expired = new Date(org.trialEndsAt).getTime() < Date.now();
									return (
										<Stack key={`trial-${org.id}`} direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
											<Box>
												<Typography variant="body2">{org.name} — trial {expired ? "expired" : "expiring soon"}</Typography>
												<Typography variant="caption" color="text.secondary">
													{expired ? "Ended" : "Ends"} {org.trialEndsAt.slice(0, 10)}
												</Typography>
											</Box>
											<Button size="small" onClick={() => navigate(`/platform/organisations/${org.id}`)}>
												View
											</Button>
										</Stack>
									);
								})}
								{overdueInvoices.slice(0, 6).map((inv) => (
									<Stack key={inv.id} direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
										<Box>
											<Typography variant="body2">Overdue invoice — {currency(inv.amount)}</Typography>
											<Typography variant="caption" color="text.secondary">
												Due {inv.dueDate}
											</Typography>
										</Box>
										<Button size="small" onClick={() => navigate(`/platform/organisations/${inv.organisationId}`)}>
											View
										</Button>
									</Stack>
								))}
								{inactiveOrgs.slice(0, 6).map((org) => (
									<Stack key={`inactive-${org.id}`} direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
										<Box>
											<Typography variant="body2">{org.name} — inactive {INACTIVE_AFTER_DAYS}+ days</Typography>
											<Typography variant="caption" color="text.secondary">
												No activity in the last {INACTIVE_AFTER_DAYS} days
											</Typography>
										</Box>
										<Button size="small" onClick={() => navigate(`/platform/organisations/${org.id}`)}>
											View
										</Button>
									</Stack>
								))}
								{incompleteOnboardingOrgs.slice(0, 6).map((org) => (
									<Stack
										key={`onboarding-${org.id}`}
										direction="row"
										sx={{ justifyContent: "space-between", alignItems: "center" }}
									>
										<Box>
											<Typography variant="body2">{org.name} — onboarding incomplete</Typography>
											<Typography variant="caption" color="text.secondary">
												No active students yet
											</Typography>
										</Box>
										<Button size="small" onClick={() => navigate(`/platform/organisations/${org.id}`)}>
											View
										</Button>
									</Stack>
								))}
							</Stack>
						</Paper>

						<Paper variant="outlined" sx={{ p: 2.5, flex: 1, minWidth: 0 }}>
							<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 1 }}>
								<Typography variant="h6">Recent activity</Typography>
								<Button size="small" onClick={() => navigate("/platform/activity")}>
									View all
								</Button>
							</Stack>
							{activity!.length === 0 && (
								<Typography variant="body2" color="text.secondary">
									Nothing has happened yet.
								</Typography>
							)}
							<Stack spacing={1.25}>
								{activity!.slice(0, 6).map((entry) => (
									<Stack key={entry.id} direction="row" sx={{ justifyContent: "space-between", alignItems: "flex-start", gap: 2 }}>
										<Typography variant="body2">
											{describeActivity(entry, { organisations: organisations!, plans: plans!, subscriptions: subscriptions!, invoices: invoices! })}
										</Typography>
										<Typography variant="caption" color="text.secondary" sx={{ whiteSpace: "nowrap" }}>
											{timeAgo(entry.occurredAt)}
										</Typography>
									</Stack>
								))}
							</Stack>
						</Paper>
					</Stack>

					<Box>
						<Typography variant="h6" sx={{ mb: 1.5 }}>
							Plans
						</Typography>
						<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
							<StatTile label="Active subscriptions" value={String(activeSubscriptions)} />
							<StatTile label="Active plans" value={String(activePlans)} onClick={() => navigate("/platform/plans")} />
							<StatTile label="Collected revenue" value={currency(paidTotal)} onClick={() => navigate("/platform/payments")} />
						</Stack>
					</Box>
				</>
			)}
		</Stack>
	);
}
