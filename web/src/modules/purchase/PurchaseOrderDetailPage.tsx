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
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { ApiError } from "../../api/client";
import { listItems, type ItemResponse } from "../../api/items";
import {
	approvePurchaseOrder,
	cancelPurchaseOrder,
	getPurchaseOrder,
	receiveGoods,
	recordPurchaseReturn,
	submitPurchaseOrder,
	type PurchaseOrderDetailResponse,
	type PurchaseOrderLineResponse,
} from "../../api/purchaseOrders";
import { listSuppliers, type SupplierResponse } from "../../api/suppliers";
import { PURCHASE_ORDER_STATUS_COLOR } from "./PurchaseOrdersPanel";

const RETURN_REASONS = ["DEFECTIVE", "EXCESS", "WRONG_ITEM", "OTHER"];

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

export function PurchaseOrderDetailPage() {
	const { orderId } = useParams<{ orderId: string }>();
	const navigate = useNavigate();

	const [order, setOrder] = useState<PurchaseOrderDetailResponse | null>(null);
	const [suppliers, setSuppliers] = useState<SupplierResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [items, setItems] = useState<ItemResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);
	const [actionSubmitting, setActionSubmitting] = useState(false);

	const [receiveDialogOpen, setReceiveDialogOpen] = useState(false);
	const [receiveEntryDate, setReceiveEntryDate] = useState(todayIso());
	const [receiveQuantities, setReceiveQuantities] = useState<Record<number, string>>({});

	const [returnLine, setReturnLine] = useState<PurchaseOrderLineResponse | null>(null);
	const [returnQuantity, setReturnQuantity] = useState("");
	const [returnReason, setReturnReason] = useState("DEFECTIVE");
	const [returnDate, setReturnDate] = useState(todayIso());
	const [returnRemarks, setReturnRemarks] = useState("");

	function refresh() {
		if (!orderId) return;
		getPurchaseOrder(Number(orderId))
			.then(setOrder)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load purchase order"))
			.finally(() => setLoading(false));
	}

	useEffect(refresh, [orderId]);
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

	async function runAction(action: () => Promise<unknown>, failureMessage: string) {
		setActionSubmitting(true);
		try {
			await action();
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : failureMessage);
		} finally {
			setActionSubmitting(false);
		}
	}

	function openReceiveDialog() {
		if (!order) return;
		const initial: Record<number, string> = {};
		for (const line of order.lines) {
			if (line.quantityReceived < line.quantity) {
				initial[line.id] = "";
			}
		}
		setReceiveQuantities(initial);
		setReceiveEntryDate(todayIso());
		setReceiveDialogOpen(true);
	}

	async function handleReceiveSubmit(event: FormEvent) {
		event.preventDefault();
		if (!order) return;
		const lines = Object.entries(receiveQuantities)
			.map(([lineId, quantity]) => ({ lineId: Number(lineId), quantity: Number(quantity) }))
			.filter((line) => line.quantity > 0);
		if (lines.length === 0) return;
		setActionSubmitting(true);
		try {
			await receiveGoods(order.id, { entryDate: receiveEntryDate, lines });
			setReceiveDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record goods receipt");
		} finally {
			setActionSubmitting(false);
		}
	}

	function openReturnDialog(line: PurchaseOrderLineResponse) {
		setReturnLine(line);
		setReturnQuantity("");
		setReturnReason("DEFECTIVE");
		setReturnDate(todayIso());
		setReturnRemarks("");
	}

	async function handleReturnSubmit(event: FormEvent) {
		event.preventDefault();
		if (!order || !returnLine) return;
		setActionSubmitting(true);
		try {
			await recordPurchaseReturn(order.id, returnLine.id, {
				quantity: Number(returnQuantity),
				reason: returnReason,
				returnDate,
				remarks: returnRemarks || null,
			});
			setReturnLine(null);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record purchase return");
		} finally {
			setActionSubmitting(false);
		}
	}

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	if (!order) {
		return <Alert severity="error">{error ?? "Purchase order not found"}</Alert>;
	}

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/purchase")}>
					Back to purchase orders
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				Purchase order #{order.id}
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
						<Stack direction="row" spacing={3}>
							<Typography>
								Supplier: <strong>{supplierLabel(order.supplierId)}</strong>
							</Typography>
							<Typography>
								Campus: <strong>{campusLabel(order.campusId)}</strong>
							</Typography>
							<Typography>
								Expected: <strong>{order.expectedDate}</strong>
							</Typography>
						</Stack>
						<Chip label={order.status} color={PURCHASE_ORDER_STATUS_COLOR[order.status] ?? "default"} />
					</Box>

					<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
						{order.status === "DRAFT" && (
							<Button size="small" variant="outlined" disabled={actionSubmitting} onClick={() => runAction(() => submitPurchaseOrder(order.id), "Failed to submit purchase order")}>
								Submit
							</Button>
						)}
						{order.status === "SUBMITTED" && (
							<Button size="small" variant="outlined" disabled={actionSubmitting} onClick={() => runAction(() => approvePurchaseOrder(order.id), "Failed to approve purchase order")}>
								Approve
							</Button>
						)}
						{(order.status === "APPROVED" || order.status === "PARTIALLY_RECEIVED") && (
							<Button size="small" variant="contained" disabled={actionSubmitting} onClick={openReceiveDialog}>
								Receive goods
							</Button>
						)}
						{(order.status === "DRAFT" || order.status === "SUBMITTED" || order.status === "APPROVED") && (
							<Button size="small" color="error" disabled={actionSubmitting} onClick={() => runAction(() => cancelPurchaseOrder(order.id), "Failed to cancel purchase order")}>
								Cancel
							</Button>
						)}
					</Stack>
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Line items</Typography>
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Item</TableCell>
									<TableCell>Ordered</TableCell>
									<TableCell>Unit cost</TableCell>
									<TableCell>Received</TableCell>
									<TableCell>Returned</TableCell>
									<TableCell></TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{order.lines.map((line) => (
									<TableRow key={line.id}>
										<TableCell>{itemLabel(line.itemId)}</TableCell>
										<TableCell>{line.quantity}</TableCell>
										<TableCell>{line.unitCost}</TableCell>
										<TableCell>{line.quantityReceived}</TableCell>
										<TableCell>{line.quantityReturned}</TableCell>
										<TableCell>
											{line.quantityReceived - line.quantityReturned > 0 && (
												<Button size="small" onClick={() => openReturnDialog(line)}>
													Return
												</Button>
											)}
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				</Stack>
			</Paper>

			<Dialog open={receiveDialogOpen} onClose={() => setReceiveDialogOpen(false)} component="form" onSubmit={handleReceiveSubmit} fullWidth maxWidth="sm">
				<DialogTitle>Receive goods</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Entry date"
							type="date"
							value={receiveEntryDate}
							onChange={(e) => setReceiveEntryDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						{order.lines
							.filter((line) => line.quantityReceived < line.quantity)
							.map((line) => (
								<TextField
									key={line.id}
									label={`${itemLabel(line.itemId)} (max ${line.quantity - line.quantityReceived})`}
									type="number"
									value={receiveQuantities[line.id] ?? ""}
									onChange={(e) => setReceiveQuantities((prev) => ({ ...prev, [line.id]: e.target.value }))}
									fullWidth
								/>
							))}
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setReceiveDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={actionSubmitting}>
						Record receipt
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={returnLine !== null} onClose={() => setReturnLine(null)} component="form" onSubmit={handleReturnSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Return to supplier</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label={`Quantity (max ${returnLine ? returnLine.quantityReceived - returnLine.quantityReturned : 0})`}
							type="number"
							value={returnQuantity}
							onChange={(e) => setReturnQuantity(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<TextField select label="Reason" value={returnReason} onChange={(e) => setReturnReason(e.target.value)} required fullWidth>
							{RETURN_REASONS.map((reason) => (
								<MenuItem key={reason} value={reason}>
									{reason}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Return date"
							type="date"
							value={returnDate}
							onChange={(e) => setReturnDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField label="Remarks" value={returnRemarks} onChange={(e) => setReturnRemarks(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setReturnLine(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={actionSubmitting || !returnQuantity}>
						Record return
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
