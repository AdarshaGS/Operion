import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
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
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { ApiError } from "../../api/client";
import {
	assignInventoryAsset,
	changeInventoryAssetStatus,
	createInventoryAsset,
	listInventoryAssets,
	type InventoryAssetResponse,
} from "../../api/inventoryAssets";
import { listItems, type ItemResponse } from "../../api/items";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listStaffProfiles, type StaffProfileResponse } from "../../api/staffProfiles";

const ASSET_STATUSES = ["ACTIVE", "UNDER_REPAIR", "LOST", "DISPOSED"];

/** High-value/serialized asset tracking (#177) - one row per physical unit, distinct
 * from the bulk-stock Items/StockEntry/StockIssue panels above. Campus-scoped like
 * VehiclesPanel; status changes via the same inline-select pattern VehiclesPanel uses. */
export function AssetsPanel() {
	const [assets, setAssets] = useState<InventoryAssetResponse[]>([]);
	const [items, setItems] = useState<ItemResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [staffOptions, setStaffOptions] = useState<{ personId: number; label: string }[]>([]);
	const [campusId, setCampusId] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [itemId, setItemId] = useState("");
	const [assetTag, setAssetTag] = useState("");
	const [purchaseDate, setPurchaseDate] = useState("");
	const [warrantyExpiryDate, setWarrantyExpiryDate] = useState("");

	const [assignDialogAsset, setAssignDialogAsset] = useState<InventoryAssetResponse | null>(null);
	const [assignPersonId, setAssignPersonId] = useState("");
	const [assignLocation, setAssignLocation] = useState("");

	useEffect(() => {
		listCampuses()
			.then((all) => {
				setCampuses(all);
				if (all.length > 0) setCampusId((current) => current || String(all[0].id));
			})
			.catch(() => {});
		listItems().then(setItems).catch(() => {});
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

	function refresh() {
		if (!campusId) return;
		listInventoryAssets(Number(campusId))
			.then(setAssets)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load assets"));
	}

	useEffect(refresh, [campusId]);

	function itemLabel(id: number): string {
		return items.find((item) => item.id === id)?.name ?? `Item #${id}`;
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		if (!campusId) return;
		setSubmitting(true);
		try {
			await createInventoryAsset({
				itemId: Number(itemId),
				campusId: Number(campusId),
				assetTag,
				purchaseDate: purchaseDate || null,
				warrantyExpiryDate: warrantyExpiryDate || null,
			});
			setItemId("");
			setAssetTag("");
			setPurchaseDate("");
			setWarrantyExpiryDate("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create asset");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleStatusChange(id: number, status: string) {
		try {
			await changeInventoryAssetStatus(id, status);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to change asset status");
		}
	}

	function openAssignDialog(asset: InventoryAssetResponse) {
		setAssignDialogAsset(asset);
		setAssignPersonId(asset.assignedPersonId != null ? String(asset.assignedPersonId) : "");
		setAssignLocation(asset.assignedLocation ?? "");
	}

	async function handleAssign(event: FormEvent) {
		event.preventDefault();
		if (!assignDialogAsset) return;
		setSubmitting(true);
		try {
			await assignInventoryAsset(assignDialogAsset.id, assignPersonId ? Number(assignPersonId) : null, assignLocation || null);
			setAssignDialogAsset(null);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update asset assignment");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Assets</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add asset
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value)} size="small" sx={{ minWidth: 250 }}>
					{campuses.map((campus) => (
						<MenuItem key={campus.id} value={campus.id}>
							{campus.name}
						</MenuItem>
					))}
				</TextField>

				{assets.length === 0 && <Alert severity="info">No assets recorded at this campus.</Alert>}

				{assets.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Asset tag</TableCell>
									<TableCell>Item</TableCell>
									<TableCell>Assigned to</TableCell>
									<TableCell>Warranty expiry</TableCell>
									<TableCell>Status</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{assets.map((asset) => (
									<TableRow key={asset.id}>
										<TableCell>{asset.assetTag}</TableCell>
										<TableCell>{itemLabel(asset.itemId)}</TableCell>
										<TableCell>
											{asset.assignedPersonName ?? asset.assignedLocation ?? "—"}
											{asset.assignedPersonName && asset.assignedLocation ? ` (${asset.assignedLocation})` : ""}
										</TableCell>
										<TableCell>{asset.warrantyExpiryDate ?? "—"}</TableCell>
										<TableCell>
											<TextField
												select
												size="small"
												value={asset.status}
												onChange={(e) => handleStatusChange(asset.id, e.target.value)}
												sx={{ minWidth: 140 }}
											>
												{ASSET_STATUSES.map((status) => (
													<MenuItem key={status} value={status}>
														{status}
													</MenuItem>
												))}
											</TextField>
										</TableCell>
										<TableCell>
											<Button size="small" onClick={() => openAssignDialog(asset)}>
												Assign
											</Button>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add asset</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Item" value={itemId} onChange={(e) => setItemId(e.target.value)} required autoFocus fullWidth>
							{items.map((item) => (
								<MenuItem key={item.id} value={item.id}>
									{item.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Asset tag"
							placeholder="LAPTOP-0001"
							value={assetTag}
							onChange={(e) => setAssetTag(e.target.value)}
							required
							fullWidth
						/>
						<TextField
							label="Purchase date (optional)"
							type="date"
							value={purchaseDate}
							onChange={(e) => setPurchaseDate(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField
							label="Warranty expiry (optional)"
							type="date"
							value={warrantyExpiryDate}
							onChange={(e) => setWarrantyExpiryDate(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !itemId || !assetTag}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={assignDialogAsset != null} onClose={() => setAssignDialogAsset(null)} component="form" onSubmit={handleAssign} fullWidth maxWidth="xs">
				<DialogTitle>Assign {assignDialogAsset?.assetTag}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Assigned person (optional)" value={assignPersonId} onChange={(e) => setAssignPersonId(e.target.value)} fullWidth>
							<MenuItem value="">None</MenuItem>
							{staffOptions.map((staff) => (
								<MenuItem key={staff.personId} value={staff.personId}>
									{staff.label}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Location (optional)"
							placeholder="Staff Room 2"
							value={assignLocation}
							onChange={(e) => setAssignLocation(e.target.value)}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAssignDialogAsset(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
