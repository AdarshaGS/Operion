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
import {
	closeAcademicYear,
	createAcademicYear,
	listAcademicYears,
	markAcademicYearCurrent,
	type AcademicYearResponse,
} from "../../api/academicYears";

export function AcademicYearsPanel() {
	const [years, setYears] = useState<AcademicYearResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [name, setName] = useState("");
	const [startDate, setStartDate] = useState("");
	const [endDate, setEndDate] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listAcademicYears()
			.then(setYears)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load academic years"));
	}

	useEffect(refresh, []);

	async function handleMarkCurrent(id: number) {
		try {
			await markAcademicYearCurrent(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to mark academic year current");
		}
	}

	async function handleClose(id: number) {
		try {
			await closeAcademicYear(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to close academic year");
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createAcademicYear({ name, startDate, endDate });
			setName("");
			setStartDate("");
			setEndDate("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create academic year");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Academic years</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add academic year
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Name</TableCell>
								<TableCell>Start date</TableCell>
								<TableCell>End date</TableCell>
								<TableCell>Current</TableCell>
								<TableCell>Status</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{years.map((year) => (
								<TableRow key={year.id}>
									<TableCell>{year.name}</TableCell>
									<TableCell>{year.startDate}</TableCell>
									<TableCell>{year.endDate}</TableCell>
									<TableCell>{year.current ? <Chip label="Current" color="primary" size="small" /> : "—"}</TableCell>
									<TableCell>
										<Chip label={year.status} size="small" />
									</TableCell>
									<TableCell>
										{year.status !== "CLOSED" && !year.current && (
											<Button size="small" onClick={() => handleMarkCurrent(year.id)}>
												Mark current
											</Button>
										)}
										{year.status !== "CLOSED" && (
											<Button size="small" color="error" onClick={() => handleClose(year.id)}>
												Close
											</Button>
										)}
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add academic year</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Name"
							placeholder="2026-2027"
							value={name}
							onChange={(e) => setName(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<TextField
							label="Start date"
							type="date"
							value={startDate}
							onChange={(e) => setStartDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField
							label="End date"
							type="date"
							value={endDate}
							onChange={(e) => setEndDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
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
