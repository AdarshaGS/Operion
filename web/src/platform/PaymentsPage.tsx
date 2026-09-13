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
import { listAllInvoices, type PlatformInvoiceResponse } from "./api/platformInvoices";

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

/** There's no Payment entity distinct from PlatformInvoice yet - invoices are marked paid
 * manually, no gateway reconciliation for platform billing (see GitHub issue for the
 * ticket tracking a real payments ledger). Until then, "Payments" is just PAID invoices,
 * sorted by when they were paid. */
export function PaymentsPage() {
	const navigate = useNavigate();
	const [invoices, setInvoices] = useState<PlatformInvoiceResponse[] | null>(null);
	const [organisations, setOrganisations] = useState<OrganisationResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		Promise.all([listAllInvoices(), listOrganisations()])
			.then(([inv, orgs]) => {
				setInvoices(inv.filter((invoice) => invoice.status === "PAID"));
				setOrganisations(orgs);
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load payments"));
	}, []);

	const orgName = (id: number) => organisations.find((org) => org.id === id)?.name ?? `#${id}`;

	return (
		<Stack spacing={2}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Billing
				</Typography>
				<Typography variant="h4" component="h1">
					Payments
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{invoices && invoices.length === 0 && <Alert severity="info">No payments recorded yet.</Alert>}

			{invoices && invoices.length > 0 && (
				<TableContainer component={Paper}>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Organisation</TableCell>
								<TableCell>Amount</TableCell>
								<TableCell>Paid on</TableCell>
								<TableCell>For period</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{invoices
								.slice()
								.sort((a, b) => (b.paidAt ?? "").localeCompare(a.paidAt ?? ""))
								.map((invoice) => (
									<TableRow
										key={invoice.id}
										hover
										sx={{ cursor: "pointer" }}
										onClick={() => navigate(`/platform/organisations/${invoice.organisationId}`)}
									>
										<TableCell>{orgName(invoice.organisationId)}</TableCell>
										<TableCell>{currency(invoice.amount)}</TableCell>
										<TableCell>{invoice.paidAt ? new Date(invoice.paidAt).toLocaleDateString() : "—"}</TableCell>
										<TableCell>
											{invoice.periodStart} – {invoice.periodEnd}
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
