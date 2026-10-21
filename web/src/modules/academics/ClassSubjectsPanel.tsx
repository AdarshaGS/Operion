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
import {
	assignSubjectToClass,
	changeClassSubjectStatus,
	listClassSubjects,
	updateClassSubject,
	type ClassSubjectResponse,
} from "../../api/classSubjects";
import { ApiError } from "../../api/client";
import { listSubjects, type SubjectResponse } from "../../api/subjects";

/** Subject assignments for one class offering - a standalone panel (#246) so it can be
 * reused both in the Academics tabbed configuration area and on SchoolClassSectionsPage's
 * own per-class route, from a single source of truth. */
export function ClassSubjectsPanel({ classId }: { classId: number }) {
	const [subjects, setSubjects] = useState<SubjectResponse[]>([]);
	const [classSubjects, setClassSubjects] = useState<ClassSubjectResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [assignDialogOpen, setAssignDialogOpen] = useState(false);
	const [subjectId, setSubjectId] = useState("");
	const [mandatory, setMandatory] = useState(true);
	const [weeklyPeriods, setWeeklyPeriods] = useState("");

	const [editTarget, setEditTarget] = useState<ClassSubjectResponse | null>(null);
	const [editMandatory, setEditMandatory] = useState(true);
	const [editWeeklyPeriods, setEditWeeklyPeriods] = useState("");

	function refresh() {
		listClassSubjects(classId)
			.then(setClassSubjects)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load class subjects"));
	}

	useEffect(() => {
		setClassSubjects([]);
		listSubjects().then(setSubjects).catch(() => {});
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [classId]);

	function openAssignDialog() {
		listSubjects().then(setSubjects).catch(() => {});
		setAssignDialogOpen(true);
	}

	async function handleAssignSubject(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await assignSubjectToClass(classId, { subjectId: Number(subjectId), mandatory, weeklyPeriods: weeklyPeriods ? Number(weeklyPeriods) : null });
			setSubjectId("");
			setMandatory(true);
			setWeeklyPeriods("");
			setAssignDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to assign subject");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleToggleStatus(classSubject: ClassSubjectResponse) {
		try {
			await changeClassSubjectStatus(classId, classSubject.id, classSubject.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update class subject status");
		}
	}

	function openEdit(classSubject: ClassSubjectResponse) {
		setEditTarget(classSubject);
		setEditMandatory(classSubject.mandatory);
		setEditWeeklyPeriods(classSubject.weeklyPeriods === null ? "" : String(classSubject.weeklyPeriods));
	}

	async function handleEditSave(event: FormEvent) {
		event.preventDefault();
		if (!editTarget) return;
		setSubmitting(true);
		try {
			// Blank clears an existing quota (0); with none set there is nothing to send.
			const quota = editWeeklyPeriods ? Number(editWeeklyPeriods) : editTarget.weeklyPeriods === null ? undefined : 0;
			await updateClassSubject(classId, editTarget.id, editMandatory, quota);
			setEditTarget(null);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update class subject");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Class subjects</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={openAssignDialog}>
						Assign subject
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{classSubjects.length === 0 && <Alert severity="info">No subjects assigned to this class yet.</Alert>}

				{classSubjects.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Subject</TableCell>
									<TableCell>Mandatory</TableCell>
									<TableCell>Periods / week</TableCell>
									<TableCell>Status</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{classSubjects.map((classSubject) => (
									<TableRow key={classSubject.id}>
										<TableCell>{subjects.find((s) => s.id === classSubject.subjectId)?.name ?? `Subject #${classSubject.subjectId}`}</TableCell>
										<TableCell>{classSubject.mandatory ? "Yes" : "No"}</TableCell>
										<TableCell>{classSubject.weeklyPeriods ?? "—"}</TableCell>
										<TableCell>
											<Chip label={classSubject.status} size="small" />
										</TableCell>
										<TableCell>
											<Button size="small" onClick={() => openEdit(classSubject)}>
												Edit
											</Button>
											<Button size="small" onClick={() => handleToggleStatus(classSubject)}>
												{classSubject.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
											</Button>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={assignDialogOpen} onClose={() => setAssignDialogOpen(false)} component="form" onSubmit={handleAssignSubject} fullWidth maxWidth="xs">
				<DialogTitle>Assign subject</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Subject" value={subjectId} onChange={(e) => setSubjectId(e.target.value)} required autoFocus fullWidth>
							{subjects.map((subject) => (
								<MenuItem key={subject.id} value={subject.id}>
									{subject.name}
								</MenuItem>
							))}
						</TextField>
						<TextField select label="Mandatory" value={mandatory ? "yes" : "no"} onChange={(e) => setMandatory(e.target.value === "yes")} fullWidth>
							<MenuItem value="yes">Yes</MenuItem>
							<MenuItem value="no">No (elective)</MenuItem>
						</TextField>
						<TextField
							label="Periods per week"
							type="number"
							value={weeklyPeriods}
							onChange={(e) => setWeeklyPeriods(e.target.value)}
							helperText="How many timetable slots this subject should get in a week; blank for no quota"
							slotProps={{ htmlInput: { min: 1 } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAssignDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !subjectId}>
						Assign
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={editTarget !== null} onClose={() => setEditTarget(null)} component="form" onSubmit={handleEditSave} fullWidth maxWidth="xs">
				<DialogTitle>Edit class subject</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							select
							label="Mandatory"
							value={editMandatory ? "yes" : "no"}
							onChange={(e) => setEditMandatory(e.target.value === "yes")}
							fullWidth
						>
							<MenuItem value="yes">Yes</MenuItem>
							<MenuItem value="no">No (elective)</MenuItem>
						</TextField>
						<TextField
							label="Periods per week"
							type="number"
							value={editWeeklyPeriods}
							onChange={(e) => setEditWeeklyPeriods(e.target.value)}
							helperText="How many timetable slots this subject should get in a week; blank for no quota"
							slotProps={{ htmlInput: { min: 1 } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setEditTarget(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
