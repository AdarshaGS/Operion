import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
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
import { createPeriod, deletePeriod, listPeriods, updatePeriod, type PeriodResponse } from "../../api/timetable";
import { Can } from "../../auth/Can";

/** The org's school-day structure - ordered periods and break slots. Configured, not
 * hardcoded: every school defines its own number of periods and times. */
export function PeriodsPanel() {
	const [periods, setPeriods] = useState<PeriodResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [editing, setEditing] = useState<PeriodResponse | null>(null);
	const [ordinal, setOrdinal] = useState("");
	const [label, setLabel] = useState("");
	const [startTime, setStartTime] = useState("09:00");
	const [endTime, setEndTime] = useState("09:45");
	const [breakSlot, setBreakSlot] = useState(false);

	function refresh() {
		listPeriods()
			.then(setPeriods)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load periods"));
	}

	useEffect(refresh, []);

	function openCreate() {
		setEditing(null);
		setOrdinal(String(periods.length === 0 ? 1 : Math.max(...periods.map((p) => p.ordinal)) + 1));
		setLabel("");
		setBreakSlot(false);
		// Default the next slot to start where the previous one ended.
		const last = periods[periods.length - 1];
		setStartTime(last ? last.endTime.slice(0, 5) : "09:00");
		setEndTime(last ? last.endTime.slice(0, 5) : "09:45");
		setError(null);
		setDialogOpen(true);
	}

	function openEdit(period: PeriodResponse) {
		setEditing(period);
		setOrdinal(String(period.ordinal));
		setLabel(period.label);
		setStartTime(period.startTime.slice(0, 5));
		setEndTime(period.endTime.slice(0, 5));
		setBreakSlot(period.breakSlot);
		setError(null);
		setDialogOpen(true);
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setError(null);
		try {
			const body = { label, startTime, endTime, breakSlot };
			if (editing) {
				await updatePeriod(editing.id, body);
			} else {
				await createPeriod({ ...body, ordinal: Number(ordinal) });
			}
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save period");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleDelete(id: number) {
		setError(null);
		try {
			await deletePeriod(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to delete period");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">School day periods</Typography>
					<Can anyOf={["TIMETABLE_MANAGE"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={openCreate}>
							Add period
						</Button>
					</Can>
				</Box>

				{error && !dialogOpen && <Alert severity="error">{error}</Alert>}
				{periods.length === 0 && <Alert severity="info">No periods yet. Add the periods and breaks that make up your school day.</Alert>}

				{periods.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>#</TableCell>
									<TableCell>Label</TableCell>
									<TableCell>Start</TableCell>
									<TableCell>End</TableCell>
									<TableCell>Kind</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{periods.map((period) => (
									<TableRow key={period.id}>
										<TableCell>{period.ordinal}</TableCell>
										<TableCell>{period.label}</TableCell>
										<TableCell sx={{ fontVariantNumeric: "tabular-nums" }}>{period.startTime.slice(0, 5)}</TableCell>
										<TableCell sx={{ fontVariantNumeric: "tabular-nums" }}>{period.endTime.slice(0, 5)}</TableCell>
										<TableCell>
											<Chip label={period.breakSlot ? "Break" : "Class period"} size="small" />
										</TableCell>
										<TableCell align="right">
											<Can anyOf={["TIMETABLE_MANAGE"]}>
												<Button size="small" onClick={() => openEdit(period)}>
													Edit
												</Button>
												<Button size="small" color="error" onClick={() => handleDelete(period.id)}>
													Delete
												</Button>
											</Can>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>{editing ? "Edit period" : "Add period"}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{error && <Alert severity="error">{error}</Alert>}
						<TextField
							label="Order"
							type="number"
							value={ordinal}
							onChange={(e) => setOrdinal(e.target.value)}
							disabled={editing !== null}
							required
							fullWidth
						/>
						<TextField label="Label" value={label} onChange={(e) => setLabel(e.target.value)} required fullWidth />
						<Stack direction="row" spacing={2}>
							<TextField
								label="Start"
								type="time"
								value={startTime}
								onChange={(e) => setStartTime(e.target.value)}
								required
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
							<TextField
								label="End"
								type="time"
								value={endTime}
								onChange={(e) => setEndTime(e.target.value)}
								required
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
						</Stack>
						<FormControlLabel
							control={<Checkbox checked={breakSlot} onChange={(e) => setBreakSlot(e.target.checked)} />}
							label="Break (no classes scheduled)"
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !label || !ordinal}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
