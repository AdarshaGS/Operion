import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { ApiError } from "../../api/client";
import {
	changeConsentPurposeStatus,
	createConsentPurpose,
	listConsentPurposes,
	type ConsentPurposeResponse,
} from "../../api/privacy";

/** The org's own catalog of things a person can agree to - nothing is pre-defined. */
export function ConsentPurposesTab() {
	const [purposes, setPurposes] = useState<ConsentPurposeResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [code, setCode] = useState("");
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");

	function refresh() {
		listConsentPurposes()
			.then(setPurposes)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load consent purposes"));
	}

	useEffect(refresh, []);

	async function handleCreate(event: FormEvent) {
		event.preventDefault();
		try {
			await createConsentPurpose({ code: code.trim(), name: name.trim(), description: description.trim() || null });
			setDialogOpen(false);
			setCode("");
			setName("");
			setDescription("");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create purpose");
		}
	}

	async function handleToggle(purpose: ConsentPurposeResponse) {
		try {
			await changeConsentPurposeStatus(purpose.id, purpose.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update purpose");
		}
	}

	return (
		<Stack spacing={2}>
			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
				<Typography variant="body2" color="text.secondary">
					What people can be asked to agree to, e.g. publishing photos or sharing contact details. Deactivating a
					purpose stops new records but keeps the history.
				</Typography>
				<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
					Add purpose
				</Button>
			</Box>
			{error && <Alert severity="error">{error}</Alert>}
			<Table size="small">
				<TableHead>
					<TableRow>
						<TableCell>Code</TableCell>
						<TableCell>Name</TableCell>
						<TableCell>Description</TableCell>
						<TableCell>Status</TableCell>
						<TableCell />
					</TableRow>
				</TableHead>
				<TableBody>
					{purposes.map((purpose) => (
						<TableRow key={purpose.id}>
							<TableCell>
								<code>{purpose.code}</code>
							</TableCell>
							<TableCell>{purpose.name}</TableCell>
							<TableCell>{purpose.description}</TableCell>
							<TableCell>
								<Chip label={purpose.status} size="small" color={purpose.status === "ACTIVE" ? "success" : "default"} />
							</TableCell>
							<TableCell align="right">
								<Button size="small" onClick={() => handleToggle(purpose)}>
									{purpose.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
								</Button>
							</TableCell>
						</TableRow>
					))}
					{purposes.length === 0 && (
						<TableRow>
							<TableCell colSpan={5}>
								<Typography variant="body2" color="text.secondary">
									No consent purposes defined yet.
								</Typography>
							</TableCell>
						</TableRow>
					)}
				</TableBody>
			</Table>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleCreate} fullWidth maxWidth="xs">
				<DialogTitle>Add consent purpose</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
						<TextField label="Code" helperText="Stable identifier - cannot be changed later" value={code} onChange={(e) => setCode(e.target.value)} required fullWidth />
						<TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} multiline fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained">
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
