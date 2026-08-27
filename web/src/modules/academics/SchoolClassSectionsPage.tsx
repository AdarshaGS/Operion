import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
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
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import {
	assignSubjectToClass,
	changeClassSubjectStatus,
	listClassSubjects,
	type ClassSubjectResponse,
} from "../../api/classSubjects";
import { ApiError } from "../../api/client";
import { listGradeLevels, type GradeLevelResponse } from "../../api/gradeLevels";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { changeSectionStatus, createSection, listSections, type SectionResponse } from "../../api/sections";
import { listSubjects, type SubjectResponse } from "../../api/subjects";

/** No GET-by-id endpoint exists on SchoolClassController - at the scale of "classes in
 * one school" (tens, not thousands), resolving the current class from the already-cheap
 * list call is simpler than adding a single-purpose endpoint for it. */
export function SchoolClassSectionsPage() {
	const { classId } = useParams<{ classId: string }>();
	const navigate = useNavigate();
	const [schoolClass, setSchoolClass] = useState<SchoolClassResponse | null>(null);
	const [gradeLevels, setGradeLevels] = useState<GradeLevelResponse[]>([]);
	const [sections, setSections] = useState<SectionResponse[] | null>(null);
	const [subjects, setSubjects] = useState<SubjectResponse[]>([]);
	const [classSubjects, setClassSubjects] = useState<ClassSubjectResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [name, setName] = useState("");
	const [capacity, setCapacity] = useState("");
	const [room, setRoom] = useState("");
	const [submitting, setSubmitting] = useState(false);

	const [subjectDialogOpen, setSubjectDialogOpen] = useState(false);
	const [subjectId, setSubjectId] = useState("");
	const [mandatory, setMandatory] = useState(true);

	function refreshSections(id: number) {
		listSections(id)
			.then(setSections)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load sections"));
	}

	function refreshClassSubjects(id: number) {
		listClassSubjects(id)
			.then(setClassSubjects)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load class subjects"));
	}

	useEffect(() => {
		if (!classId) return;
		const id = Number(classId);
		listSchoolClasses()
			.then((classes) => setSchoolClass(classes.find((c) => c.id === id) ?? null))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load class"));
		listGradeLevels().then(setGradeLevels).catch(() => {});
		listSubjects().then(setSubjects).catch(() => {});
		refreshSections(id);
		refreshClassSubjects(id);
	}, [classId]);

	function openSubjectDialog() {
		listSubjects().then(setSubjects).catch(() => {});
		setSubjectDialogOpen(true);
	}

	async function handleAssignSubject(event: FormEvent) {
		event.preventDefault();
		if (!classId) return;
		setSubmitting(true);
		try {
			await assignSubjectToClass(Number(classId), { subjectId: Number(subjectId), mandatory });
			setSubjectId("");
			setMandatory(true);
			setSubjectDialogOpen(false);
			refreshClassSubjects(Number(classId));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to assign subject");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleToggleClassSubjectStatus(classSubject: ClassSubjectResponse) {
		if (!classId) return;
		try {
			await changeClassSubjectStatus(Number(classId), classSubject.id, classSubject.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refreshClassSubjects(Number(classId));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update class subject status");
		}
	}

	async function handleToggleSectionStatus(section: SectionResponse) {
		if (!classId) return;
		try {
			await changeSectionStatus(Number(classId), section.id, section.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refreshSections(Number(classId));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update section status");
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		if (!classId) return;
		setSubmitting(true);
		try {
			await createSection(Number(classId), { name, capacity: capacity ? Number(capacity) : null, room: room || null });
			setName("");
			setCapacity("");
			setRoom("");
			setDialogOpen(false);
			refreshSections(Number(classId));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create section");
		} finally {
			setSubmitting(false);
		}
	}

	const gradeLevelName = schoolClass ? gradeLevels.find((level) => level.id === schoolClass.gradeLevelId)?.name : undefined;

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/academics")}>
					Back to academics
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				{schoolClass?.displayName ?? gradeLevelName ?? `Class #${classId}`}
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Class subjects</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={openSubjectDialog}>
							Assign subject
						</Button>
					</Box>

					{classSubjects.length === 0 && <Alert severity="info">No subjects assigned to this class yet.</Alert>}

					{classSubjects.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Subject</TableCell>
										<TableCell>Mandatory</TableCell>
										<TableCell>Status</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{classSubjects.map((classSubject) => (
										<TableRow key={classSubject.id}>
											<TableCell>{subjects.find((s) => s.id === classSubject.subjectId)?.name ?? `Subject #${classSubject.subjectId}`}</TableCell>
											<TableCell>{classSubject.mandatory ? "Yes" : "No"}</TableCell>
											<TableCell>
												<Chip label={classSubject.status} size="small" />
											</TableCell>
											<TableCell>
												<Button size="small" onClick={() => handleToggleClassSubjectStatus(classSubject)}>
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

				<Dialog open={subjectDialogOpen} onClose={() => setSubjectDialogOpen(false)} component="form" onSubmit={handleAssignSubject} fullWidth maxWidth="xs">
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
						</Stack>
					</DialogContent>
					<DialogActions>
						<Button onClick={() => setSubjectDialogOpen(false)}>Cancel</Button>
						<Button type="submit" variant="contained" disabled={submitting || !subjectId}>
							Assign
						</Button>
					</DialogActions>
				</Dialog>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Sections</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
							Add section
						</Button>
					</Box>

					{!sections && (
						<Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
							<CircularProgress size={24} />
						</Box>
					)}

					{sections && sections.length === 0 && <Alert severity="info">No sections yet.</Alert>}

					{sections && sections.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Name</TableCell>
										<TableCell>Capacity</TableCell>
										<TableCell>Room</TableCell>
										<TableCell>Status</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{sections.map((section) => (
										<TableRow
											key={section.id}
											hover
											sx={{ cursor: "pointer" }}
											onClick={() => navigate(`/academics/classes/${classId}/sections/${section.id}`)}
										>
											<TableCell>{section.name}</TableCell>
											<TableCell>{section.capacity ?? "—"}</TableCell>
											<TableCell>{section.room ?? "—"}</TableCell>
											<TableCell>
												<Chip label={section.status} size="small" />
											</TableCell>
											<TableCell onClick={(e) => e.stopPropagation()}>
												<Button size="small" onClick={() => handleToggleSectionStatus(section)}>
													{section.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
												</Button>
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>

				<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
					<DialogTitle>Add section</DialogTitle>
					<DialogContent>
						<Stack spacing={2} sx={{ mt: 1 }}>
							<TextField label="Name" placeholder="A" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
							<TextField label="Capacity" type="number" value={capacity} onChange={(e) => setCapacity(e.target.value)} fullWidth />
							<TextField label="Room" value={room} onChange={(e) => setRoom(e.target.value)} fullWidth />
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
		</Stack>
	);
}
