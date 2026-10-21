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
import { ApiError } from "../../api/client";
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

/** The org's school-day structure - ordered periods, breaks and special slots. Configured, not
 * hardcoded: every school defines its own number of periods and times, and can keep alternative
 * school days (templates) for class groups that run to a different clock. */
export function PeriodsPanel() {
	const [templates, setTemplates] = useState<PeriodTemplateResponse[]>([]);
	const [templateId, setTemplateId] = useState<number | null>(null); // null is the default school day
	const [gapMinutes, setGapMinutes] = useState(0);
	const [templateDialog, setTemplateDialog] = useState<"new" | "rename" | null>(null);
	const [templateName, setTemplateName] = useState("");
	const [periods, setPeriods] = useState<PeriodResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [editing, setEditing] = useState<PeriodResponse | null>(null);
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

	function openCreate() {
		setEditing(null);
		setOrdinal(String(periods.length === 0 ? 1 : Math.max(...periods.map((p) => p.ordinal)) + 1));
		setLabel("");
		setSlotType("TEACHING");
		// Default the next slot to start where the previous one ended, plus the minimum gap when both are class periods.
		const last = periods[periods.length - 1];
		const start = last ? addMinutes(last.endTime.slice(0, 5), last.slotType === "TEACHING" ? gapMinutes : 0) : "09:00";
		setStartTime(start);
		setEndTime(start);
		setError(null);
		setDialogOpen(true);
	}

	function openEdit(period: PeriodResponse) {
		setEditing(period);
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

				<TimetableRulesCard onChanged={(settings) => setGapMinutes(settings.gapMinutes)} />

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
											<Chip label={KIND_CHIPS[period.slotType]} size="small" />
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
						<TextField
							select
							label="Kind"
							value={slotType}
							onChange={(e) => setSlotType(e.target.value as SlotType)}
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
