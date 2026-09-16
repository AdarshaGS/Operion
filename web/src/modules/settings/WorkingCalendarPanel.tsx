import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import IconButton from "@mui/material/IconButton";
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
import DeleteIcon from "@mui/icons-material/Delete";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import { listAcademicYears, type AcademicYearResponse } from "../../api/academicYears";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import {
	createWorkingCalendarEntry,
	deleteWorkingCalendarEntry,
	getAcademicConfiguration,
	listWorkingCalendarEntries,
	updateAcademicConfiguration,
	type WorkingCalendarEntryResponse,
	type WorkingCalendarEntryType,
} from "../../api/workingCalendar";

const ENTRY_TYPE_LABELS: Record<WorkingCalendarEntryType, string> = {
	HOLIDAY: "Holiday",
	SPECIAL_WORKING_DAY: "Special working day",
};

interface EntryFormState {
	date: string;
	label: string;
	type: WorkingCalendarEntryType;
	campusId: string;
}

const EMPTY_ENTRY_FORM: EntryFormState = { date: "", label: "", type: "HOLIDAY", campusId: "" };

/** Holidays, special working days, and school timings - the dated exceptions and
 * School-vertical detail that Business Settings' weekly working-days mask doesn't cover
 * (see #143). Entries are scoped to one academic year at a time, mirroring how the rest
 * of Academics (classes, etc.) resets per year. */
export function WorkingCalendarPanel() {
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [academicYearId, setAcademicYearId] = useState<number | "">("");
	const [entries, setEntries] = useState<WorkingCalendarEntryResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState<EntryFormState>(EMPTY_ENTRY_FORM);
	const [submitting, setSubmitting] = useState(false);

	const [schoolStartTime, setSchoolStartTime] = useState("");
	const [schoolEndTime, setSchoolEndTime] = useState("");
	const [timingsError, setTimingsError] = useState<string | null>(null);
	const [timingsSaved, setTimingsSaved] = useState(false);
	const [timingsSubmitting, setTimingsSubmitting] = useState(false);

	useEffect(() => {
		listAcademicYears()
			.then((years) => {
				setAcademicYears(years);
				const current = years.find((year) => year.current) ?? years[0];
				if (current) {
					setAcademicYearId(current.id);
				}
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load academic years"));
		listCampuses()
			.then(setCampuses)
			.catch(() => undefined);
		getAcademicConfiguration()
			.then((configuration) => {
				setSchoolStartTime(configuration.schoolStartTime ?? "");
				setSchoolEndTime(configuration.schoolEndTime ?? "");
			})
			.catch((err) => setTimingsError(err instanceof ApiError ? err.message : "Failed to load school timings"));
	}, []);

	function refreshEntries(yearId: number) {
		listWorkingCalendarEntries(yearId)
			.then(setEntries)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load working calendar"));
	}

	useEffect(() => {
		if (academicYearId !== "") {
			refreshEntries(academicYearId);
		}
	}, [academicYearId]);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		if (academicYearId === "") {
			return;
		}
		setSubmitting(true);
		try {
			await createWorkingCalendarEntry({
				academicYearId,
				campusId: form.campusId ? Number(form.campusId) : null,
				date: form.date,
				label: form.label,
				type: form.type,
			});
			setForm(EMPTY_ENTRY_FORM);
			setDialogOpen(false);
			refreshEntries(academicYearId);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add working calendar entry");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleDelete(id: number) {
		if (academicYearId === "") {
			return;
		}
		try {
			await deleteWorkingCalendarEntry(id);
			refreshEntries(academicYearId);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to remove working calendar entry");
		}
	}

	async function handleSaveTimings(event: FormEvent) {
		event.preventDefault();
		setTimingsSubmitting(true);
		setTimingsSaved(false);
		try {
			await updateAcademicConfiguration({
				schoolStartTime: schoolStartTime || null,
				schoolEndTime: schoolEndTime || null,
			});
			setTimingsSaved(true);
		} catch (err) {
			setTimingsError(err instanceof ApiError ? err.message : "Failed to update school timings");
		} finally {
			setTimingsSubmitting(false);
		}
	}

	function campusName(campusId: number | null) {
		if (campusId === null) {
			return "All campuses";
		}
		return campuses.find((campus) => campus.id === campusId)?.name ?? `Campus #${campusId}`;
	}

	return (
		<Stack spacing={3}>
			<Paper component="form" onSubmit={handleSaveTimings} sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">School timings</Typography>
					{timingsError && <Alert severity="error">{timingsError}</Alert>}
					{timingsSaved && <Alert severity="success">School timings updated</Alert>}
					<Box sx={{ display: "flex", gap: 2 }}>
						<TextField
							label="Start time"
							type="time"
							value={schoolStartTime}
							onChange={(e) => setSchoolStartTime(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField
							label="End time"
							type="time"
							value={schoolEndTime}
							onChange={(e) => setSchoolEndTime(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Box>
					<Can anyOf={["ACADEMIC_CONFIGURATION_MANAGE"]}>
						<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
							<Button type="submit" variant="contained" disabled={timingsSubmitting}>
								{timingsSubmitting ? "Saving..." : "Save"}
							</Button>
						</Box>
					</Can>
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 2 }}>
						<Typography variant="h6">Holidays &amp; special working days</Typography>
						<Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
							<TextField
								select
								size="small"
								label="Academic year"
								value={academicYearId}
								onChange={(e) => setAcademicYearId(Number(e.target.value))}
								sx={{ minWidth: 180 }}
							>
								{academicYears.map((year) => (
									<MenuItem key={year.id} value={year.id}>
										{year.name}
									</MenuItem>
								))}
							</TextField>
							<Can anyOf={["WORKING_CALENDAR_MANAGE"]}>
								<Button
									size="small"
									startIcon={<AddIcon />}
									disabled={academicYearId === ""}
									onClick={() => setDialogOpen(true)}
								>
									Add entry
								</Button>
							</Can>
						</Stack>
					</Box>

					{error && <Alert severity="error">{error}</Alert>}
					<Divider />

					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Date</TableCell>
									<TableCell>Label</TableCell>
									<TableCell>Type</TableCell>
									<TableCell>Campus</TableCell>
									<TableCell align="right">Actions</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{entries.map((entry) => (
									<TableRow key={entry.id}>
										<TableCell>{entry.date}</TableCell>
										<TableCell>{entry.label}</TableCell>
										<TableCell>
											<Chip
												label={ENTRY_TYPE_LABELS[entry.type]}
												size="small"
												color={entry.type === "HOLIDAY" ? "default" : "primary"}
											/>
										</TableCell>
										<TableCell>{campusName(entry.campusId)}</TableCell>
										<TableCell align="right">
											<Can anyOf={["WORKING_CALENDAR_MANAGE"]}>
												<IconButton size="small" onClick={() => handleDelete(entry.id)} aria-label="Remove entry">
													<DeleteIcon fontSize="small" />
												</IconButton>
											</Can>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				</Stack>
			</Paper>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add working calendar entry</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Date"
							type="date"
							value={form.date}
							onChange={(e) => setForm((prev) => ({ ...prev, date: e.target.value }))}
							required
							autoFocus
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField
							label="Label"
							placeholder="Independence Day"
							value={form.label}
							onChange={(e) => setForm((prev) => ({ ...prev, label: e.target.value }))}
							required
							fullWidth
						/>
						<TextField
							select
							label="Type"
							value={form.type}
							onChange={(e) => setForm((prev) => ({ ...prev, type: e.target.value as WorkingCalendarEntryType }))}
							fullWidth
						>
							{Object.entries(ENTRY_TYPE_LABELS).map(([value, label]) => (
								<MenuItem key={value} value={value}>
									{label}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Campus"
							value={form.campusId}
							onChange={(e) => setForm((prev) => ({ ...prev, campusId: e.target.value }))}
							fullWidth
						>
							<MenuItem value="">All campuses</MenuItem>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
