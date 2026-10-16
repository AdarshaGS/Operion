import { useEffect, useMemo, useState, type FormEvent } from "react";
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
import { Can } from "../../auth/Can";
import { DialogErrorAlert } from "../../components/DialogErrorAlert";
import { ApiError } from "../../api/client";
import {
	getItemBalance,
	listItems,
	updateItemDefaultSupplier,
	updateItemReorderLevel,
	updateItemStorageLocation,
	type BalanceResponse,
	type ItemResponse,
} from "../../api/items";
import { recordStockAdjustment, listStockAdjustments, type StockAdjustmentResponse } from "../../api/stockAdjustments";
import { recordStockEntry, listStockEntries, type StockEntryResponse } from "../../api/stockEntries";
import { recordStockIssue, listStockIssues, type StockIssueResponse } from "../../api/stockIssues";
import { recordStockTransfer } from "../../api/stockTransfers";
import { listSuppliers, type SupplierResponse } from "../../api/suppliers";
import { listDepartments, type DepartmentResponse } from "../../api/departments";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listStaffProfiles, type StaffProfileResponse } from "../../api/staffProfiles";
import { StudentPicker } from "../../components/StudentPicker";

type RecipientType = "NONE" | "DEPARTMENT" | "STAFF" | "STUDENT";

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
	const [suppliers, setSuppliers] = useState<SupplierResponse[]>([]);
	const [campusId, setCampusId] = useState("");
	const [balance, setBalance] = useState<BalanceResponse | null>(null);
	const [entries, setEntries] = useState<StockEntryResponse[]>([]);
	const [issues, setIssues] = useState<StockIssueResponse[]>([]);
	const [adjustments, setAdjustments] = useState<StockAdjustmentResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);
	// A rejected save is explained inside its dialog - the page-level alert sits behind the backdrop.
	const [dialogError, setDialogError] = useState<string | null>(null);
	const [reorderLevelInput, setReorderLevelInput] = useState("");
	const [reorderLevelSubmitting, setReorderLevelSubmitting] = useState(false);
	const [storageLocationInput, setStorageLocationInput] = useState("");
	const [storageLocationSubmitting, setStorageLocationSubmitting] = useState(false);
	const [defaultSupplierInput, setDefaultSupplierInput] = useState("");
	const [defaultSupplierSubmitting, setDefaultSupplierSubmitting] = useState(false);

	const [entryDialogOpen, setEntryDialogOpen] = useState(false);
	const [entryQuantity, setEntryQuantity] = useState("");
	const [entryUnitCost, setEntryUnitCost] = useState("");
	const [entryDate, setEntryDate] = useState(todayIso());
	const [entrySource, setEntrySource] = useState("");
	const [entrySupplierId, setEntrySupplierId] = useState("");

	const [issueDialogOpen, setIssueDialogOpen] = useState(false);
	const [issueQuantity, setIssueQuantity] = useState("");
	const [issuedDate, setIssuedDate] = useState(todayIso());
	const [issuedTo, setIssuedTo] = useState("");
	const [purpose, setPurpose] = useState("");
	const [issueUnitCost, setIssueUnitCost] = useState("");
	const [recipientType, setRecipientType] = useState<RecipientType>("NONE");
	const [recipientDepartmentId, setRecipientDepartmentId] = useState("");
	const [recipientStaffPersonId, setRecipientStaffPersonId] = useState("");
	const [recipientStudentId, setRecipientStudentId] = useState<number | "">("");
	const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
	const [staffOptions, setStaffOptions] = useState<{ personId: number; label: string }[]>([]);

	const [adjustmentDialogOpen, setAdjustmentDialogOpen] = useState(false);
	const [quantityDelta, setQuantityDelta] = useState("");
	const [reason, setReason] = useState("COUNT_CORRECTION");
	const [adjustmentDate, setAdjustmentDate] = useState(todayIso());

	const [transferDialogOpen, setTransferDialogOpen] = useState(false);
	const [transferToCampusId, setTransferToCampusId] = useState("");
	const [transferQuantity, setTransferQuantity] = useState("");
	const [transferDate, setTransferDate] = useState(todayIso());
	const [transferRemarks, setTransferRemarks] = useState("");

	useEffect(() => {
		if (!itemId) return;
		listItems()
			.then((items) => {
				const found = items.find((i) => i.id === Number(itemId));
				setItem(found ?? null);
				setReorderLevelInput(found?.reorderLevel != null ? String(found.reorderLevel) : "");
				setStorageLocationInput(found?.storageLocation ?? "");
				setDefaultSupplierInput(found?.defaultSupplierId != null ? String(found.defaultSupplierId) : "");
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load item"))
			.finally(() => setLoading(false));
		listCampuses().then(setCampuses).catch(() => {});
		listSuppliers().then(setSuppliers).catch(() => {});
	}, [itemId]);

	useEffect(() => {
		listDepartments().then(setDepartments).catch(() => {});
		Promise.all([listStaffProfiles(), listPersons()])
			.then(([staffProfiles, persons]: [StaffProfileResponse[], PersonResponse[]]) => {
				const personById = new Map(persons.map((person) => [person.id, person]));
				setStaffOptions(
					staffProfiles
						.filter((staff) => staff.status === "ACTIVE")
						.map((staff) => {
							const person = personById.get(staff.personId);
							const label = person ? `${person.firstName}${person.lastName ? " " + person.lastName : ""}` : `Staff #${staff.personId}`;
							return { personId: staff.personId, label: `${label} (${staff.designationName})` };
						}),
				);
			})
			.catch(() => setStaffOptions([]));
	}, []);

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

	type LedgerRow = { key: string; date: string; type: "ENTRY" | "ISSUE" | "ADJUSTMENT"; quantity: number; detail: string };

	const ledger: LedgerRow[] = useMemo(() => {
		const rows: LedgerRow[] = [
			...entries.map((entry) => ({
				key: `entry-${entry.id}`,
				date: entry.entryDate,
				type: "ENTRY" as const,
				quantity: entry.quantity,
				detail: entry.supplierName ?? entry.source ?? "—",
			})),
			...issues.map((issue) => ({
				key: `issue-${issue.id}`,
				date: issue.issuedDate,
				type: "ISSUE" as const,
				quantity: -issue.quantity,
				detail: issue.departmentName ?? issue.recipientPersonName ?? issue.recipientStudentName ?? issue.issuedTo,
			})),
			...adjustments.map((adjustment) => ({
				key: `adjustment-${adjustment.id}`,
				date: adjustment.adjustmentDate,
				type: "ADJUSTMENT" as const,
				quantity: adjustment.quantityDelta,
				detail: adjustment.reason,
			})),
		];
		return rows.sort((a, b) => b.date.localeCompare(a.date));
	}, [entries, issues, adjustments]);

	const LEDGER_TYPE_COLOR: Record<LedgerRow["type"], "success" | "error" | "default"> = {
		ENTRY: "success",
		ISSUE: "error",
		ADJUSTMENT: "default",
	};

	async function handleRecordEntry(event: FormEvent) {
		event.preventDefault();
		if (!itemId || !campusId) return;
		setSubmitting(true);
		setDialogError(null);
		try {
			await recordStockEntry({
				itemId: Number(itemId),
				campusId: Number(campusId),
				quantity: Number(entryQuantity),
				unitCost: entryUnitCost ? Number(entryUnitCost) : null,
				entryDate,
				source: entrySource || null,
				supplierId: entrySupplierId ? Number(entrySupplierId) : null,
			});
			setEntryQuantity("");
			setEntryUnitCost("");
			setEntrySource("");
			setEntrySupplierId("");
			setEntryDialogOpen(false);
			refreshAll();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : "Failed to record stock entry");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleRecordIssue(event: FormEvent) {
		event.preventDefault();
		if (!itemId || !campusId) return;
		setSubmitting(true);
		setDialogError(null);
		try {
			await recordStockIssue({
				itemId: Number(itemId),
				campusId: Number(campusId),
				quantity: Number(issueQuantity),
				issuedDate,
				issuedTo,
				purpose: purpose || null,
				unitCost: issueUnitCost ? Number(issueUnitCost) : null,
				departmentId: recipientType === "DEPARTMENT" && recipientDepartmentId ? Number(recipientDepartmentId) : null,
				recipientPersonId: recipientType === "STAFF" && recipientStaffPersonId ? Number(recipientStaffPersonId) : null,
				recipientStudentId: recipientType === "STUDENT" && recipientStudentId ? Number(recipientStudentId) : null,
			});
			setIssueQuantity("");
			setIssuedTo("");
			setPurpose("");
			setIssueUnitCost("");
			setRecipientType("NONE");
			setRecipientDepartmentId("");
			setRecipientStaffPersonId("");
			setRecipientStudentId("");
			setIssueDialogOpen(false);
			refreshAll();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : "Failed to record stock issue");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleRecordAdjustment(event: FormEvent) {
		event.preventDefault();
		if (!itemId || !campusId) return;
		setSubmitting(true);
		setDialogError(null);
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
			setDialogError(err instanceof ApiError ? err.message : "Failed to record stock adjustment");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleRecordTransfer(event: FormEvent) {
		event.preventDefault();
		if (!itemId || !campusId || !transferToCampusId) return;
		setSubmitting(true);
		setDialogError(null);
		try {
			await recordStockTransfer({
				itemId: Number(itemId),
				fromCampusId: Number(campusId),
				toCampusId: Number(transferToCampusId),
				quantity: Number(transferQuantity),
				transferDate,
				remarks: transferRemarks || null,
			});
			setTransferToCampusId("");
			setTransferQuantity("");
			setTransferRemarks("");
			setTransferDialogOpen(false);
			refreshAll();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : "Failed to record stock transfer");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleUpdateReorderLevel() {
		if (!itemId) return;
		setReorderLevelSubmitting(true);
		try {
			const updated = await updateItemReorderLevel(Number(itemId), reorderLevelInput ? Number(reorderLevelInput) : null);
			setItem(updated);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update reorder level");
		} finally {
			setReorderLevelSubmitting(false);
		}
	}

	async function handleUpdateStorageLocation() {
		if (!itemId) return;
		setStorageLocationSubmitting(true);
		try {
			const updated = await updateItemStorageLocation(Number(itemId), storageLocationInput || null);
			setItem(updated);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update storage location");
		} finally {
			setStorageLocationSubmitting(false);
		}
	}

	async function handleUpdateDefaultSupplier() {
		if (!itemId) return;
		setDefaultSupplierSubmitting(true);
		try {
			const updated = await updateItemDefaultSupplier(Number(itemId), defaultSupplierInput ? Number(defaultSupplierInput) : null);
			setItem(updated);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update default supplier");
		} finally {
			setDefaultSupplierSubmitting(false);
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
							<Typography variant="h6" sx={{ display: "flex", alignItems: "center", gap: 1 }}>
								Balance: <Chip label={balance.balance} color={balance.balance > 0 ? "success" : "default"} />
								{item?.reorderLevel != null && balance.balance <= item.reorderLevel && (
									<Chip label="Low stock" color="warning" size="small" />
								)}
							</Typography>
						)}
					</Box>

					{!campusId && <Alert severity="info">Pick a campus to view balance and stock activity.</Alert>}

					<Box sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}>
						<TextField
							label="Reorder level (optional)"
							type="number"
							value={reorderLevelInput}
							onChange={(e) => setReorderLevelInput(e.target.value)}
							size="small"
							sx={{ maxWidth: 220 }}
						/>
						<Can anyOf={["INVENTORY_CATALOG_MANAGE"]}>
							<Button
								size="small"
								variant="outlined"
								onClick={handleUpdateReorderLevel}
								disabled={reorderLevelSubmitting || (item?.reorderLevel ?? null) === (reorderLevelInput ? Number(reorderLevelInput) : null)}
							>
								Update
							</Button>
						</Can>
					</Box>

					<Box sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}>
						<TextField
							label="Storage location (optional)"
							value={storageLocationInput}
							onChange={(e) => setStorageLocationInput(e.target.value)}
							size="small"
							sx={{ minWidth: 220 }}
						/>
						<Can anyOf={["INVENTORY_CATALOG_MANAGE"]}>
							<Button
								size="small"
								variant="outlined"
								onClick={handleUpdateStorageLocation}
								disabled={storageLocationSubmitting || (item?.storageLocation ?? "") === storageLocationInput}
							>
								Update
							</Button>
						</Can>
					</Box>

					<Box sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}>
						<TextField
							select
							label="Default supplier (optional)"
							value={defaultSupplierInput}
							onChange={(e) => setDefaultSupplierInput(e.target.value)}
							size="small"
							sx={{ minWidth: 220 }}
						>
							<MenuItem value="">None</MenuItem>
							{suppliers
								.filter((supplier) => supplier.status === "ACTIVE")
								.map((supplier) => (
									<MenuItem key={supplier.id} value={supplier.id}>
										{supplier.name}
									</MenuItem>
								))}
						</TextField>
						<Can anyOf={["INVENTORY_CATALOG_MANAGE"]}>
							<Button
								size="small"
								variant="outlined"
								onClick={handleUpdateDefaultSupplier}
								disabled={
									defaultSupplierSubmitting ||
									(item?.defaultSupplierId != null ? String(item.defaultSupplierId) : "") === defaultSupplierInput
								}
							>
								Update
							</Button>
						</Can>
					</Box>
				</Stack>
			</Paper>

			{campusId && (
				<Paper sx={{ p: 3 }}>
					<Stack spacing={2}>
						<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
							<Typography variant="h6">Stock ledger</Typography>
							<Stack direction="row" spacing={1}>
								<Can anyOf={["INVENTORY_STOCK_MANAGE"]}>
									<Button size="small" startIcon={<AddIcon />} onClick={() => { setDialogError(null); setEntryDialogOpen(true); }}>
										Record entry
									</Button>
									<Button size="small" startIcon={<AddIcon />} onClick={() => { setDialogError(null); setIssueDialogOpen(true); }}>
										Record issue
									</Button>
								</Can>
								<Can anyOf={["INVENTORY_ADJUSTMENT_MANAGE"]}>
									<Button size="small" startIcon={<AddIcon />} onClick={() => { setDialogError(null); setAdjustmentDialogOpen(true); }}>
										Record adjustment
									</Button>
								</Can>
								<Can anyOf={["INVENTORY_STOCK_MANAGE"]}>
									<Button size="small" startIcon={<AddIcon />} onClick={() => { setDialogError(null); setTransferDialogOpen(true); }}>
										Transfer to another campus
									</Button>
								</Can>
							</Stack>
						</Box>

						{ledger.length === 0 && <Alert severity="info">No stock movements recorded.</Alert>}

						{ledger.length > 0 && (
							<TableContainer>
								<Table size="small">
									<TableHead>
										<TableRow>
											<TableCell>Date</TableCell>
											<TableCell>Type</TableCell>
											<TableCell>Quantity</TableCell>
											<TableCell>Detail</TableCell>
										</TableRow>
									</TableHead>
									<TableBody>
										{ledger.map((row) => (
											<TableRow key={row.key}>
												<TableCell>{row.date}</TableCell>
												<TableCell>
													<Chip label={row.type} size="small" color={LEDGER_TYPE_COLOR[row.type]} />
												</TableCell>
												<TableCell>{row.quantity > 0 ? `+${row.quantity}` : row.quantity}</TableCell>
												<TableCell>{row.detail}</TableCell>
											</TableRow>
										))}
									</TableBody>
								</Table>
							</TableContainer>
						)}
					</Stack>
				</Paper>
			)}

			<Dialog open={entryDialogOpen} onClose={() => setEntryDialogOpen(false)} component="form" onSubmit={handleRecordEntry} fullWidth maxWidth="xs">
				<DialogTitle>Record stock entry</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{dialogError && <DialogErrorAlert>{dialogError}</DialogErrorAlert>}
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
						<TextField
							select
							label="Supplier (optional)"
							value={entrySupplierId}
							onChange={(e) => setEntrySupplierId(e.target.value)}
							fullWidth
						>
							<MenuItem value="">None</MenuItem>
							{suppliers
								.filter((supplier) => supplier.status === "ACTIVE")
								.map((supplier) => (
									<MenuItem key={supplier.id} value={supplier.id}>
										{supplier.name}
									</MenuItem>
								))}
						</TextField>
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
						{dialogError && <DialogErrorAlert>{dialogError}</DialogErrorAlert>}
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
						<TextField label="Unit cost (optional)" type="number" value={issueUnitCost} onChange={(e) => setIssueUnitCost(e.target.value)} fullWidth />
						<TextField
							select
							label="Link recipient (optional)"
							value={recipientType}
							onChange={(e) => {
								setRecipientType(e.target.value as RecipientType);
								setRecipientDepartmentId("");
								setRecipientStaffPersonId("");
								setRecipientStudentId("");
							}}
							fullWidth
						>
							<MenuItem value="NONE">None</MenuItem>
							<MenuItem value="DEPARTMENT">Department</MenuItem>
							<MenuItem value="STAFF">Staff member</MenuItem>
							<MenuItem value="STUDENT">Student</MenuItem>
						</TextField>
						{recipientType === "DEPARTMENT" && (
							<TextField
								select
								label="Department"
								value={recipientDepartmentId}
								onChange={(e) => setRecipientDepartmentId(e.target.value)}
								fullWidth
							>
								{departments
									.filter((department) => department.status === "ACTIVE")
									.map((department) => (
										<MenuItem key={department.id} value={department.id}>
											{department.name}
										</MenuItem>
									))}
							</TextField>
						)}
						{recipientType === "STAFF" && (
							<TextField
								select
								label="Staff member"
								value={recipientStaffPersonId}
								onChange={(e) => setRecipientStaffPersonId(e.target.value)}
								fullWidth
							>
								{staffOptions.map((staff) => (
									<MenuItem key={staff.personId} value={staff.personId}>
										{staff.label}
									</MenuItem>
								))}
							</TextField>
						)}
						{recipientType === "STUDENT" && (
							<StudentPicker value={recipientStudentId} onChange={(id) => setRecipientStudentId(id)} fullWidth />
						)}
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
						{dialogError && <DialogErrorAlert>{dialogError}</DialogErrorAlert>}
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

			<Dialog
				open={transferDialogOpen}
				onClose={() => setTransferDialogOpen(false)}
				component="form"
				onSubmit={handleRecordTransfer}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Transfer to another campus</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{dialogError && <DialogErrorAlert>{dialogError}</DialogErrorAlert>}
						<TextField select label="To campus" value={transferToCampusId} onChange={(e) => setTransferToCampusId(e.target.value)} required autoFocus fullWidth>
							{campuses
								.filter((campus) => String(campus.id) !== campusId)
								.map((campus) => (
									<MenuItem key={campus.id} value={campus.id}>
										{campus.name}
									</MenuItem>
								))}
						</TextField>
						<TextField
							label="Quantity"
							type="number"
							value={transferQuantity}
							onChange={(e) => setTransferQuantity(e.target.value)}
							required
							fullWidth
						/>
						<TextField
							label="Transfer date"
							type="date"
							value={transferDate}
							onChange={(e) => setTransferDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField label="Remarks" value={transferRemarks} onChange={(e) => setTransferRemarks(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setTransferDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !transferToCampusId || !transferQuantity}>
						Transfer
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
