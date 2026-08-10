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
import { createLeaveType, listLeaveTypes, type LeaveTypeResponse } from "../../api/leaveTypes";

export function LeaveTypesPanel() {
	const [leaveTypes, setLeaveTypes] = useState<LeaveTypeResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [code, setCode] = useState("");
	const [name, setName] = useState("");
	const [defaultAnnualDays, setDefaultAnnualDays] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listLeaveTypes()
			.then(setLeaveTypes)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load leave types"));
	}

	useEffect(refresh, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createLeaveType({ code, name, defaultAnnualDays: defaultAnnualDays ? Number(defaultAnnualDays) : null });
			setCode("");
			setName("");
			setDefaultAnnualDays("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create leave type");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Leave types</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add leave type
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Code</TableCell>
								<TableCell>Name</TableCell>
								<TableCell>Default annual days</TableCell>
								<TableCell>Status</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{leaveTypes.map((leaveType) => (
								<TableRow key={leaveType.id}>
									<TableCell>{leaveType.code}</TableCell>
									<TableCell>{leaveType.name}</TableCell>
									<TableCell>{leaveType.defaultAnnualDays ?? "—"}</TableCell>
									<TableCell>
										<Chip label={leaveType.status} size="small" />
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add leave type</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Code" placeholder="CASUAL" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus fullWidth />
						<TextField label="Name" placeholder="Casual Leave" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
						<TextField
							label="Default annual days"
							type="number"
							value={defaultAnnualDays}
							onChange={(e) => setDefaultAnnualDays(e.target.value)}
							fullWidth
						/>
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
