import { useEffect, useMemo, useState, type FormEvent } from "react";
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
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import DeleteIcon from "@mui/icons-material/Delete";
import { Can } from "../../auth/Can";
import { useAuth } from "../../auth/AuthContext";
import { ApiError } from "../../api/client";
import { listAcademicYears, type AcademicYearResponse } from "../../api/academicYears";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { listWorkingCalendarEntries, type WorkingCalendarEntryResponse } from "../../api/workingCalendar";
import { createSchoolEvent, deleteSchoolEvent, listSchoolEvents, type SchoolEventResponse } from "../../api/schoolEvents";

const WEEKDAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

interface EventFormState {
	date: string;
	title: string;
	description: string;
	campusId: string;
}

const EMPTY_EVENT_FORM: EventFormState = { date: "", title: "", description: "", campusId: "" };

function toIsoDate(date: Date): string {
	return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function buildMonthGrid(monthStart: Date): Date[][] {
	const firstOfMonth = new Date(monthStart.getFullYear(), monthStart.getMonth(), 1);
	const daysInMonth = new Date(monthStart.getFullYear(), monthStart.getMonth() + 1, 0).getDate();
	const leadingBlanks = firstOfMonth.getDay();
	const cells: (Date | null)[] = [];
	for (let i = 0; i < leadingBlanks; i++) {
		cells.push(null);
	}
	for (let day = 1; day <= daysInMonth; day++) {
		cells.push(new Date(monthStart.getFullYear(), monthStart.getMonth(), day));
	}
	while (cells.length % 7 !== 0) {
		cells.push(null);
	}
	const weeks: Date[][] = [];
	for (let i = 0; i < cells.length; i += 7) {
		weeks.push(cells.slice(i, i + 7) as Date[]);
	}
	return weeks;
}

/** #240's admin-side scope: a month-grid view over WorkingCalendarEntry (holidays/special
 * working days, #143) plus ad-hoc SchoolEvent rows, with an "Add event" action for admins.
 * Surfacing this inside the Teacher/Student/Guardian portals is blocked on #207-209, which
 * don't exist yet. */
export function EventCalendarPanel() {
	const { hasAnyPermission } = useAuth();
	const canManageEvents = hasAnyPermission(["SCHOOL_EVENT_MANAGE"]);
	const [monthStart, setMonthStart] = useState(() => {
		const now = new Date();
		return new Date(now.getFullYear(), now.getMonth(), 1);
	});
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [calendarEntries, setCalendarEntries] = useState<WorkingCalendarEntryResponse[]>([]);
	const [events, setEvents] = useState<SchoolEventResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState<EventFormState>(EMPTY_EVENT_FORM);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		listAcademicYears().then(setAcademicYears).catch(() => undefined);
		listCampuses().then(setCampuses).catch(() => undefined);
	}, []);

	useEffect(() => {
		const currentYear = academicYears.find((year) => year.current) ?? academicYears[0];
		if (currentYear) {
			listWorkingCalendarEntries(currentYear.id)
				.then(setCalendarEntries)
				.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load working calendar"));
		}
	}, [academicYears]);

	function refreshEvents() {
		const monthEnd = new Date(monthStart.getFullYear(), monthStart.getMonth() + 1, 0);
		listSchoolEvents(toIsoDate(monthStart), toIsoDate(monthEnd))
			.then(setEvents)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load school events"));
	}

	useEffect(refreshEvents, [monthStart]);

	const entriesByDate = useMemo(() => {
		const map = new Map<string, WorkingCalendarEntryResponse[]>();
		for (const entry of calendarEntries) {
			const list = map.get(entry.date) ?? [];
			list.push(entry);
			map.set(entry.date, list);
		}
		return map;
	}, [calendarEntries]);

	const eventsByDate = useMemo(() => {
		const map = new Map<string, SchoolEventResponse[]>();
		for (const event of events) {
			const list = map.get(event.date) ?? [];
			list.push(event);
			map.set(event.date, list);
		}
		return map;
	}, [events]);

	function campusName(campusId: number | null) {
		if (campusId === null) {
			return "All campuses";
		}
		return campuses.find((campus) => campus.id === campusId)?.name ?? `Campus #${campusId}`;
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createSchoolEvent({
				campusId: form.campusId ? Number(form.campusId) : null,
				date: form.date,
				title: form.title,
				description: form.description || null,
			});
			setForm(EMPTY_EVENT_FORM);
			setDialogOpen(false);
			refreshEvents();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add event");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleDelete(id: number) {
		try {
			await deleteSchoolEvent(id);
			refreshEvents();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to remove event");
		}
	}

	const weeks = buildMonthGrid(monthStart);
	const monthLabel = monthStart.toLocaleDateString(undefined, { month: "long", year: "numeric" });
	const today = toIsoDate(new Date());

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 2 }}>
					<Typography variant="h6">Events calendar</Typography>
					<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
						<IconButton
							size="small"
							aria-label="Previous month"
							onClick={() => setMonthStart((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1))}
						>
							<ChevronLeftIcon fontSize="small" />
						</IconButton>
						<Typography variant="body2" sx={{ minWidth: 140, textAlign: "center" }}>
							{monthLabel}
						</Typography>
						<IconButton
							size="small"
							aria-label="Next month"
							onClick={() => setMonthStart((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
						>
							<ChevronRightIcon fontSize="small" />
						</IconButton>
						<Can anyOf={["SCHOOL_EVENT_MANAGE"]}>
							<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
								Add event
							</Button>
						</Can>
					</Stack>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}
				<Divider />

				<Box sx={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", gap: 0.5 }}>
					{WEEKDAY_LABELS.map((label) => (
						<Typography key={label} variant="caption" sx={{ textAlign: "center", fontWeight: 600, color: "text.secondary" }}>
							{label}
						</Typography>
					))}
					{weeks.flatMap((week, weekIndex) =>
						week.map((day, dayIndex) => {
							if (!day) {
								return <Box key={`${weekIndex}-${dayIndex}`} />;
							}
							const iso = toIsoDate(day);
							const dayEntries = entriesByDate.get(iso) ?? [];
							const dayEvents = eventsByDate.get(iso) ?? [];
							return (
								<Box
									key={iso}
									sx={{
										minHeight: 84,
										p: 0.5,
										border: "1px solid",
										borderColor: "divider",
										borderRadius: 1,
										bgcolor: iso === today ? "action.selected" : undefined,
									}}
								>
									<Typography variant="caption" sx={{ fontWeight: iso === today ? 700 : 400 }}>
										{day.getDate()}
									</Typography>
									<Stack spacing={0.25} sx={{ mt: 0.25 }}>
										{dayEntries.map((entry) => (
											<Tooltip key={entry.id} title={`${entry.label} (${campusName(entry.campusId)})`}>
												<Chip
													label={entry.label}
													size="small"
													color={entry.type === "HOLIDAY" ? "default" : "primary"}
													sx={{ height: 18, fontSize: 10, justifyContent: "flex-start" }}
												/>
											</Tooltip>
										))}
										{dayEvents.map((event) => (
											<Tooltip key={event.id} title={`${event.title} (${campusName(event.campusId)})`}>
												<Chip
													label={event.title}
													size="small"
													color="success"
													onDelete={canManageEvents ? () => handleDelete(event.id) : undefined}
													deleteIcon={<DeleteIcon />}
													sx={{ height: 18, fontSize: 10, justifyContent: "flex-start" }}
												/>
											</Tooltip>
										))}
									</Stack>
								</Box>
							);
						}),
					)}
				</Box>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add school event</DialogTitle>
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
							label="Title"
							placeholder="Sports Day"
							value={form.title}
							onChange={(e) => setForm((prev) => ({ ...prev, title: e.target.value }))}
							required
							fullWidth
						/>
						<TextField
							label="Description"
							value={form.description}
							onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
							multiline
							minRows={2}
							fullWidth
						/>
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
		</Paper>
	);
}
