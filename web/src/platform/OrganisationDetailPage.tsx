import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import {
	listOrganisationExternalServices,
	setOrganisationExternalServiceEnabled,
	type OrganisationExternalServiceResponse,
} from "./api/organisationExternalServices";
import { changeOrganisationStatus, getOrganisation, type OrganisationResponse } from "./api/organisations";
import { listPlans, type PlanResponse } from "./api/plans";
import { generateInvoice, listInvoices, markInvoicePaid, type PlatformInvoiceResponse } from "./api/platformInvoices";
import { PlatformApiError } from "./api/platformClient";
import { createSubscription, listSubscriptions, type SubscriptionResponse } from "./api/subscriptions";
import { colors } from "../theme";

const ORG_STATUS_COLOR: Record<string, "success" | "warning" | "error" | "default"> = {
	TRIAL: "warning",
	ACTIVE: "success",
	SUSPENDED: "error",
	ARCHIVED: "default",
};

const ALL_ORG_STATUSES = ["TRIAL", "ACTIVE", "SUSPENDED", "ARCHIVED"];

const INVOICE_STATUS_COLOR: Record<string, "success" | "default"> = { ISSUED: "default", PAID: "success" };

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

export function OrganisationDetailPage() {
	const { organisationId } = useParams<{ organisationId: string }>();
	const orgId = Number(organisationId);

	const [organisation, setOrganisation] = useState<OrganisationResponse | null>(null);
	const [plans, setPlans] = useState<PlanResponse[]>([]);
	const [subscriptions, setSubscriptions] = useState<SubscriptionResponse[]>([]);
	const [invoices, setInvoices] = useState<PlatformInvoiceResponse[]>([]);
	const [externalServices, setExternalServices] = useState<OrganisationExternalServiceResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [statusDialogOpen, setStatusDialogOpen] = useState(false);
	const [nextStatus, setNextStatus] = useState("");

	const [subDialogOpen, setSubDialogOpen] = useState(false);
	const [planId, setPlanId] = useState("");
	const [startDate, setStartDate] = useState(todayIso());

	const [invoiceDialogOpen, setInvoiceDialogOpen] = useState(false);
	const [periodStart, setPeriodStart] = useState(todayIso());
	const [periodEnd, setPeriodEnd] = useState(todayIso());
	const [dueDate, setDueDate] = useState(todayIso());

	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listSubscriptions(orgId)
			.then(setSubscriptions)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load subscriptions"));
		listInvoices(orgId)
			.then(setInvoices)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load invoices"));
		listOrganisationExternalServices(orgId)
			.then(setExternalServices)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load integrations"));
	}

	async function handleToggleExternalService(serviceKey: string, enabled: boolean) {
		try {
			const updated = await setOrganisationExternalServiceEnabled(orgId, serviceKey, enabled);
			setExternalServices((prev) => prev.map((service) => (service.serviceKey === serviceKey ? updated : service)));
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to update integration");
		}
	}

	useEffect(() => {
		getOrganisation(orgId)
			.then(setOrganisation)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load organisation"));
		listPlans().then(setPlans).catch(() => {});
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [orgId]);

	function planName(id: number): string {
		return plans.find((p) => p.id === id)?.name ?? `Plan #${id}`;
	}

	const activeSubscription = subscriptions.find((s) => s.status === "ACTIVE") ?? null;

	async function handleSubscribe(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createSubscription(orgId, { planId: Number(planId), startDate });
			setSubDialogOpen(false);
			setPlanId("");
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to start subscription");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleGenerateInvoice(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await generateInvoice(orgId, { periodStart, periodEnd, dueDate });
			setInvoiceDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to generate invoice");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleMarkPaid(id: number) {
		try {
			await markInvoicePaid(id);
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to mark invoice paid");
		}
	}

	async function handleChangeStatus(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			const updated = await changeOrganisationStatus(orgId, nextStatus);
			setOrganisation(updated);
			setStatusDialogOpen(false);
			setNextStatus("");
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to change organisation status");
		} finally {
			setSubmitting(false);
		}
	}

	const availableTransitions = organisation ? ALL_ORG_STATUSES.filter((status) => status !== organisation.status) : [];

	return (
		<Stack spacing={3}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Organisation
				</Typography>
				<Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
					<Typography variant="h4" component="h1">
						{organisation?.name ?? "—"}
					</Typography>
					{organisation && <Chip label={organisation.status} size="small" color={ORG_STATUS_COLOR[organisation.status] ?? "default"} />}
					{availableTransitions.length > 0 && (
						<Button size="small" onClick={() => setStatusDialogOpen(true)}>
							Change status
						</Button>
					)}
				</Box>
				<Typography variant="body2" color="text.secondary">
					{organisation?.legalName} · slug: {organisation?.slug}
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Subscription</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setSubDialogOpen(true)} disabled={plans.length === 0}>
							Start subscription
						</Button>
					</Box>

					{plans.length === 0 && <Alert severity="info">No plans exist yet — add one under Plans first.</Alert>}

					{activeSubscription && (
						<Box sx={{ border: `1px solid ${colors.rule}`, borderRadius: 1, p: 1.5, bgcolor: colors.paperRaised }}>
							<Typography variant="overline" sx={{ color: colors.inkFaint, fontSize: "0.65rem" }}>
								Current plan
							</Typography>
							<Typography variant="h6">
								{planName(activeSubscription.planId)} — {currency(activeSubscription.pricePerStudentPerYear)}/student/year
							</Typography>
							<Typography variant="body2" color="text.secondary">
								Since {activeSubscription.startDate}
							</Typography>
						</Box>
					)}

					{subscriptions.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Plan</TableCell>
										<TableCell>Price / student / year</TableCell>
										<TableCell>Start</TableCell>
										<TableCell>End</TableCell>
										<TableCell>Status</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{subscriptions.map((sub) => (
										<TableRow key={sub.id}>
											<TableCell>{planName(sub.planId)}</TableCell>
											<TableCell>{currency(sub.pricePerStudentPerYear)}</TableCell>
											<TableCell>{sub.startDate}</TableCell>
											<TableCell>{sub.endDate ?? "—"}</TableCell>
											<TableCell>
												<Chip label={sub.status} size="small" color={sub.status === "ACTIVE" ? "success" : "default"} />
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Invoices</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setInvoiceDialogOpen(true)} disabled={!activeSubscription}>
							Generate invoice
						</Button>
					</Box>

					{!activeSubscription && <Alert severity="info">Start a subscription before generating an invoice.</Alert>}
					{invoices.length === 0 && <Alert severity="info">No invoices generated yet.</Alert>}

					{invoices.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Period</TableCell>
										<TableCell>Students billed</TableCell>
										<TableCell>Amount</TableCell>
										<TableCell>Due date</TableCell>
										<TableCell>Status</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{invoices.map((invoice) => (
										<TableRow key={invoice.id}>
											<TableCell>
												{invoice.periodStart} → {invoice.periodEnd}
											</TableCell>
											<TableCell>{invoice.studentCountAtBilling}</TableCell>
											<TableCell>{currency(invoice.amount)}</TableCell>
											<TableCell>{invoice.dueDate}</TableCell>
											<TableCell>
												<Chip label={invoice.status} size="small" color={INVOICE_STATUS_COLOR[invoice.status] ?? "default"} />
											</TableCell>
											<TableCell>
												{invoice.status === "ISSUED" && (
													<Button size="small" onClick={() => handleMarkPaid(invoice.id)}>
														Mark paid
													</Button>
												)}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Integrations</Typography>
					<Typography variant="body2" color="text.secondary">
						Grants this organisation access to configure its own credentials from its own Settings — the platform never
						sees the values it enters.
					</Typography>

					{externalServices.length === 0 && <Alert severity="info">No integrations available yet.</Alert>}

					{externalServices.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Integration</TableCell>
										<TableCell>Enabled</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{externalServices.map((service) => (
										<TableRow key={service.serviceKey}>
											<TableCell>{service.displayName}</TableCell>
											<TableCell>
												<Switch
													checked={service.enabled}
													onChange={(e) => handleToggleExternalService(service.serviceKey, e.target.checked)}
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

			<Dialog open={subDialogOpen} onClose={() => setSubDialogOpen(false)} component="form" onSubmit={handleSubscribe} fullWidth maxWidth="xs">
				<DialogTitle>Start subscription</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Plan" value={planId} onChange={(e) => setPlanId(e.target.value)} required fullWidth>
							{plans.map((plan) => (
								<MenuItem key={plan.id} value={plan.id}>
									{plan.name} — {currency(plan.pricePerStudentPerYear)}/student/year
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Start date"
							type="date"
							value={startDate}
							onChange={(e) => setStartDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setSubDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !planId}>
						Start
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog
				open={invoiceDialogOpen}
				onClose={() => setInvoiceDialogOpen(false)}
				component="form"
				onSubmit={handleGenerateInvoice}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Generate invoice</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Period start"
							type="date"
							value={periodStart}
							onChange={(e) => setPeriodStart(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField
							label="Period end"
							type="date"
							value={periodEnd}
							onChange={(e) => setPeriodEnd(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField
							label="Due date"
							type="date"
							value={dueDate}
							onChange={(e) => setDueDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setInvoiceDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Generate
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog
				open={statusDialogOpen}
				onClose={() => setStatusDialogOpen(false)}
				component="form"
				onSubmit={handleChangeStatus}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Change status</DialogTitle>
				<DialogContent>
					<TextField
						select
						label="New status"
						value={nextStatus}
						onChange={(e) => setNextStatus(e.target.value)}
						required
						fullWidth
						sx={{ mt: 1 }}
					>
						{availableTransitions.map((status) => (
							<MenuItem key={status} value={status}>
								{status}
							</MenuItem>
						))}
					</TextField>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setStatusDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !nextStatus}>
						Confirm
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
