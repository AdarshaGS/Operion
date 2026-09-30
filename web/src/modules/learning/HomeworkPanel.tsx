import { useEffect, useMemo, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
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
import { resolveAssetUrl } from "../../api/assets";
import { ApiError } from "../../api/client";
import {
	cancelHomework,
	createHomework,
	getHomeworkSubmissions,
	listHomework,
	reviewHomeworkSubmission,
	type HomeworkResponse,
	type HomeworkTrackingRow,
	type LearningScope,
} from "../../api/learning";
import { AttachmentField } from "./AttachmentField";

function submissionChip(row: HomeworkTrackingRow) {
	if (row.status === "NOT_SUBMITTED") {
		return <Chip label="Not submitted" size="small" variant="outlined" />;
	}
	return (
		<Stack direction="row" spacing={0.5}>
			<Chip label={row.status === "REVIEWED" ? "Reviewed" : "Submitted"} size="small" color={row.status === "REVIEWED" ? "success" : "info"} />
			{row.late && <Chip label="Late" size="small" color="warning" />}
		</Stack>
	);
}

/** Assign homework per section/subject and track who has submitted (#226). Shared by the admin
 * Learning page and the teacher portal - the server scopes what each caller sees and may write. */
export function HomeworkPanel({ scope }: { scope: LearningScope }) {
	const [items, setItems] = useState<HomeworkResponse[]>([]);
	const [sectionFilter, setSectionFilter] = useState<number | "">("");
	const [error, setError] = useState<string | null>(null);
	const [creating, setCreating] = useState(false);
	const [tracking, setTracking] = useState<HomeworkResponse | null>(null);

	const writableSections = useMemo(() => scope.sections.filter((s) => s.writableSubjectIds.length > 0), [scope]);

	function refresh() {
		listHomework(sectionFilter === "" ? undefined : sectionFilter)
			.then(setItems)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load homework"));
	}

	// eslint-disable-next-line react-hooks/exhaustive-deps
	useEffect(refresh, [sectionFilter]);

	async function handleCancel(id: number) {
		try {
			await cancelHomework(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to cancel homework");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }} spacing={2}>
					<Typography variant="h6">Homework</Typography>
					<Stack direction="row" spacing={1}>
						<TextField select size="small" label="Section" value={sectionFilter} onChange={(e) => setSectionFilter(e.target.value === "" ? "" : Number(e.target.value))} sx={{ minWidth: 180 }}>
							<MenuItem value="">All sections</MenuItem>
							{scope.sections.map((s) => (
								<MenuItem key={s.sectionId} value={s.sectionId}>
									{s.className} – {s.sectionName}
								</MenuItem>
							))}
						</TextField>
						{writableSections.length > 0 && (
							<Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>
								Assign
							</Button>
						)}
					</Stack>
				</Stack>

				{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
				{items.length === 0 && <Alert severity="info">No homework yet.</Alert>}

				{items.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Title</TableCell>
									<TableCell>Section</TableCell>
									<TableCell>Subject</TableCell>
									<TableCell>Due</TableCell>
									<TableCell>Submitted</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{items.map((hw) => (
									<TableRow key={hw.id} sx={{ opacity: hw.status === "CANCELLED" ? 0.5 : 1 }}>
										<TableCell>
											{hw.title}
											{hw.status === "CANCELLED" && <Chip label="Cancelled" size="small" sx={{ ml: 1 }} />}
											{hw.attachmentUrl && (
												<Link href={resolveAssetUrl(hw.attachmentUrl)} target="_blank" rel="noreferrer" sx={{ ml: 1 }} variant="caption">
													attachment
												</Link>
											)}
										</TableCell>
										<TableCell>{hw.className} – {hw.sectionName}</TableCell>
										<TableCell>{hw.subjectName}</TableCell>
										<TableCell>{hw.dueDate}</TableCell>
										<TableCell>{hw.submitted}/{hw.enrolled}</TableCell>
										<TableCell align="right">
											<Button size="small" onClick={() => setTracking(hw)}>Track</Button>
											{hw.status === "ACTIVE" && scope.sections.find((s) => s.sectionId === hw.sectionId)?.writableSubjectIds.includes(hw.subjectId) && (
												<Button size="small" color="error" onClick={() => handleCancel(hw.id)}>Cancel</Button>
											)}
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			{creating && (
				<AssignDialog
					scope={scope}
					onClose={() => setCreating(false)}
					onCreated={() => {
						setCreating(false);
						refresh();
					}}
				/>
			)}
			{tracking && (
				<TrackingDialog
					homework={tracking}
					canReview={scope.sections.find((s) => s.sectionId === tracking.sectionId)?.writableSubjectIds.includes(tracking.subjectId) ?? false}
					onClose={() => {
						setTracking(null);
						refresh();
					}}
				/>
			)}
		</Paper>
	);
}

function AssignDialog({ scope, onClose, onCreated }: { scope: LearningScope; onClose: () => void; onCreated: () => void }) {
	const sections = scope.sections.filter((s) => s.writableSubjectIds.length > 0);
	const [sectionId, setSectionId] = useState<number | "">(sections[0]?.sectionId ?? "");
	const [subjectId, setSubjectId] = useState<number | "">("");
	const [title, setTitle] = useState("");
	const [description, setDescription] = useState("");
	const [dueDate, setDueDate] = useState("");
	const [attachmentRef, setAttachmentRef] = useState<string | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);

	const subjects = scope.subjects.filter((s) => sections.find((sec) => sec.sectionId === sectionId)?.writableSubjectIds.includes(s.id));

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		if (sectionId === "" || subjectId === "") {
			setError("Choose a section and subject");
			return;
		}
		setSaving(true);
		try {
			await createHomework({ sectionId, subjectId, title, description: description || null, dueDate, attachmentRef });
			onCreated();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to assign homework");
			setSaving(false);
		}
	}

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={handleSubmit}>
			<DialogTitle>Assign homework</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField select required label="Section" value={sectionId} onChange={(e) => { setSectionId(Number(e.target.value)); setSubjectId(""); }}>
						{sections.map((s) => (
							<MenuItem key={s.sectionId} value={s.sectionId}>{s.className} – {s.sectionName}</MenuItem>
						))}
					</TextField>
					<TextField select required label="Subject" value={subjectId} onChange={(e) => setSubjectId(Number(e.target.value))}>
						{subjects.map((s) => (
							<MenuItem key={s.id} value={s.id}>{s.name}</MenuItem>
						))}
					</TextField>
					<TextField required label="Title" value={title} onChange={(e) => setTitle(e.target.value)} />
					<TextField label="Instructions" multiline minRows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
					<TextField required type="date" label="Due date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
					<AttachmentField value={attachmentRef} onChange={setAttachmentRef} onError={setError} />
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" disabled={saving}>Assign</Button>
			</DialogActions>
		</Dialog>
	);
}

function TrackingDialog({ homework, canReview, onClose }: { homework: HomeworkResponse; canReview: boolean; onClose: () => void }) {
	const [rows, setRows] = useState<HomeworkTrackingRow[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [feedbackFor, setFeedbackFor] = useState<HomeworkTrackingRow | null>(null);
	const [feedback, setFeedback] = useState("");

	function refresh() {
		getHomeworkSubmissions(homework.id)
			.then(setRows)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load submissions"));
	}

	// eslint-disable-next-line react-hooks/exhaustive-deps
	useEffect(refresh, [homework.id]);

	async function handleReview() {
		if (!feedbackFor?.submissionId) {
			return;
		}
		try {
			await reviewHomeworkSubmission(feedbackFor.submissionId, feedback);
			setFeedbackFor(null);
			setFeedback("");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save review");
		}
	}

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="md">
			<DialogTitle>{homework.title} – submissions</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Roll</TableCell>
								<TableCell>Student</TableCell>
								<TableCell>Status</TableCell>
								<TableCell>Answer</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{rows.map((row) => (
								<TableRow key={row.studentId}>
									<TableCell>{row.rollNumber ?? "—"}</TableCell>
									<TableCell>{row.studentName}</TableCell>
									<TableCell>{submissionChip(row)}</TableCell>
									<TableCell sx={{ maxWidth: 260 }}>
										{row.submissionText}
										{row.attachmentUrl && (
											<Link href={resolveAssetUrl(row.attachmentUrl)} target="_blank" rel="noreferrer" sx={{ ml: 1 }}>
												file
											</Link>
										)}
										{row.feedback && <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>Feedback: {row.feedback}</Typography>}
									</TableCell>
									<TableCell align="right">
										{canReview && row.status === "SUBMITTED" && (
											<Button size="small" onClick={() => { setFeedbackFor(row); setFeedback(""); }}>Review</Button>
										)}
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
					{feedbackFor && (
						<Stack spacing={1}>
							<TextField label={`Feedback for ${feedbackFor.studentName}`} multiline minRows={2} value={feedback} onChange={(e) => setFeedback(e.target.value)} />
							<Stack direction="row" spacing={1}>
								<Button variant="contained" size="small" onClick={handleReview}>Mark reviewed</Button>
								<Button size="small" onClick={() => setFeedbackFor(null)}>Cancel</Button>
							</Stack>
						</Stack>
					)}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	);
}
