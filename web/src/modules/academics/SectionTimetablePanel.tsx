import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
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
	listPeriods,
	listSectionTimetable,
	removeTimetableEntry,
	setTimetableEntry,
	type PeriodResponse,
	type TimetableEntryResponse,
} from "../../api/timetable";
import { useAuth } from "../../auth/AuthContext";
import { TimetableGrid } from "./TimetableGrid";

interface Slot {
	day: string;
	period: PeriodResponse;
	entry: TimetableEntryResponse | undefined;
}

/** Weekly timetable for one section. Everyone with TIMETABLE_VIEW gets the read-only grid
 * (#214); holders of TIMETABLE_MANAGE can additionally click a cell to place, change or
 * clear a slot (#213). Only subject+teacher pairs the section actually has an ACTIVE
 * teacher assignment for are offered - the backend enforces the same rule, and rejects a
 * teacher already booked elsewhere in that period, surfaced inline in the dialog. */
export function SectionTimetablePanel({ sectionId }: { sectionId: number }) {
	const { hasAnyPermission } = useAuth();
	const canManage = hasAnyPermission(["TIMETABLE_MANAGE"]);

	const [periods, setPeriods] = useState<PeriodResponse[]>([]);
	const [entries, setEntries] = useState<TimetableEntryResponse[]>([]);
	const [assignments, setAssignments] = useState<TeacherAssignmentResponse[]>([]);
	const [subjects, setSubjects] = useState<SubjectResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [slot, setSlot] = useState<Slot | null>(null);
	const [pairKey, setPairKey] = useState("");
	const [dialogError, setDialogError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listSectionTimetable(sectionId)
			.then(setEntries)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load timetable"));
	}

	useEffect(() => {
		setEntries([]);
		setError(null);
		listPeriods().then(setPeriods).catch(() => {});
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [sectionId]);

	// Refetch on each open, not just at mount - assignments may have changed in the sibling panel.
	function openSlot(day: string, period: PeriodResponse, entry: TimetableEntryResponse | undefined) {
		listTeacherAssignmentsForSection(sectionId).then(setAssignments).catch(() => {});
		listSubjects().then(setSubjects).catch(() => {});
		listPersons().then(setPersons).catch(() => {});
		setDialogError(null);
		setPairKey(entry ? `${entry.subjectId}:${entry.teacherPersonId}` : "");
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
			await setTimetableEntry(sectionId, { subjectId, teacherPersonId, dayOfWeek: slot.day, periodId: slot.period.id });
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

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Timetable</Typography>
				{error && <Alert severity="error">{error}</Alert>}
				{periods.length === 0 && <Alert severity="info">No periods are configured yet - set up the school day under Academics &gt; Periods.</Alert>}
				{periods.length > 0 && (
					<TimetableGrid
						periods={periods}
						entries={entries}
						onCellClick={canManage ? openSlot : undefined}
						renderEntry={(entry) => (
							<>
								<Typography variant="body2" sx={{ fontWeight: 600 }}>
									{entry.subjectName}
								</Typography>
								<Typography variant="caption" color="text.secondary">
									{entry.teacherName}
								</Typography>
							</>
						)}
					/>
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
		</Paper>
	);
}
