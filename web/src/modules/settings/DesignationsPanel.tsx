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
import { changeDesignationStatus, createDesignation, listDesignations, type DesignationResponse } from "../../api/designations";
import { ApiError } from "../../api/client";

export function DesignationsPanel() {
	const [designations, setDesignations] = useState<DesignationResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [name, setName] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listDesignations()
			.then(setDesignations)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load designations"));
	}

	useEffect(refresh, []);

	async function handleToggleStatus(designation: DesignationResponse) {
		try {
			await changeDesignationStatus(designation.id, designation.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update designation status");
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createDesignation({ name });
			setName("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create designation");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Designations</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add designation
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Name</TableCell>
								<TableCell>Status</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{designations.map((designation) => (
								<TableRow key={designation.id}>
									<TableCell>{designation.name}</TableCell>
									<TableCell>
										<Chip label={designation.status} size="small" />
									</TableCell>
									<TableCell>
										<Button size="small" onClick={() => handleToggleStatus(designation)}>
											{designation.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
										</Button>
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add designation</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Name" placeholder="Principal" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
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
