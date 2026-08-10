import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
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
import {
	cancelAnnouncement,
	createAnnouncement,
	listAnnouncements,
	publishAnnouncement,
	type AnnouncementResponse,
} from "../../api/announcements";
import { type CampusResponse, listCampuses } from "../../api/campuses";
import { ApiError } from "../../api/client";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { listStudents, type StudentResponse } from "../../api/students";
import { type PersonResponse, listPersons } from "../../api/persons";

const AUDIENCE_TYPES = ["ORG", "CAMPUS", "CLASS", "SECTION", "INDIVIDUAL"];

export function AnnouncementsPanel() {
	const [drafts, setDrafts] = useState<AnnouncementResponse[]>([]);
	const [published, setPublished] = useState<AnnouncementResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [title, setTitle] = useState("");
	const [body, setBody] = useState("");
	const [audienceType, setAudienceType] = useState("ORG");
	const [campusId, setCampusId] = useState("");
	const [classId, setClassId] = useState("");
	const [sectionId, setSectionId] = useState("");
	const [studentId, setStudentId] = useState("");

	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
	const [sections, setSections] = useState<SectionResponse[]>([]);
	const [students, setStudents] = useState<StudentResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);

	function refresh() {
		listAnnouncements("DRAFT")
			.then(setDrafts)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load drafts"));
		listAnnouncements("PUBLISHED")
			.then(setPublished)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load published feed"));
	}

	useEffect(refresh, []);

	useEffect(() => {
		listCampuses().then(setCampuses).catch(() => {});
		listSchoolClasses().then(setClasses).catch(() => {});
		listStudents().then(setStudents).catch(() => {});
		listPersons().then(setPersons).catch(() => {});
	}, []);

	useEffect(() => {
		setSectionId("");
		if (!classId) {
			setSections([]);
			return;
		}
		listSections(Number(classId)).then(setSections).catch(() => {});
	}, [classId]);

	function studentLabel(student: StudentResponse): string {
		const person = persons.find((p) => p.id === student.personId);
		return person ? `${person.firstName} ${person.lastName} (${student.admissionNumber})` : student.admissionNumber;
	}

	function resetForm() {
		setTitle("");
		setBody("");
		setAudienceType("ORG");
		setCampusId("");
		setClassId("");
		setSectionId("");
		setStudentId("");
	}

	function audienceRefId(): number | null {
		if (audienceType === "CLASS") return classId ? Number(classId) : null;
		if (audienceType === "SECTION") return sectionId ? Number(sectionId) : null;
		if (audienceType === "INDIVIDUAL") return studentId ? Number(studentId) : null;
		return null;
	}

	async function handleCreate(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createAnnouncement({
				title,
				body,
				audienceType,
				campusId: audienceType === "CAMPUS" ? Number(campusId) : null,
				audienceRefId: audienceRefId(),
			});
			resetForm();
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create announcement");
		} finally {
			setSubmitting(false);
		}
	}

	async function handlePublish(id: number) {
		try {
			await publishAnnouncement(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to publish announcement");
		}
	}

	async function handleCancel(id: number) {
		try {
			await cancelAnnouncement(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to cancel announcement");
		}
	}

	return (
		<Stack spacing={3}>
			<Paper sx={{ p: 3 }}>
				<Stack spacing={2} component="form" onSubmit={handleCreate}>
					<Typography variant="h6">Compose announcement</Typography>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required fullWidth />
					<TextField label="Body" value={body} onChange={(e) => setBody(e.target.value)} required multiline rows={3} fullWidth />
					<TextField select label="Audience" value={audienceType} onChange={(e) => setAudienceType(e.target.value)} sx={{ maxWidth: 240 }}>
						{AUDIENCE_TYPES.map((type) => (
							<MenuItem key={type} value={type}>
								{type}
							</MenuItem>
						))}
					</TextField>

					{audienceType === "CAMPUS" && (
						<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value)} required sx={{ maxWidth: 300 }}>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
					)}

					{(audienceType === "CLASS" || audienceType === "SECTION") && (
						<TextField select label="Class" value={classId} onChange={(e) => setClassId(e.target.value)} required sx={{ maxWidth: 300 }}>
							{classes.map((schoolClass) => (
								<MenuItem key={schoolClass.id} value={schoolClass.id}>
									{schoolClass.displayName ?? `Class #${schoolClass.id}`}
								</MenuItem>
							))}
						</TextField>
					)}

					{audienceType === "SECTION" && (
						<TextField
							select
							label="Section"
							value={sectionId}
							onChange={(e) => setSectionId(e.target.value)}
							required
							disabled={!classId}
							sx={{ maxWidth: 300 }}
						>
							{sections.map((section) => (
								<MenuItem key={section.id} value={section.id}>
									{section.name}
								</MenuItem>
							))}
						</TextField>
					)}

					{audienceType === "INDIVIDUAL" && (
						<TextField select label="Student" value={studentId} onChange={(e) => setStudentId(e.target.value)} required sx={{ maxWidth: 300 }}>
							{students.map((student) => (
								<MenuItem key={student.id} value={student.id}>
									{studentLabel(student)}
								</MenuItem>
							))}
						</TextField>
					)}

					<Box>
						<Button type="submit" variant="contained" disabled={submitting}>
							Save as draft
						</Button>
					</Box>
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Drafts</Typography>
					{drafts.length === 0 && <Alert severity="info">No drafts.</Alert>}
					{drafts.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Title</TableCell>
										<TableCell>Audience</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{drafts.map((announcement) => (
										<TableRow key={announcement.id}>
											<TableCell>{announcement.title}</TableCell>
											<TableCell>{announcement.audienceType}</TableCell>
											<TableCell>
												<Button size="small" onClick={() => handlePublish(announcement.id)}>
													Publish
												</Button>
												<Button size="small" color="error" onClick={() => handleCancel(announcement.id)}>
													Cancel
												</Button>
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Published feed</Typography>
					{published.length === 0 && <Alert severity="info">No published announcements yet.</Alert>}
					{published.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Title</TableCell>
										<TableCell>Audience</TableCell>
										<TableCell>Published</TableCell>
										<TableCell>Status</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{published.map((announcement) => (
										<TableRow key={announcement.id}>
											<TableCell>{announcement.title}</TableCell>
											<TableCell>{announcement.audienceType}</TableCell>
											<TableCell>{announcement.publishedAt ? new Date(announcement.publishedAt).toLocaleString() : "—"}</TableCell>
											<TableCell>
												<Chip label={announcement.status} size="small" />
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>
		</Stack>
	);
}
