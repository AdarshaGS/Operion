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
import EditIcon from "@mui/icons-material/Edit";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import { Can } from "../../auth/Can";
import { DialogErrorAlert } from "../../components/DialogErrorAlert";
import { ApiError } from "../../api/client";
import { loadErrorMessage } from "./loadError";
import { createLeaveType, listLeaveTypes, updateLeaveType, type LeaveTypeResponse } from "../../api/leaveTypes";

export function LeaveTypesPanel() {
	const [leaveTypes, setLeaveTypes] = useState<LeaveTypeResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	// null = creating a new leave type; otherwise the one being edited (its code stays fixed).
	const [editing, setEditing] = useState<LeaveTypeResponse | null>(null);
	const [code, setCode] = useState("");
	const [name, setName] = useState("");
	const [defaultAnnualDays, setDefaultAnnualDays] = useState("");
	const [submitting, setSubmitting] = useState(false);
	// Shown inside the dialog: the page-level alert sits behind the dialog's backdrop.
	const [dialogError, setDialogError] = useState<string | null>(null);

	function refresh() {
		listLeaveTypes()
			.then(setLeaveTypes)
			.catch((err) => setError(loadErrorMessage(err, "Failed to load leave types")));
	}

	useEffect(refresh, []);

	function openCreate() {
		setEditing(null);
		setCode("");
		setName("");
		setDefaultAnnualDays("");
		setDialogError(null);
		setDialogOpen(true);
	}

	function openEdit(leaveType: LeaveTypeResponse) {
		setEditing(leaveType);
		setCode(leaveType.code);
		setName(leaveType.name);
		setDefaultAnnualDays(leaveType.defaultAnnualDays === null ? "" : String(leaveType.defaultAnnualDays));
		setDialogError(null);
		setDialogOpen(true);
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setDialogError(null);
		const days = defaultAnnualDays ? Number(defaultAnnualDays) : null;
		try {
			if (editing) {
				await updateLeaveType(editing.id, { name, defaultAnnualDays: days });
			} else {
				await createLeaveType({ code, name, defaultAnnualDays: days });
			}
			setCode("");
			setName("");
			setDefaultAnnualDays("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : editing ? "Failed to update leave type" : "Failed to create leave type");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Leave types</Typography>
					<Can anyOf={["HR_LEAVE_TYPE_MANAGE"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={openCreate}>
							Add leave type
						</Button>
					</Can>
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
								<Can anyOf={["HR_LEAVE_TYPE_MANAGE"]}>
									<TableCell align="right">Actions</TableCell>
								</Can>
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
									<Can anyOf={["HR_LEAVE_TYPE_MANAGE"]}>
										<TableCell align="right">
											<Tooltip title="Edit">
												<IconButton size="small" aria-label={`Edit ${leaveType.name}`} onClick={() => openEdit(leaveType)}>
													<EditIcon fontSize="small" />
												</IconButton>
											</Tooltip>
										</TableCell>
									</Can>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>{editing ? "Edit leave type" : "Add leave type"}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{dialogError && <DialogErrorAlert>{dialogError}</DialogErrorAlert>}
						<TextField label="Code" placeholder="CASUAL" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus={!editing} disabled={!!editing} helperText={editing ? "Code cannot be changed" : undefined} fullWidth />
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
						{editing ? "Save" : "Add"}
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
