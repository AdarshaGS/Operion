import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import IconButton from "@mui/material/IconButton";
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
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { ApiError } from "../../api/client";
import { listCustomers, type CustomerResponse } from "../../api/customers";
import { listItems, type ItemResponse } from "../../api/items";
import { createSale, listSales, type SaleResponse } from "../../api/sales";
import { CustomerPicker } from "../../components/CustomerPicker";

export const SALE_STATUS_COLOR: Record<string, "default" | "info" | "primary" | "warning" | "success" | "error"> = {
	COMPLETED: "warning",
	PARTIALLY_PAID: "info",
	PAID: "success",
};

interface DraftLine {
	itemId: string;
	quantity: string;
	unitPrice: string;
}

function emptyLine(): DraftLine {
	return { itemId: "", quantity: "", unitPrice: "" };
}

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

export function SalesPanel() {
	const navigate = useNavigate();
	const [sales, setSales] = useState<SaleResponse[]>([]);
	const [customers, setCustomers] = useState<CustomerResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [items, setItems] = useState<ItemResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [customerId, setCustomerId] = useState<number | "">("");
	const [campusId, setCampusId] = useState("");
	const [saleDate, setSaleDate] = useState(todayIso());
	const [lines, setLines] = useState<DraftLine[]>([emptyLine()]);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listSales()
			.then(setSales)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load sales"));
	}

	useEffect(refresh, []);
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

	function openDialog() {
		setCustomerId("");
		setCampusId("");
		setSaleDate(todayIso());
		setLines([emptyLine()]);
		setDialogOpen(true);
	}

	function updateLine(index: number, patch: Partial<DraftLine>) {
		setLines((prev) => prev.map((line, i) => (i === index ? { ...line, ...patch } : line)));
	}

	function removeLine(index: number) {
		setLines((prev) => prev.filter((_, i) => i !== index));
	}

	const total = lines.reduce((sum, line) => sum + (Number(line.quantity) || 0) * (Number(line.unitPrice) || 0), 0);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createSale({
				customerId: Number(customerId),
				campusId: Number(campusId),
				saleDate,
				lines: lines.map((line) => ({
					itemId: Number(line.itemId),
					quantity: Number(line.quantity),
					unitPrice: Number(line.unitPrice),
				})),
			});
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create sale");
		} finally {
			setSubmitting(false);
		}
	}

	const canSubmit =
		customerId !== "" &&
		campusId !== "" &&
		lines.length > 0 &&
		lines.every((line) => line.itemId !== "" && Number(line.quantity) > 0 && line.unitPrice !== "");

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Sales</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={openDialog}>
						New sale
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{sales.length === 0 && <Alert severity="info">No sales yet.</Alert>}

				{sales.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Receipt #</TableCell>
									<TableCell>Customer</TableCell>
									<TableCell>Campus</TableCell>
									<TableCell>Date</TableCell>
									<TableCell>Total</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{sales.map((sale) => (
									<TableRow key={sale.id} hover onClick={() => navigate(`/sales/${sale.id}`)} sx={{ cursor: "pointer" }}>
										<TableCell>{sale.receiptNumber}</TableCell>
										<TableCell>{customerLabel(sale.customerId)}</TableCell>
										<TableCell>{campusLabel(sale.campusId)}</TableCell>
										<TableCell>{sale.saleDate}</TableCell>
										<TableCell>{sale.totalAmount}</TableCell>
										<TableCell>
											<Chip label={sale.status} size="small" color={SALE_STATUS_COLOR[sale.status] ?? "default"} />
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>New sale</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<CustomerPicker value={customerId} onChange={setCustomerId} required fullWidth />
						<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value)} required fullWidth>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Sale date"
							type="date"
							value={saleDate}
							onChange={(e) => setSaleDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>

						<Typography variant="subtitle2">Line items</Typography>
						{lines.map((line, index) => (
							<Stack key={index} direction="row" spacing={1} sx={{ alignItems: "center" }}>
								<TextField
									select
									label="Item"
									value={line.itemId}
									onChange={(e) => updateLine(index, { itemId: e.target.value })}
									required
									sx={{ flex: 2 }}
								>
									{items.map((item) => (
										<MenuItem key={item.id} value={item.id}>
											{itemLabel(item.id)}
										</MenuItem>
									))}
								</TextField>
								<TextField
									label="Quantity"
									type="number"
									value={line.quantity}
									onChange={(e) => updateLine(index, { quantity: e.target.value })}
									required
									sx={{ flex: 1 }}
								/>
								<TextField
									label="Unit price"
									type="number"
									value={line.unitPrice}
									onChange={(e) => updateLine(index, { unitPrice: e.target.value })}
									required
									sx={{ flex: 1 }}
								/>
								<IconButton size="small" onClick={() => removeLine(index)} disabled={lines.length === 1} aria-label="Remove line">
									<DeleteIcon fontSize="small" />
								</IconButton>
							</Stack>
						))}
						<Button size="small" startIcon={<AddIcon />} onClick={() => setLines((prev) => [...prev, emptyLine()])} sx={{ alignSelf: "start" }}>
							Add line
						</Button>

						<Typography variant="subtitle1" sx={{ textAlign: "right" }}>
							Total: {total.toFixed(2)}
						</Typography>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !canSubmit}>
						Complete sale
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
