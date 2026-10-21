import { useEffect, useState } from "react";
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
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listSubjects, type SubjectResponse } from "../../api/subjects";
import { listTeacherAssignmentsForSection, type TeacherAssignmentResponse } from "../../api/teacherAssignments";
import {
	downloadSectionTimetableCsv,
	generateSectionTimetable,
	getClassPeriodTemplate,
	getSectionTimetableSummary,
	listPeriodTemplates,
	listRooms,
	listSectionPeriods,
	listSectionTimetable,
	publishSectionTimetable,
	removeTimetableEntry,
	setClassPeriodTemplate,
	setTimetableEntry,
	unpublishSectionTimetable,
	type ClassPeriodTemplateResponse,
	type GenerationResponse,
	type PeriodResponse,
	type PeriodTemplateResponse,
	type RoomResponse,
	type SectionTimetableSummaryResponse,
	type TimetableEntryResponse,
} from "../../api/timetable";
import { useAuth } from "../../auth/AuthContext";
import { printArea } from "../../utils/printArea";
import { TimetableGrid } from "./TimetableGrid";

interface Slot {
	day: string;
	period: PeriodResponse;
	entry: TimetableEntryResponse | undefined;
}

/** Weekly timetable for one section. Everyone with TIMETABLE_VIEW gets the read-only grid
 * (#214); holders of TIMETABLE_MANAGE can additionally click a cell to place, change or
 * clear a slot (#213), generate a draft from the subject quotas, and publish it. Only
 * subject+teacher pairs the section actually has an ACTIVE teacher assignment for are offered -
 * the backend enforces the same rule, and rejects a teacher already booked elsewhere in that
 * period, a room already taken, a quota already met or a teacher over their workload limit,
 * surfaced inline in the dialog. classId is only needed to let a manager change the class's school day. */
export function SectionTimetablePanel({ sectionId, classId }: { sectionId: number; classId?: number }) {
	const { hasAnyPermission } = useAuth();
	const canManage = hasAnyPermission(["TIMETABLE_MANAGE"]);

	const [periods, setPeriods] = useState<PeriodResponse[]>([]);
	const [entries, setEntries] = useState<TimetableEntryResponse[]>([]);
	const [summary, setSummary] = useState<SectionTimetableSummaryResponse | null>(null);
	const [rooms, setRooms] = useState<RoomResponse[]>([]);
	const [assignments, setAssignments] = useState<TeacherAssignmentResponse[]>([]);
	const [subjects, setSubjects] = useState<SubjectResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [showSunday, setShowSunday] = useState(false);
	const [busy, setBusy] = useState(false);

	const [slot, setSlot] = useState<Slot | null>(null);
	const [pairKey, setPairKey] = useState("");
	const [roomId, setRoomId] = useState("");
	const [dialogError, setDialogError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [generateOpen, setGenerateOpen] = useState(false);
	const [replaceExisting, setReplaceExisting] = useState(false);
	const [generation, setGeneration] = useState<GenerationResponse | null>(null);

	const [classDay, setClassDay] = useState<ClassPeriodTemplateResponse | null>(null);
	const [templates, setTemplates] = useState<PeriodTemplateResponse[]>([]);
	const [dayOpen, setDayOpen] = useState(false);
	const [dayChoice, setDayChoice] = useState("default");
	const [dayError, setDayError] = useState<string | null>(null);

	function refresh() {
		listSectionTimetable(sectionId)
			.then(setEntries)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load timetable"));
		getSectionTimetableSummary(sectionId).then(setSummary).catch(() => {});
	}

	useEffect(() => {
		setEntries([]);
		setSummary(null);
		setError(null);
		listSectionPeriods(sectionId).then(setPeriods).catch(() => {});
		if (classId) getClassPeriodTemplate(classId).then(setClassDay).catch(() => {});
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [sectionId, classId]);

	// Refetch on each open, not just at mount - assignments may have changed in the sibling panel.
	function openSlot(day: string, period: PeriodResponse, entry: TimetableEntryResponse | undefined) {
		listTeacherAssignmentsForSection(sectionId).then(setAssignments).catch(() => {});
		listSubjects().then(setSubjects).catch(() => {});
		listPersons().then(setPersons).catch(() => {});
		listRooms().then(setRooms).catch(() => {});
		setDialogError(null);
		setPairKey(entry ? `${entry.subjectId}:${entry.teacherPersonId}` : "");
		setRoomId(entry?.roomId ? String(entry.roomId) : "");
		setSlot({ day, period, entry });
	}

	const pairs = assignments
		.filter((a) => a.status === "ACTIVE" && a.subjectId !== null)
		.map((a) => {
			const subject = subjects.find((s) => s.id === a.subjectId);
			const person = persons.find((p) => p.id === a.teacherPersonId);
			return {
				key: `${a.subjectId}:${a.teacherPersonId}`,
				label: `${subject?.name ?? `Subject #${a.subjectId}`} — ${person ? `${person.firstName} ${person.lastName}` : `Person #${a.teacherPersonId}`}`,
			};
		});

	async function handleSave() {
		if (!slot || !pairKey) return;
		const [subjectId, teacherPersonId] = pairKey.split(":").map(Number);
		setSubmitting(true);
		setDialogError(null);
		try {
			await setTimetableEntry(sectionId, {
				subjectId,
				teacherPersonId,
				dayOfWeek: slot.day,
				periodId: slot.period.id,
				roomId: roomId ? Number(roomId) : null,
			});
			setSlot(null);
			refresh();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : "Failed to save slot");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleClear() {
		if (!slot?.entry) return;
		setSubmitting(true);
		setDialogError(null);
		try {
			await removeTimetableEntry(slot.entry.id);
			setSlot(null);
			refresh();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : "Failed to clear slot");
		} finally {
			setSubmitting(false);
		}
	}

	async function run(action: () => Promise<unknown>, failure: string) {
		setBusy(true);
		setError(null);
		try {
			await action();
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : failure);
		} finally {
			setBusy(false);
		}
	}

	async function handleGenerate() {
		setBusy(true);
		setError(null);
		try {
			setGeneration(await generateSectionTimetable(sectionId, replaceExisting));
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to generate the timetable");
			setGenerateOpen(false);
		} finally {
			setBusy(false);
		}
	}

	function openDay() {
		listPeriodTemplates().then(setTemplates).catch(() => {});
		setDayChoice(classDay?.templateId ? String(classDay.templateId) : "default");
		setDayError(null);
		setDayOpen(true);
	}

	async function handleDaySave() {
		if (!classId) return;
		setDayError(null);
		try {
			setClassDay(await setClassPeriodTemplate(classId, dayChoice === "default" ? null : Number(dayChoice)));
			setDayOpen(false);
			listSectionPeriods(sectionId).then(setPeriods).catch(() => {});
			refresh();
		} catch (err) {
			setDayError(err instanceof ApiError ? err.message : "Failed to change the school day");
		}
	}

	const quotaLines = summary?.subjects.filter((s) => s.requiredPerWeek !== null) ?? [];
	const published = summary?.published ?? false;

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
					<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
						<Typography variant="h6">Timetable</Typography>
						{summary && (
							<Chip
								size="small"
								color={published ? "success" : "default"}
								label={published ? "Published" : "Draft"}
								aria-label={published ? "Timetable published" : "Timetable draft"}
							/>
						)}
					</Stack>
					<Stack direction="row" spacing={1} className="no-print" sx={{ alignItems: "center", flexWrap: "wrap" }}>
						{canManage && (
							<FormControlLabel
								control={<Checkbox size="small" checked={showSunday} onChange={(e) => setShowSunday(e.target.checked)} />}
								label="Sunday"
							/>
						)}
						{canManage && !published && (
							<Button
								size="small"
								disabled={busy}
								onClick={() => {
									setGeneration(null);
									setReplaceExisting(false);
									setGenerateOpen(true);
								}}
							>
								Generate draft
							</Button>
						)}
						{canManage && !published && (
							<Button size="small" variant="contained" disabled={busy || entries.length === 0} onClick={() => run(() => publishSectionTimetable(sectionId), "Failed to publish")}>
								Publish
							</Button>
						)}
						{canManage && published && (
							<Button size="small" disabled={busy} onClick={() => run(() => unpublishSectionTimetable(sectionId), "Failed to unpublish")}>
								Unpublish
							</Button>
						)}
						<Button size="small" disabled={entries.length === 0} onClick={() => run(() => downloadSectionTimetableCsv(sectionId), "Failed to export")}>
							Export CSV
						</Button>
						<Button size="small" disabled={entries.length === 0} onClick={printArea}>
							Print
						</Button>
					</Stack>
				</Box>

				{classId && classDay && (
					<Stack direction="row" spacing={1} className="no-print" sx={{ alignItems: "center" }}>
						<Typography variant="body2" color="text.secondary" component="div">
							School day: {classDay.templateName ?? "Default school day"}
						</Typography>
						{canManage && (
							<Button size="small" onClick={openDay}>
								Change school day
							</Button>
						)}
					</Stack>
				)}

				{quotaLines.length > 0 && (
					<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", rowGap: 1 }} aria-label="Weekly subject quotas">
						{quotaLines.map((line) => (
							<Chip
								key={line.subjectId}
								size="small"
								variant="outlined"
								color={line.remaining > 0 ? "warning" : "success"}
								label={`${line.subjectName} ${line.placed}/${line.requiredPerWeek}`}
							/>
						))}
					</Stack>
				)}

				{error && <Alert severity="error">{error}</Alert>}
				{periods.length === 0 && <Alert severity="info">No periods are configured yet - set up the school day under Academics &gt; Periods.</Alert>}
				{periods.length > 0 && (
					<Box className="print-area">
						<TimetableGrid
							periods={periods}
							entries={entries}
							onCellClick={canManage ? openSlot : undefined}
							showSunday={canManage && showSunday}
							renderEntry={(entry) => (
								<>
									<Typography variant="body2" sx={{ fontWeight: 600 }}>
										{entry.subjectName}
									</Typography>
									<Typography variant="caption" color="text.secondary" component="div">
										{entry.teacherName}
									</Typography>
									{entry.roomName && (
										<Typography variant="caption" color="text.secondary" component="div">
											{entry.roomName}
										</Typography>
									)}
								</>
							)}
						/>
					</Box>
				)}
			</Stack>

			<Dialog open={slot !== null} onClose={() => setSlot(null)} fullWidth maxWidth="xs">
				<DialogTitle>
					{slot?.day.charAt(0)}
					{slot?.day.slice(1).toLowerCase()} · {slot?.period.label}
				</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{dialogError && <Alert severity="error">{dialogError}</Alert>}
						{pairs.length === 0 && (
							<Alert severity="info">This section has no subject teachers assigned yet - assign them first.</Alert>
						)}
						<TextField select label="Subject — teacher" value={pairKey} onChange={(e) => setPairKey(e.target.value)} fullWidth>
							{pairs.map((pair) => (
								<MenuItem key={pair.key} value={pair.key}>
									{pair.label}
								</MenuItem>
							))}
						</TextField>
						{rooms.some((r) => r.active) && (
							<TextField select label="Room" value={roomId} onChange={(e) => setRoomId(e.target.value)} fullWidth>
								<MenuItem value="">No room</MenuItem>
								{rooms
									.filter((r) => r.active || String(r.id) === roomId)
									.map((room) => (
										<MenuItem key={room.id} value={String(room.id)}>
											{room.name}
											{room.roomType ? ` (${room.roomType})` : ""}
										</MenuItem>
									))}
							</TextField>
						)}
					</Stack>
				</DialogContent>
				<DialogActions>
					{slot?.entry && (
						<Button color="error" onClick={handleClear} disabled={submitting} sx={{ mr: "auto" }}>
							Clear slot
						</Button>
					)}
					<Button onClick={() => setSlot(null)}>Cancel</Button>
					<Button variant="contained" onClick={handleSave} disabled={submitting || !pairKey}>
						Save
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={generateOpen} onClose={() => setGenerateOpen(false)} fullWidth maxWidth="sm">
				<DialogTitle>Generate draft timetable</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{!generation && (
							<>
								<Typography variant="body2">
									Fills the empty slots from each subject&apos;s weekly quota and the subject teachers assigned to this section, spreading
									subjects across the week and respecting teacher clashes, availability and workload limits. It never moves a teacher
									into a clash, and tells you what it could not place.
								</Typography>
								<FormControlLabel
									control={<Checkbox checked={replaceExisting} onChange={(e) => setReplaceExisting(e.target.checked)} />}
									label="Replace the slots already placed"
								/>
							</>
						)}
						{generation && (
							<>
								<Alert severity={generation.unplaced === 0 ? "success" : "warning"}>
									Placed {generation.placed} period{generation.placed === 1 ? "" : "s"}
									{generation.unplaced > 0 ? `; ${generation.unplaced} could not be placed.` : "."}
								</Alert>
								{generation.sections.flatMap((outcome) =>
									outcome.shortfalls.map((shortfall) => (
										<Typography key={`${outcome.sectionId}-${shortfall.subjectId}`} variant="body2">
											{shortfall.subjectName}: {shortfall.missing} short - {shortfall.reason}
										</Typography>
									)),
								)}
							</>
						)}
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setGenerateOpen(false)}>{generation ? "Close" : "Cancel"}</Button>
					{!generation && (
						<Button variant="contained" onClick={handleGenerate} disabled={busy}>
							Generate
						</Button>
					)}
				</DialogActions>
			</Dialog>

			<Dialog open={dayOpen} onClose={() => setDayOpen(false)} fullWidth maxWidth="xs">
				<DialogTitle>Change school day</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{dayError && <Alert severity="error">{dayError}</Alert>}
						<Typography variant="body2">The class follows this day&apos;s periods for every one of its sections.</Typography>
						<TextField select label="School day" value={dayChoice} onChange={(e) => setDayChoice(e.target.value)} fullWidth>
							<MenuItem value="default">Default school day</MenuItem>
							{templates.map((t) => (
								<MenuItem key={t.id} value={String(t.id)}>
									{t.name}
								</MenuItem>
							))}
						</TextField>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDayOpen(false)}>Cancel</Button>
					<Button variant="contained" onClick={handleDaySave}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
