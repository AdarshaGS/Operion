import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Autocomplete from "@mui/material/Autocomplete";
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
import { listSubjects } from "../../api/subjects";
import {
	createPeriod,
	createPeriodTemplate,
	deletePeriod,
	deletePeriodTemplate,
	listPeriods,
	listPeriodTemplates,
	renamePeriodTemplate,
	updatePeriod,
	type PeriodResponse,
	type PeriodTemplateResponse,
	type SlotType,
} from "../../api/timetable";
import { Can } from "../../auth/Can";
import { TimetableRulesCard } from "./TimetableRulesCard";

const KIND_LABELS: Record<SlotType, string> = {
	TEACHING: "Class period",
	BREAK: "Break (no classes scheduled)",
	SPECIAL: "Special slot (assembly, no classes)",
};
const KIND_CHIPS: Record<SlotType, string> = { TEACHING: "Class period", BREAK: "Break", SPECIAL: "Special" };

/** "09:45" + 5 minutes -> "09:50"; wraps nowhere because a school day ends before midnight. */
function addMinutes(time: string, minutes: number): string {
	const [h, m] = time.split(":").map(Number);
	const total = Math.min(h * 60 + m + minutes, 23 * 60 + 59);
	return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

/** Whole minutes from "09:00" to "09:45"; never negative. */
function minutesBetween(start: string, end: string): number {
	const [sh, sm] = start.split(":").map(Number);
	const [eh, em] = end.split(":").map(Number);
	return Math.max(eh * 60 + em - (sh * 60 + sm), 0);
}

/** Class periods default to this length when the school hasn't configured a standard one. */
const DEFAULT_PERIOD_MINUTES = 40;
/** Length offered for a break or special slot whose end would otherwise not follow its start. */
const DEFAULT_BREAK_MINUTES = 15;

/** The org's school-day structure - ordered periods, breaks and special slots. Configured, not
 * hardcoded: every school defines its own number of periods and times, and can keep alternative
 * school days (templates) for class groups that run to a different clock. */
export function PeriodsPanel() {
	const [templates, setTemplates] = useState<PeriodTemplateResponse[]>([]);
	const [templateId, setTemplateId] = useState<number | null>(null); // null is the default school day
	const [gapMinutes, setGapMinutes] = useState(0);
	const [periodMinutes, setPeriodMinutes] = useState<number | null>(null);
	const [templateDialog, setTemplateDialog] = useState<"new" | "rename" | null>(null);
	const [templateName, setTemplateName] = useState("");
	const [periods, setPeriods] = useState<PeriodResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);
	// Active subject names, offered as the period label; a break or assembly can still be typed freely.
	const [subjectNames, setSubjectNames] = useState<string[]>([]);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [editing, setEditing] = useState<PeriodResponse | null>(null);
	const [duplicating, setDuplicating] = useState(false);
	// Once the user types an End time, changing Start or Kind no longer recomputes it.
	const [endTouched, setEndTouched] = useState(false);
	const [ordinal, setOrdinal] = useState("");
	const [label, setLabel] = useState("");
	const [startTime, setStartTime] = useState("09:00");
	const [endTime, setEndTime] = useState("09:45");
	const [slotType, setSlotType] = useState<SlotType>("TEACHING");

	function refresh() {
		listPeriods(templateId)
			.then(setPeriods)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load periods"));
		listPeriodTemplates().then(setTemplates).catch(() => {});
	}

	useEffect(refresh, [templateId]);

	useEffect(() => {
		listSubjects()
			.then((subjects) => setSubjectNames(subjects.filter((subject) => subject.status === "ACTIVE").map((subject) => subject.name)))
			.catch(() => {});
	}, []);

	const selectedTemplate = templates.find((t) => t.id === templateId) ?? null;

	async function handleTemplateSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		try {
			if (templateDialog === "new") {
				const created = await createPeriodTemplate(templateName);
				setTemplateId(created.id);
			} else if (selectedTemplate) {
				await renamePeriodTemplate(selectedTemplate.id, templateName);
			}
			setTemplateDialog(null);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save school day");
		}
	}

	async function handleTemplateDelete() {
		if (!selectedTemplate) return;
		setError(null);
		try {
			await deletePeriodTemplate(selectedTemplate.id);
			setTemplateId(null);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to delete school day");
		}
	}

	const classPeriodMinutes = periodMinutes ?? DEFAULT_PERIOD_MINUTES;

	function nextOrdinal(): string {
		return String(periods.length === 0 ? 1 : Math.max(...periods.map((p) => p.ordinal)) + 1);
	}

	/** Start where the last slot ended, plus the minimum gap when that slot is a class period. */
	function nextStart(): string {
		const last = periods[periods.length - 1];
		return last ? addMinutes(last.endTime.slice(0, 5), last.slotType === "TEACHING" ? gapMinutes : 0) : "09:00";
	}

	function openCreate() {
		setEditing(null);
		setDuplicating(false);
		setEndTouched(false);
		setOrdinal(nextOrdinal());
		setLabel("");
		setSlotType("TEACHING");
		const start = nextStart();
		setStartTime(start);
		setEndTime(addMinutes(start, classPeriodMinutes));
		setError(null);
		setDialogOpen(true);
	}

	/** Opens the Add dialog prefilled from an existing period (same label, kind and duration), placed after the last one.
	 * Nothing is saved until the user submits. */
	function openDuplicate(source: PeriodResponse) {
		const duration = minutesBetween(source.startTime.slice(0, 5), source.endTime.slice(0, 5));
		setEditing(null);
		setDuplicating(true);
		setEndTouched(true);
		setOrdinal(nextOrdinal());
		setLabel(source.label);
		setSlotType(source.slotType);
		const start = nextStart();
		setStartTime(start);
		setEndTime(addMinutes(start, duration > 0 ? duration : classPeriodMinutes));
		setError(null);
		setDialogOpen(true);
	}

	function handleStartChange(value: string) {
		setStartTime(value);
		if (editing || endTouched || !value) return;
		if (slotType === "TEACHING") {
			setEndTime(addMinutes(value, classPeriodMinutes));
		} else if (endTime <= value) {
			setEndTime(addMinutes(value, DEFAULT_BREAK_MINUTES));
		}
	}

	function handleKindChange(kind: SlotType) {
		setSlotType(kind);
		if (editing || endTouched || !startTime) return;
		if (kind === "TEACHING") {
			setEndTime(addMinutes(startTime, classPeriodMinutes));
		} else if (endTime <= startTime) {
			setEndTime(addMinutes(startTime, DEFAULT_BREAK_MINUTES));
		}
	}

	function openEdit(period: PeriodResponse) {
		setEditing(period);
		setDuplicating(false);
		setOrdinal(String(period.ordinal));
		setLabel(period.label);
		setStartTime(period.startTime.slice(0, 5));
		setEndTime(period.endTime.slice(0, 5));
		setSlotType(period.slotType);
		setError(null);
		setDialogOpen(true);
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setError(null);
		try {
			const body = { label, startTime, endTime, slotType };
			if (editing) {
				await updatePeriod(editing.id, body);
			} else {
				await createPeriod({ ...body, ordinal: Number(ordinal), templateId });
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

				<TimetableRulesCard
					onChanged={(settings) => {
						setGapMinutes(settings.gapMinutes);
						setPeriodMinutes(settings.periodMinutes);
					}}
				/>

				<Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
					<TextField
						select
						label="School day"
						value={templateId === null ? "default" : String(templateId)}
						onChange={(e) => setTemplateId(e.target.value === "default" ? null : Number(e.target.value))}
						helperText="A class follows the default day unless it is given its own"
						sx={{ minWidth: 260 }}
					>
						<MenuItem value="default">Default school day</MenuItem>
						{templates.map((t) => (
							<MenuItem key={t.id} value={String(t.id)}>
								{t.name} ({t.classCount} class{t.classCount === 1 ? "" : "es"})
							</MenuItem>
						))}
					</TextField>
					<Can anyOf={["TIMETABLE_MANAGE"]}>
						<Button
							size="small"
							onClick={() => {
								setTemplateName("");
								setTemplateDialog("new");
							}}
						>
							New school day
						</Button>
						{selectedTemplate && (
							<>
								<Button
									size="small"
									onClick={() => {
										setTemplateName(selectedTemplate.name);
										setTemplateDialog("rename");
									}}
								>
									Rename
								</Button>
								<Button size="small" color="error" onClick={handleTemplateDelete}>
									Delete school day
								</Button>
							</>
						)}
					</Can>
				</Stack>

				{error && !dialogOpen && templateDialog === null && <Alert severity="error">{error}</Alert>}
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
									<TableCell>Length</TableCell>
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
										<TableCell sx={{ fontVariantNumeric: "tabular-nums" }}>
											{minutesBetween(period.startTime.slice(0, 5), period.endTime.slice(0, 5))} min
										</TableCell>
										<TableCell>
											<Chip label={KIND_CHIPS[period.slotType]} size="small" />
										</TableCell>
										<TableCell align="right">
											<Can anyOf={["TIMETABLE_MANAGE"]}>
												<Button size="small" onClick={() => openEdit(period)}>
													Edit
												</Button>
												<Button size="small" onClick={() => openDuplicate(period)}>
													Duplicate
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

			<Dialog open={templateDialog !== null} onClose={() => setTemplateDialog(null)} component="form" onSubmit={handleTemplateSubmit} fullWidth maxWidth="xs">
				<DialogTitle>{templateDialog === "new" ? "New school day" : "Rename school day"}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{error && <Alert severity="error">{error}</Alert>}
						<TextField label="Name" value={templateName} onChange={(e) => setTemplateName(e.target.value)} required fullWidth autoFocus />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setTemplateDialog(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={!templateName.trim()}>
						Save
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>{editing ? "Edit period" : duplicating ? "Duplicate period" : "Add period"}</DialogTitle>
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
						<Autocomplete
							freeSolo
							options={subjectNames}
							inputValue={label}
							onInputChange={(_, value) => setLabel(value)}
							renderInput={(params) => (
								<TextField {...params} label="Label" required helperText="Pick a subject, or type a name such as Lunch break" />
							)}
						/>
						<Stack direction="row" spacing={2}>
							<TextField
								label="Start"
								type="time"
								value={startTime}
								onChange={(e) => handleStartChange(e.target.value)}
								required
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
							<TextField
								label="End"
								type="time"
								value={endTime}
								onChange={(e) => {
									setEndTime(e.target.value);
									setEndTouched(true);
								}}
								required
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
						</Stack>
						<TextField
							select
							label="Kind"
							value={slotType}
							onChange={(e) => handleKindChange(e.target.value as SlotType)}
							helperText={gapMinutes > 0 ? `Class periods must start at least ${gapMinutes} minute${gapMinutes === 1 ? "" : "s"} after the one before` : undefined}
							fullWidth
						>
							{(Object.keys(KIND_LABELS) as SlotType[]).map((kind) => (
								<MenuItem key={kind} value={kind}>
									{KIND_LABELS[kind]}
								</MenuItem>
							))}
						</TextField>
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
