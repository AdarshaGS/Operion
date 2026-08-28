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
import { listItems, type ItemResponse } from "../../api/items";
import { createPurchaseOrder, listPurchaseOrders, type PurchaseOrderResponse } from "../../api/purchaseOrders";
import { listSuppliers, type SupplierResponse } from "../../api/suppliers";

export const PURCHASE_ORDER_STATUS_COLOR: Record<string, "default" | "info" | "primary" | "warning" | "success" | "error"> = {
	DRAFT: "default",
	SUBMITTED: "info",
	APPROVED: "primary",
	PARTIALLY_RECEIVED: "warning",
	RECEIVED: "success",
	CANCELLED: "error",
};

interface DraftLine {
	itemId: string;
	quantity: string;
	unitCost: string;
}

function emptyLine(): DraftLine {
	return { itemId: "", quantity: "", unitCost: "" };
}

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

export function PurchaseOrdersPanel() {
	const navigate = useNavigate();
	const [orders, setOrders] = useState<PurchaseOrderResponse[]>([]);
	const [suppliers, setSuppliers] = useState<SupplierResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [items, setItems] = useState<ItemResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [supplierId, setSupplierId] = useState("");
	const [campusId, setCampusId] = useState("");
	const [expectedDate, setExpectedDate] = useState(todayIso());
	const [lines, setLines] = useState<DraftLine[]>([emptyLine()]);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listPurchaseOrders()
			.then(setOrders)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load purchase orders"));
	}

	useEffect(refresh, []);
	useEffect(() => {
		listSuppliers().then(setSuppliers).catch(() => {});
		listCampuses().then(setCampuses).catch(() => {});
		listItems().then(setItems).catch(() => {});
	}, []);

	function supplierLabel(id: number): string {
		return suppliers.find((s) => s.id === id)?.name ?? `Supplier #${id}`;
	}

	function campusLabel(id: number): string {
		return campuses.find((c) => c.id === id)?.name ?? `Campus #${id}`;
	}

	function itemLabel(id: number): string {
		const item = items.find((i) => i.id === id);
		return item ? `${item.code} - ${item.name}` : `Item #${id}`;
	}

	function openDialog() {
		setSupplierId("");
		setCampusId("");
		setExpectedDate(todayIso());
		setLines([emptyLine()]);
		setDialogOpen(true);
	}

	function updateLine(index: number, patch: Partial<DraftLine>) {
		setLines((prev) => prev.map((line, i) => (i === index ? { ...line, ...patch } : line)));
	}

	function removeLine(index: number) {
		setLines((prev) => prev.filter((_, i) => i !== index));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createPurchaseOrder({
				supplierId: Number(supplierId),
				campusId: Number(campusId),
				expectedDate,
				lines: lines.map((line) => ({
					itemId: Number(line.itemId),
					quantity: Number(line.quantity),
					unitCost: Number(line.unitCost),
				})),
			});
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create purchase order");
		} finally {
			setSubmitting(false);
		}
	}

	const canSubmit =
		supplierId !== "" &&
		campusId !== "" &&
		lines.length > 0 &&
		lines.every((line) => line.itemId !== "" && Number(line.quantity) > 0 && line.unitCost !== "");

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Purchase orders</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={openDialog}>
						New purchase order
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{orders.length === 0 && <Alert severity="info">No purchase orders yet.</Alert>}

				{orders.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Supplier</TableCell>
									<TableCell>Campus</TableCell>
									<TableCell>Expected date</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{orders.map((order) => (
									<TableRow key={order.id} hover onClick={() => navigate(`/purchase/orders/${order.id}`)} sx={{ cursor: "pointer" }}>
										<TableCell>{supplierLabel(order.supplierId)}</TableCell>
										<TableCell>{campusLabel(order.campusId)}</TableCell>
										<TableCell>{order.expectedDate}</TableCell>
										<TableCell>
											<Chip label={order.status} size="small" color={PURCHASE_ORDER_STATUS_COLOR[order.status] ?? "default"} />
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>New purchase order</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Supplier" value={supplierId} onChange={(e) => setSupplierId(e.target.value)} required autoFocus fullWidth>
							{suppliers
								.filter((s) => s.status === "ACTIVE")
								.map((supplier) => (
									<MenuItem key={supplier.id} value={supplier.id}>
										{supplier.name}
									</MenuItem>
								))}
						</TextField>
						<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value)} required fullWidth>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Expected date"
							type="date"
							value={expectedDate}
							onChange={(e) => setExpectedDate(e.target.value)}
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
									label="Unit cost"
									type="number"
									value={line.unitCost}
									onChange={(e) => updateLine(index, { unitCost: e.target.value })}
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
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !canSubmit}>
						Create
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
