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
import AddIcon from "@mui/icons-material/Add";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { ApiError } from "../../api/client";
import { getItemBalance, listItems, type BalanceResponse, type ItemResponse } from "../../api/items";
import { recordStockAdjustment, listStockAdjustments, type StockAdjustmentResponse } from "../../api/stockAdjustments";
import { recordStockEntry, listStockEntries, type StockEntryResponse } from "../../api/stockEntries";
import { recordStockIssue, listStockIssues, type StockIssueResponse } from "../../api/stockIssues";

const ADJUSTMENT_REASONS = ["DAMAGE", "LOSS", "COUNT_CORRECTION", "OTHER"];

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

/** No GET-by-id exists for Item - resolving via list+find, same tradeoff documented for
 * BookDetailPage/RouteDetailPage at this data scale. */
export function ItemDetailPage() {
	const { itemId } = useParams<{ itemId: string }>();
	const navigate = useNavigate();

	const [item, setItem] = useState<ItemResponse | null>(null);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [campusId, setCampusId] = useState("");
	const [balance, setBalance] = useState<BalanceResponse | null>(null);
	const [entries, setEntries] = useState<StockEntryResponse[]>([]);
	const [issues, setIssues] = useState<StockIssueResponse[]>([]);
	const [adjustments, setAdjustments] = useState<StockAdjustmentResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);

	const [entryDialogOpen, setEntryDialogOpen] = useState(false);
	const [entryQuantity, setEntryQuantity] = useState("");
	const [entryUnitCost, setEntryUnitCost] = useState("");
	const [entryDate, setEntryDate] = useState(todayIso());
	const [entrySource, setEntrySource] = useState("");

	const [issueDialogOpen, setIssueDialogOpen] = useState(false);
	const [issueQuantity, setIssueQuantity] = useState("");
	const [issuedDate, setIssuedDate] = useState(todayIso());
	const [issuedTo, setIssuedTo] = useState("");
	const [purpose, setPurpose] = useState("");

	const [adjustmentDialogOpen, setAdjustmentDialogOpen] = useState(false);
	const [quantityDelta, setQuantityDelta] = useState("");
	const [reason, setReason] = useState("COUNT_CORRECTION");
	const [adjustmentDate, setAdjustmentDate] = useState(todayIso());

	useEffect(() => {
		if (!itemId) return;
		listItems()
			.then((items) => {
				const found = items.find((i) => i.id === Number(itemId));
				setItem(found ?? null);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load item"))
			.finally(() => setLoading(false));
		listCampuses().then(setCampuses).catch(() => {});
	}, [itemId]);

	function refreshAll() {
		if (!itemId || !campusId) return;
		getItemBalance(Number(itemId), Number(campusId))
			.then(setBalance)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load balance"));
		listStockEntries(Number(itemId), Number(campusId)).then(setEntries).catch(() => {});
		listStockIssues(Number(itemId), Number(campusId)).then(setIssues).catch(() => {});
		listStockAdjustments(Number(itemId), Number(campusId)).then(setAdjustments).catch(() => {});
	}

	useEffect(refreshAll, [itemId, campusId]);

	async function handleRecordEntry(event: FormEvent) {
		event.preventDefault();
		if (!itemId || !campusId) return;
		setSubmitting(true);
		try {
			await recordStockEntry({
				itemId: Number(itemId),
				campusId: Number(campusId),
				quantity: Number(entryQuantity),
				unitCost: entryUnitCost ? Number(entryUnitCost) : null,
				entryDate,
				source: entrySource || null,
			});
			setEntryQuantity("");
			setEntryUnitCost("");
			setEntrySource("");
			setEntryDialogOpen(false);
			refreshAll();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record stock entry");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleRecordIssue(event: FormEvent) {
		event.preventDefault();
		if (!itemId || !campusId) return;
		setSubmitting(true);
		try {
			await recordStockIssue({
				itemId: Number(itemId),
				campusId: Number(campusId),
				quantity: Number(issueQuantity),
				issuedDate,
				issuedTo,
				purpose: purpose || null,
			});
			setIssueQuantity("");
			setIssuedTo("");
			setPurpose("");
			setIssueDialogOpen(false);
			refreshAll();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record stock issue");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleRecordAdjustment(event: FormEvent) {
		event.preventDefault();
		if (!itemId || !campusId) return;
		setSubmitting(true);
		try {
			await recordStockAdjustment({
				itemId: Number(itemId),
				campusId: Number(campusId),
				quantityDelta: Number(quantityDelta),
				reason,
				adjustmentDate,
			});
			setQuantityDelta("");
			setAdjustmentDialogOpen(false);
			refreshAll();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record stock adjustment");
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

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/inventory")}>
					Back to inventory
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				{item?.name ?? `Item #${itemId}`}
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value)} sx={{ minWidth: 250 }}>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						{campusId && balance && (
							<Typography variant="h6">
								Balance: <Chip label={balance.balance} color={balance.balance > 0 ? "success" : "default"} />
							</Typography>
						)}
					</Box>

					{!campusId && <Alert severity="info">Pick a campus to view balance and stock activity.</Alert>}
				</Stack>
			</Paper>

			{campusId && (
				<>
					<Paper sx={{ p: 3 }}>
						<Stack spacing={2}>
							<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
								<Typography variant="h6">Stock entries (receipts)</Typography>
								<Button size="small" startIcon={<AddIcon />} onClick={() => setEntryDialogOpen(true)}>
									Record entry
								</Button>
							</Box>

							{entries.length === 0 && <Alert severity="info">No entries recorded.</Alert>}

							{entries.length > 0 && (
								<TableContainer>
									<Table size="small">
										<TableHead>
											<TableRow>
												<TableCell>Date</TableCell>
												<TableCell>Quantity</TableCell>
												<TableCell>Unit cost</TableCell>
												<TableCell>Source</TableCell>
											</TableRow>
										</TableHead>
										<TableBody>
											{entries.map((entry) => (
												<TableRow key={entry.id}>
													<TableCell>{entry.entryDate}</TableCell>
													<TableCell>{entry.quantity}</TableCell>
													<TableCell>{entry.unitCost ?? "—"}</TableCell>
													<TableCell>{entry.source ?? "—"}</TableCell>
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
								<Typography variant="h6">Stock issues</Typography>
								<Button size="small" startIcon={<AddIcon />} onClick={() => setIssueDialogOpen(true)}>
									Record issue
								</Button>
							</Box>

							{issues.length === 0 && <Alert severity="info">No issues recorded.</Alert>}

							{issues.length > 0 && (
								<TableContainer>
									<Table size="small">
										<TableHead>
											<TableRow>
												<TableCell>Date</TableCell>
												<TableCell>Quantity</TableCell>
												<TableCell>Issued to</TableCell>
												<TableCell>Purpose</TableCell>
											</TableRow>
										</TableHead>
										<TableBody>
											{issues.map((issue) => (
												<TableRow key={issue.id}>
													<TableCell>{issue.issuedDate}</TableCell>
													<TableCell>{issue.quantity}</TableCell>
													<TableCell>{issue.issuedTo}</TableCell>
													<TableCell>{issue.purpose ?? "—"}</TableCell>
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
								<Typography variant="h6">Stock adjustments</Typography>
								<Button size="small" startIcon={<AddIcon />} onClick={() => setAdjustmentDialogOpen(true)}>
									Record adjustment
								</Button>
							</Box>

							{adjustments.length === 0 && <Alert severity="info">No adjustments recorded.</Alert>}

							{adjustments.length > 0 && (
								<TableContainer>
									<Table size="small">
										<TableHead>
											<TableRow>
												<TableCell>Date</TableCell>
												<TableCell>Delta</TableCell>
												<TableCell>Reason</TableCell>
											</TableRow>
										</TableHead>
										<TableBody>
											{adjustments.map((adjustment) => (
												<TableRow key={adjustment.id}>
													<TableCell>{adjustment.adjustmentDate}</TableCell>
													<TableCell>{adjustment.quantityDelta}</TableCell>
													<TableCell>{adjustment.reason}</TableCell>
												</TableRow>
											))}
										</TableBody>
									</Table>
								</TableContainer>
							)}
						</Stack>
					</Paper>
				</>
			)}

			<Dialog open={entryDialogOpen} onClose={() => setEntryDialogOpen(false)} component="form" onSubmit={handleRecordEntry} fullWidth maxWidth="xs">
				<DialogTitle>Record stock entry</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Quantity"
							type="number"
							value={entryQuantity}
							onChange={(e) => setEntryQuantity(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<TextField label="Unit cost" type="number" value={entryUnitCost} onChange={(e) => setEntryUnitCost(e.target.value)} fullWidth />
						<TextField
							label="Entry date"
							type="date"
							value={entryDate}
							onChange={(e) => setEntryDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField label="Source" placeholder="Vendor / donation / transfer" value={entrySource} onChange={(e) => setEntrySource(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setEntryDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Record
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={issueDialogOpen} onClose={() => setIssueDialogOpen(false)} component="form" onSubmit={handleRecordIssue} fullWidth maxWidth="xs">
				<DialogTitle>Record stock issue</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Quantity"
							type="number"
							value={issueQuantity}
							onChange={(e) => setIssueQuantity(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<TextField
							label="Issued date"
							type="date"
							value={issuedDate}
							onChange={(e) => setIssuedDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField label="Issued to" placeholder="Class 5B" value={issuedTo} onChange={(e) => setIssuedTo(e.target.value)} required fullWidth />
						<TextField label="Purpose" value={purpose} onChange={(e) => setPurpose(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setIssueDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Record
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog
				open={adjustmentDialogOpen}
				onClose={() => setAdjustmentDialogOpen(false)}
				component="form"
				onSubmit={handleRecordAdjustment}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Record stock adjustment</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Quantity delta"
							type="number"
							placeholder="Positive to add, negative to remove"
							value={quantityDelta}
							onChange={(e) => setQuantityDelta(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<TextField select label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required fullWidth>
							{ADJUSTMENT_REASONS.map((r) => (
								<MenuItem key={r} value={r}>
									{r}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Adjustment date"
							type="date"
							value={adjustmentDate}
							onChange={(e) => setAdjustmentDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAdjustmentDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !quantityDelta}>
						Record
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
