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
import { changeDepartmentStatus, createDepartment, listDepartments, type DepartmentResponse } from "../../api/departments";
import { ApiError } from "../../api/client";

export function DepartmentsPanel() {
	const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [name, setName] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listDepartments()
			.then(setDepartments)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load departments"));
	}

	useEffect(refresh, []);

	async function handleToggleStatus(department: DepartmentResponse) {
		try {
			await changeDepartmentStatus(department.id, department.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update department status");
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createDepartment({ name });
			setName("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create department");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Departments</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add department
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
							{departments.map((department) => (
								<TableRow key={department.id}>
									<TableCell>{department.name}</TableCell>
									<TableCell>
										<Chip label={department.status} size="small" />
									</TableCell>
									<TableCell>
										<Button size="small" onClick={() => handleToggleStatus(department)}>
											{department.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
										</Button>
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add department</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Name" placeholder="Accounts" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
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
