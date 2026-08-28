import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import PrintIcon from "@mui/icons-material/Print";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { ApiError } from "../../api/client";
import { listCustomers, type CustomerResponse } from "../../api/customers";
import { listItems, type ItemResponse } from "../../api/items";
import { getSale, recordSalePayment, type SaleDetailResponse } from "../../api/sales";
import { SALE_STATUS_COLOR } from "./SalesPanel";

const PAYMENT_METHODS = ["CASH", "CARD", "UPI", "CHEQUE", "BANK_TRANSFER"];

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

export function SaleDetailPage() {
	const { saleId } = useParams<{ saleId: string }>();
	const navigate = useNavigate();

	const [sale, setSale] = useState<SaleDetailResponse | null>(null);
	const [customers, setCustomers] = useState<CustomerResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [items, setItems] = useState<ItemResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);

	const [paymentDialogOpen, setPaymentDialogOpen] = useState(false);
	const [paymentMethod, setPaymentMethod] = useState("CASH");
	const [paymentAmount, setPaymentAmount] = useState("");
	const [paidAt, setPaidAt] = useState(todayIso());

	function refresh() {
		if (!saleId) return;
		getSale(Number(saleId))
			.then(setSale)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load sale"))
			.finally(() => setLoading(false));
	}

	useEffect(refresh, [saleId]);
	useEffect(() => {
		listCustomers().then(setCustomers).catch(() => {});
		listCampuses().then(setCampuses).catch(() => {});
		listItems().then(setItems).catch(() => {});
	}, []);

	function customerLabel(id: number): string {
		return customers.find((c) => c.id === id)?.name ?? `Customer #${id}`;
	}

	function campusLabel(id: number): string {
		return campuses.find((c) => c.id === id)?.name ?? `Campus #${id}`;
	}

	function itemLabel(id: number): string {
		const item = items.find((i) => i.id === id);
		return item ? `${item.code} - ${item.name}` : `Item #${id}`;
	}

	function openPaymentDialog() {
		if (!sale) return;
		setPaymentMethod("CASH");
		setPaymentAmount((sale.totalAmount - sale.amountPaid).toFixed(2));
		setPaidAt(todayIso());
		setPaymentDialogOpen(true);
	}

	async function handlePaymentSubmit(event: FormEvent) {
		event.preventDefault();
		if (!sale) return;
		setSubmitting(true);
		try {
			await recordSalePayment(sale.id, { paymentMethod, amount: Number(paymentAmount), paidAt });
			setPaymentDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record payment");
		} finally {
			setSubmitting(false);
		}
	}

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	if (!sale) {
		return <Alert severity="error">{error ?? "Sale not found"}</Alert>;
	}

	const outstanding = sale.totalAmount - sale.amountPaid;

	return (
		<Stack spacing={2}>
			<Box sx={{ display: "flex", justifyContent: "space-between" }} className="no-print">
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/sales")}>
					Back to sales
				</Button>
				<Button startIcon={<PrintIcon />} onClick={() => window.print()}>
					Print receipt
				</Button>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
						<Box>
							<Typography variant="h4" component="h1">
								Receipt {sale.receiptNumber}
							</Typography>
							<Typography color="text.secondary">{sale.saleDate}</Typography>
						</Box>
						<Chip label={sale.status} color={SALE_STATUS_COLOR[sale.status] ?? "default"} />
					</Box>

					<Stack direction="row" spacing={3}>
						<Typography>
							Customer: <strong>{customerLabel(sale.customerId)}</strong>
						</Typography>
						<Typography>
							Campus: <strong>{campusLabel(sale.campusId)}</strong>
						</Typography>
					</Stack>

					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Item</TableCell>
									<TableCell>Quantity</TableCell>
									<TableCell>Unit price</TableCell>
									<TableCell>Line total</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{sale.lines.map((line) => (
									<TableRow key={line.id}>
										<TableCell>{itemLabel(line.itemId)}</TableCell>
										<TableCell>{line.quantity}</TableCell>
										<TableCell>{line.unitPrice}</TableCell>
										<TableCell>{line.lineTotal}</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>

					<Stack spacing={0.5} sx={{ alignItems: "flex-end" }}>
						<Typography>Total: {sale.totalAmount}</Typography>
						<Typography>Paid: {sale.amountPaid}</Typography>
						<Typography variant="subtitle1">Outstanding: {outstanding.toFixed(2)}</Typography>
					</Stack>

					{outstanding > 0 && (
						<Box className="no-print">
							<Button variant="contained" onClick={openPaymentDialog}>
								Record payment
							</Button>
						</Box>
					)}
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }} className="no-print">
				<Stack spacing={2}>
					<Typography variant="h6">Payments</Typography>
					{sale.payments.length === 0 && <Alert severity="info">No payments recorded yet.</Alert>}
					{sale.payments.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Date</TableCell>
										<TableCell>Method</TableCell>
										<TableCell>Amount</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{sale.payments.map((payment) => (
										<TableRow key={payment.id}>
											<TableCell>{payment.paidAt}</TableCell>
											<TableCell>{payment.paymentMethod}</TableCell>
											<TableCell>{payment.amount}</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Dialog open={paymentDialogOpen} onClose={() => setPaymentDialogOpen(false)} component="form" onSubmit={handlePaymentSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Record payment</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Method" value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)} required fullWidth>
							{PAYMENT_METHODS.map((method) => (
								<MenuItem key={method} value={method}>
									{method}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Amount"
							type="number"
							value={paymentAmount}
							onChange={(e) => setPaymentAmount(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<TextField
							label="Paid on"
							type="date"
							value={paidAt}
							onChange={(e) => setPaidAt(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setPaymentDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !paymentAmount}>
						Record
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
