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
import { PlatformApiError } from "./api/platformClient";
import { listAllInvoices, type PlatformInvoiceResponse } from "./api/platformInvoices";

const STATUS_COLOR: Record<string, "warning" | "success"> = {
	ISSUED: "warning",
	PAID: "success",
};

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

/** Cross-org browse view over GET /api/v1/platform/invoices - same "endpoint existed for
 * the dashboard, nothing let you browse it" gap as SubscriptionsPage. */
export function InvoicesPage() {
	const navigate = useNavigate();
	const [invoices, setInvoices] = useState<PlatformInvoiceResponse[] | null>(null);
	const [organisations, setOrganisations] = useState<OrganisationResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		Promise.all([listAllInvoices(), listOrganisations()])
			.then(([inv, orgs]) => {
				setInvoices(inv);
				setOrganisations(orgs);
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load invoices"));
	}, []);

	const orgName = (id: number) => organisations.find((org) => org.id === id)?.name ?? `#${id}`;

	return (
		<Stack spacing={2}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Billing
				</Typography>
				<Typography variant="h4" component="h1">
					Invoices
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			{invoices && invoices.length === 0 && <Alert severity="info">No invoices yet.</Alert>}

			{invoices && invoices.length > 0 && (
				<TableContainer component={Paper}>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Organisation</TableCell>
								<TableCell>Period</TableCell>
								<TableCell>Amount</TableCell>
								<TableCell>Due date</TableCell>
								<TableCell>Status</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{invoices
								.slice()
								.sort((a, b) => b.periodStart.localeCompare(a.periodStart))
								.map((invoice) => (
									<TableRow
										key={invoice.id}
										hover
										sx={{ cursor: "pointer" }}
										onClick={() => navigate(`/platform/organisations/${invoice.organisationId}`)}
									>
										<TableCell>{orgName(invoice.organisationId)}</TableCell>
										<TableCell>
											{invoice.periodStart} – {invoice.periodEnd}
										</TableCell>
										<TableCell>{currency(invoice.amount)}</TableCell>
										<TableCell>{invoice.dueDate}</TableCell>
										<TableCell>
											<Chip label={invoice.status} size="small" color={STATUS_COLOR[invoice.status] ?? "default"} />
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
