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
import { ApiError } from "../../api/client";
import { listGradeLevels, type GradeLevelResponse } from "../../api/gradeLevels";
import {
	approveStudentApplication,
	listStudentApplications,
	rejectStudentApplication,
	submitStudentApplication,
	type StudentApplicationResponse,
} from "../../api/studentApplications";

const EMPTY_FORM = {
	applicantName: "",
	dateOfBirth: "",
	gender: "",
	guardianName: "",
	guardianPhone: "",
	desiredGradeLevelId: "",
	notes: "",
};

/** Pre-admission inquiries (#114) - approving here is a decision, not an admission;
 * admissions staff still run "Admit student" separately afterward, same as an approved
 * job application still needs a manual staff hire. */
export function StudentApplicationsPanel() {
	const [applications, setApplications] = useState<StudentApplicationResponse[]>([]);
	const [gradeLevels, setGradeLevels] = useState<GradeLevelResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState(EMPTY_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listStudentApplications("PENDING")
			.then(setApplications)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load applications"));
	}

	useEffect(refresh, []);
	useEffect(() => {
		listGradeLevels().then(setGradeLevels).catch(() => {});
	}, []);

	function gradeLevelName(id: number | null): string {
		if (!id) return "—";
		return gradeLevels.find((g) => g.id === id)?.name ?? `#${id}`;
	}

	async function handleApprove(id: number) {
		try {
			await approveStudentApplication(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to approve application");
		}
	}

	async function handleReject(id: number) {
		try {
			await rejectStudentApplication(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to reject application");
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await submitStudentApplication({
				applicantName: form.applicantName,
				dateOfBirth: form.dateOfBirth || null,
				gender: form.gender || null,
				guardianName: form.guardianName || null,
				guardianPhone: form.guardianPhone || null,
				desiredGradeLevelId: form.desiredGradeLevelId ? Number(form.desiredGradeLevelId) : null,
				notes: form.notes || null,
			});
			setForm(EMPTY_FORM);
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record application");
		} finally {
			setSubmitting(false);
		}
	}

	function set<K extends keyof typeof EMPTY_FORM>(key: K) {
		return (event: React.ChangeEvent<HTMLInputElement>) => setForm((prev) => ({ ...prev, [key]: event.target.value }));
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Pending applications</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Record application
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{applications.length === 0 && <Alert severity="info">No pending applications.</Alert>}

				{applications.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Applicant</TableCell>
									<TableCell>Desired grade</TableCell>
									<TableCell>Guardian</TableCell>
									<TableCell>Applied</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{applications.map((application) => (
									<TableRow key={application.id}>
										<TableCell>{application.applicantName}</TableCell>
										<TableCell>{gradeLevelName(application.desiredGradeLevelId)}</TableCell>
										<TableCell>
											{application.guardianName ? `${application.guardianName} (${application.guardianPhone ?? "—"})` : "—"}
										</TableCell>
										<TableCell>{new Date(application.appliedAt).toLocaleDateString()}</TableCell>
										<TableCell>
											<Button size="small" onClick={() => handleApprove(application.id)}>
												Approve
											</Button>
											<Button size="small" color="error" onClick={() => handleReject(application.id)}>
												Reject
											</Button>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>Record application</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Applicant name" value={form.applicantName} onChange={set("applicantName")} required autoFocus fullWidth />
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField
								label="Date of birth"
								type="date"
								value={form.dateOfBirth}
								onChange={set("dateOfBirth")}
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
							<TextField label="Gender" value={form.gender} onChange={set("gender")} fullWidth />
						</Box>
						<TextField label="Desired grade level" select value={form.desiredGradeLevelId} onChange={set("desiredGradeLevelId")} fullWidth>
							<MenuItem value="">—</MenuItem>
							{gradeLevels.map((g) => (
								<MenuItem key={g.id} value={g.id}>
									{g.name}
								</MenuItem>
							))}
						</TextField>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField label="Guardian name" value={form.guardianName} onChange={set("guardianName")} fullWidth />
							<TextField label="Guardian phone" value={form.guardianPhone} onChange={set("guardianPhone")} fullWidth />
						</Box>
						<TextField label="Notes" value={form.notes} onChange={set("notes")} multiline rows={2} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
