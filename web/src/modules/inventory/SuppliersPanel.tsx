import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
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
import { ApiError } from "../../api/client";
import { changeSupplierStatus, createSupplier, listSuppliers, type SupplierResponse } from "../../api/suppliers";

/** Vendor address book, not a procurement workflow - see Supplier's class doc. Same
 * toggle-both-ways status pattern as DepartmentsPanel. */
export function SuppliersPanel() {
	const [suppliers, setSuppliers] = useState<SupplierResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [name, setName] = useState("");
	const [contactPerson, setContactPerson] = useState("");
	const [phone, setPhone] = useState("");
	const [email, setEmail] = useState("");
	const [address, setAddress] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listSuppliers()
			.then(setSuppliers)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load suppliers"));
	}

	useEffect(refresh, []);

	async function handleToggleStatus(supplier: SupplierResponse) {
		try {
			await changeSupplierStatus(supplier.id, supplier.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update supplier status");
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createSupplier({
				name,
				contactPerson: contactPerson || null,
				phone: phone || null,
				email: email || null,
				address: address || null,
			});
			setName("");
			setContactPerson("");
			setPhone("");
			setEmail("");
			setAddress("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create supplier");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Suppliers</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add supplier
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Name</TableCell>
								<TableCell>Contact person</TableCell>
								<TableCell>Phone</TableCell>
								<TableCell>Email</TableCell>
								<TableCell>Status</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{suppliers.map((supplier) => (
								<TableRow key={supplier.id}>
									<TableCell>{supplier.name}</TableCell>
									<TableCell>{supplier.contactPerson ?? "—"}</TableCell>
									<TableCell>{supplier.phone ?? "—"}</TableCell>
									<TableCell>{supplier.email ?? "—"}</TableCell>
									<TableCell>
										<Chip label={supplier.status} size="small" />
									</TableCell>
									<TableCell>
										<Button size="small" onClick={() => handleToggleStatus(supplier)}>
											{supplier.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
										</Button>
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add supplier</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
						<TextField label="Contact person" value={contactPerson} onChange={(e) => setContactPerson(e.target.value)} fullWidth />
						<TextField label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} fullWidth />
						<TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} fullWidth />
						<TextField label="Address" value={address} onChange={(e) => setAddress(e.target.value)} multiline rows={2} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
