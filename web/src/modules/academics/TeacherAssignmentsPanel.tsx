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
import { listPersons, type PersonResponse } from "../../api/persons";
import { listSubjects, type SubjectResponse } from "../../api/subjects";
import {
	assignTeacher,
	endTeacherAssignment,
	listTeacherAssignmentsForSection,
	type TeacherAssignmentResponse,
} from "../../api/teacherAssignments";

const ASSIGNMENT_TYPES = ["HOMEROOM", "SUBJECT", "CO_TEACHER"];

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

/** Teacher assignments for one section - a standalone panel (#246) so it can be reused
 * both in the Academics tabbed configuration area and on SectionDetailPage's own
 * per-section route, from a single source of truth. */
export function TeacherAssignmentsPanel({ sectionId }: { sectionId: number }) {
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [subjects, setSubjects] = useState<SubjectResponse[]>([]);
	const [assignments, setAssignments] = useState<TeacherAssignmentResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [assignDialogOpen, setAssignDialogOpen] = useState(false);
	const [assignmentType, setAssignmentType] = useState("SUBJECT");
	const [subjectId, setSubjectId] = useState("");
	const [teacherPersonId, setTeacherPersonId] = useState("");
	const [startDate, setStartDate] = useState(todayIso());

	function refresh() {
		listTeacherAssignmentsForSection(sectionId)
			.then(setAssignments)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load teacher assignments"));
	}

	useEffect(() => {
		setAssignments([]);
		listPersons().then(setPersons).catch(() => {});
		listSubjects().then(setSubjects).catch(() => {});
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [sectionId]);

	function openAssignDialog() {
		listPersons().then(setPersons).catch(() => {});
		listSubjects().then(setSubjects).catch(() => {});
		setAssignDialogOpen(true);
	}

	function personLabel(id: number): string {
		const person = persons.find((p) => p.id === id);
		return person ? `${person.firstName} ${person.lastName}` : `Person #${id}`;
	}

	function subjectLabel(id: number | null): string {
		if (id === null) return "— (homeroom)";
		return subjects.find((s) => s.id === id)?.name ?? `Subject #${id}`;
	}

	async function handleAssign(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await assignTeacher(sectionId, {
				subjectId: assignmentType === "HOMEROOM" ? null : Number(subjectId),
				teacherPersonId: Number(teacherPersonId),
				assignmentType,
				startDate,
			});
			setSubjectId("");
			setTeacherPersonId("");
			setAssignDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to assign teacher");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleEnd(id: number) {
		try {
			await endTeacherAssignment(id, todayIso());
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to end teacher assignment");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Teacher assignments</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={openAssignDialog}>
						Assign teacher
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{assignments.length === 0 && <Alert severity="info">No teacher assignments yet.</Alert>}

				{assignments.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Type</TableCell>
									<TableCell>Subject</TableCell>
									<TableCell>Teacher</TableCell>
									<TableCell>Start</TableCell>
									<TableCell>End</TableCell>
									<TableCell>Status</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{assignments.map((assignment) => (
									<TableRow key={assignment.id}>
										<TableCell>{assignment.assignmentType}</TableCell>
										<TableCell>{subjectLabel(assignment.subjectId)}</TableCell>
										<TableCell>{personLabel(assignment.teacherPersonId)}</TableCell>
										<TableCell>{assignment.startDate}</TableCell>
										<TableCell>{assignment.endDate ?? "—"}</TableCell>
										<TableCell>
											<Chip label={assignment.status} size="small" />
										</TableCell>
										<TableCell>
											{assignment.status === "ACTIVE" && (
												<Button size="small" color="error" onClick={() => handleEnd(assignment.id)}>
													End
												</Button>
											)}
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={assignDialogOpen} onClose={() => setAssignDialogOpen(false)} component="form" onSubmit={handleAssign} fullWidth maxWidth="xs">
				<DialogTitle>Assign teacher</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Type" value={assignmentType} onChange={(e) => setAssignmentType(e.target.value)} required fullWidth>
							{ASSIGNMENT_TYPES.map((type) => (
								<MenuItem key={type} value={type}>
									{type}
								</MenuItem>
							))}
						</TextField>
						{assignmentType !== "HOMEROOM" && (
							<TextField select label="Subject" value={subjectId} onChange={(e) => setSubjectId(e.target.value)} required fullWidth>
								{subjects.map((subject) => (
									<MenuItem key={subject.id} value={subject.id}>
										{subject.name}
									</MenuItem>
								))}
							</TextField>
						)}
						<TextField select label="Teacher" value={teacherPersonId} onChange={(e) => setTeacherPersonId(e.target.value)} required fullWidth>
							{persons.map((person) => (
								<MenuItem key={person.id} value={person.id}>
									{person.firstName} {person.lastName}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Start date"
							type="date"
							value={startDate}
							onChange={(e) => setStartDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAssignDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !teacherPersonId || (assignmentType !== "HOMEROOM" && !subjectId)}>
						Assign
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
