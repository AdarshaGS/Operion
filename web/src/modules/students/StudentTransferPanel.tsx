import { useEffect, useState, type FormEvent } from "react";
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
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { listTransferRequests, raiseTransferRequest, type TransferRequestResponse } from "../../api/transferRequests";

export function StudentTransferPanel({ studentId }: { studentId: number }) {
	const [requests, setRequests] = useState<TransferRequestResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [open, setOpen] = useState(false);
	const [toCampusId, setToCampusId] = useState("");
	const [reason, setReason] = useState("");
	const [submitting, setSubmitting] = useState(false);
	const [formError, setFormError] = useState<string | null>(null);

	function refresh() {
		listTransferRequests({ studentId })
			.then(setRequests)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load transfer requests"));
	}

	useEffect(refresh, [studentId]);
	useEffect(() => {
		listCampuses().then(setCampuses).catch(() => {});
	}, []);

	function campusName(id: number): string {
		return campuses.find((c) => c.id === id)?.name ?? `Campus #${id}`;
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setFormError(null);
		setSubmitting(true);
		try {
			await raiseTransferRequest(studentId, { toCampusId: Number(toCampusId), reason: reason || null });
			setOpen(false);
			setToCampusId("");
			setReason("");
			refresh();
		} catch (err) {
			setFormError(err instanceof ApiError ? err.message : "Failed to raise transfer request");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Campus transfers</Typography>
					<Can anyOf={["STUDENT_TRANSFER_MANAGE"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
							Request transfer
						</Button>
					</Can>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{requests.length === 0 && !error && <Alert severity="info">No transfer requests yet.</Alert>}

				{requests.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>From</TableCell>
									<TableCell>To</TableCell>
									<TableCell>Reason</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{requests.map((request) => (
									<TableRow key={request.id}>
										<TableCell>{campusName(request.fromCampusId)}</TableCell>
										<TableCell>{campusName(request.toCampusId)}</TableCell>
										<TableCell>{request.reason ?? "—"}</TableCell>
										<TableCell>
											<Chip
												label={request.status}
												size="small"
												color={request.status === "APPROVED" ? "success" : request.status === "REJECTED" ? "error" : "default"}
											/>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={open} onClose={() => setOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Request campus transfer</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{formError && <Alert severity="error">{formError}</Alert>}
						<TextField
							select
							label="Destination campus"
							value={toCampusId}
							onChange={(e) => setToCampusId(e.target.value)}
							required
							fullWidth
						>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} multiline rows={2} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !toCampusId}>
						Submit
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
