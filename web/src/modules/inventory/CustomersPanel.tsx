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
import { ApiError } from "../../api/client";
import { changeCustomerStatus, createCustomer, listCustomers, type CustomerResponse } from "../../api/customers";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listStudents, type StudentResponse } from "../../api/students";

/** Store-sales master data for the future Sales module (Milestone 7) - a walk-in
 * (no link) or an existing Student linked for purchase-history continuity. Guardian
 * linking is supported by the API but not exposed here yet - there's no list-all-
 * guardians endpoint to pick from (guardians are only reachable per-student today, see
 * ai-context/load-context.md's Parent Portal section). */
export function CustomersPanel() {
	const navigate = useNavigate();
	const [customers, setCustomers] = useState<CustomerResponse[]>([]);
	const [students, setStudents] = useState<StudentResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [studentId, setStudentId] = useState("");
	const [name, setName] = useState("");
	const [phone, setPhone] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listCustomers()
			.then(setCustomers)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load customers"));
	}

	useEffect(refresh, []);
	useEffect(() => {
		listStudents().then(setStudents).catch(() => {});
		listPersons().then(setPersons).catch(() => {});
	}, []);

	function studentLabel(student: StudentResponse): string {
		const person = persons.find((p) => p.id === student.personId);
		return person ? `${person.firstName} ${person.lastName} (${student.admissionNumber})` : student.admissionNumber;
	}

	function applyStudentSelection(value: string) {
		setStudentId(value);
		if (!value) return;
		const student = students.find((s) => s.id === Number(value));
		if (student) {
			setName(studentLabel(student).replace(/\s*\([^)]*\)$/, ""));
		}
	}

	async function handleToggleStatus(customer: CustomerResponse) {
		try {
			await changeCustomerStatus(customer.id, customer.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update customer status");
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createCustomer({ studentId: studentId ? Number(studentId) : null, name, phone: phone || null });
			setStudentId("");
			setName("");
			setPhone("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create customer");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Customers</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add customer
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Name</TableCell>
								<TableCell>Phone</TableCell>
								<TableCell>Linked to</TableCell>
								<TableCell>Status</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{customers.map((customer) => (
								<TableRow key={customer.id}>
									<TableCell>{customer.name}</TableCell>
									<TableCell>{customer.phone ?? "—"}</TableCell>
									<TableCell>
										{customer.studentId != null ? "Student" : customer.guardianId != null ? "Guardian" : "Walk-in"}
									</TableCell>
									<TableCell>
										<Chip label={customer.status} size="small" />
									</TableCell>
									<TableCell>
										<Button size="small" onClick={() => navigate(`/sales/customers/${customer.id}`)}>
											History
										</Button>
										<Button size="small" onClick={() => handleToggleStatus(customer)}>
											{customer.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
										</Button>
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add customer</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							select
							label="Link to student (optional — walk-in if left blank)"
							value={studentId}
							onChange={(e) => applyStudentSelection(e.target.value)}
							fullWidth
						>
							<MenuItem value="">Walk-in (no account)</MenuItem>
							{students.map((student) => (
								<MenuItem key={student.id} value={student.id}>
									{studentLabel(student)}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
						<TextField label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} fullWidth />
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
