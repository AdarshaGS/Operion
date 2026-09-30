import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
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
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import DeleteIcon from "@mui/icons-material/Delete";
import { ApiError } from "../../api/client";
import {
	addBankQuestion,
	addQuizQuestion,
	addQuizQuestionFromBank,
	createQuiz,
	deleteBankQuestion,
	getQuizQuestions,
	getQuizResults,
	listQuestionBank,
	listQuizzes,
	publishQuiz,
	removeQuizQuestion,
	type LearningScope,
	type QuestionResponse,
	type QuizResponse,
	type QuizResultRow,
} from "../../api/learning";
import { QuestionForm } from "./QuestionForm";

function formatWhen(iso: string): string {
	return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

/** Quiz authoring, scheduling, results and the reusable question bank (#227). */
export function QuizzesPanel({ scope }: { scope: LearningScope }) {
	const [view, setView] = useState<"quizzes" | "bank">("quizzes");
	const [quizzes, setQuizzes] = useState<QuizResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [creating, setCreating] = useState(false);
	const [managing, setManaging] = useState<QuizResponse | null>(null);
	const [results, setResults] = useState<QuizResponse | null>(null);
	const canAuthor = scope.sections.some((s) => s.writableSubjectIds.length > 0);

	function refresh() {
		listQuizzes()
			.then(setQuizzes)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load quizzes"));
	}

	useEffect(refresh, []);

	return (
		<Stack spacing={2}>
			<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
				<ToggleButtonGroup size="small" exclusive value={view} onChange={(_, next) => next && setView(next)}>
					<ToggleButton value="quizzes">Quizzes</ToggleButton>
					<ToggleButton value="bank">Question bank</ToggleButton>
				</ToggleButtonGroup>
				{canAuthor && view === "quizzes" && (
					<Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>New quiz</Button>
				)}
			</Stack>
			{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

			{view === "bank" && <QuestionBank scope={scope} />}

			{view === "quizzes" && (
				<Paper sx={{ p: 3 }}>
					{quizzes.length === 0 && <Alert severity="info">No quizzes yet.</Alert>}
					{quizzes.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Quiz</TableCell>
										<TableCell>Section</TableCell>
										<TableCell>Window</TableCell>
										<TableCell>Questions</TableCell>
										<TableCell>Attempts</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{quizzes.map((quiz) => {
										const canWrite = scope.sections.find((s) => s.sectionId === quiz.sectionId)?.writableSubjectIds.includes(quiz.subjectId) ?? false;
										return (
											<TableRow key={quiz.id}>
												<TableCell>
													{quiz.title} <Typography component="span" variant="caption" color="text.secondary">· {quiz.subjectName}</Typography>
													<Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
														<Chip size="small" label={quiz.status} color={quiz.status === "PUBLISHED" ? "success" : "default"} />
														{quiz.status === "PUBLISHED" && <Chip size="small" label={quiz.availability} variant="outlined" />}
													</Stack>
												</TableCell>
												<TableCell>{quiz.className} – {quiz.sectionName}</TableCell>
												<TableCell>
													<Typography variant="caption" sx={{ display: "block" }}>{formatWhen(quiz.opensAt)}</Typography>
													<Typography variant="caption" color="text.secondary">to {formatWhen(quiz.closesAt)}</Typography>
												</TableCell>
												<TableCell>{quiz.questionCount} ({quiz.totalMarks} marks)</TableCell>
												<TableCell>{quiz.attempts}/{quiz.enrolled}</TableCell>
												<TableCell align="right">
													<Button size="small" onClick={() => setManaging(quiz)}>{quiz.status === "DRAFT" && canWrite ? "Edit" : "Questions"}</Button>
													{quiz.status === "PUBLISHED" && <Button size="small" onClick={() => setResults(quiz)}>Results</Button>}
												</TableCell>
											</TableRow>
										);
									})}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Paper>
			)}

			{creating && <CreateQuizDialog scope={scope} onClose={() => setCreating(false)} onCreated={(quiz) => { setCreating(false); refresh(); setManaging(quiz); }} />}
			{managing && (
				<ManageQuizDialog
					quiz={managing}
					canWrite={managing.status === "DRAFT" && (scope.sections.find((s) => s.sectionId === managing.sectionId)?.writableSubjectIds.includes(managing.subjectId) ?? false)}
					onClose={() => { setManaging(null); refresh(); }}
				/>
			)}
			{results && <ResultsDialog quiz={results} onClose={() => setResults(null)} />}
		</Stack>
	);
}

function CreateQuizDialog({ scope, onClose, onCreated }: { scope: LearningScope; onClose: () => void; onCreated: (quiz: QuizResponse) => void }) {
	const sections = scope.sections.filter((s) => s.writableSubjectIds.length > 0);
	const [sectionId, setSectionId] = useState<number | "">(sections[0]?.sectionId ?? "");
	const [subjectId, setSubjectId] = useState<number | "">("");
	const [title, setTitle] = useState("");
	const [instructions, setInstructions] = useState("");
	const [opensAt, setOpensAt] = useState("");
	const [closesAt, setClosesAt] = useState("");
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
			const quiz = await createQuiz({ sectionId, subjectId, title, instructions: instructions || null, opensAt: new Date(opensAt).toISOString(), closesAt: new Date(closesAt).toISOString() });
			onCreated(quiz);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create quiz");
			setSaving(false);
		}
	}

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={handleSubmit}>
			<DialogTitle>New quiz</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField select required label="Section" value={sectionId} onChange={(e) => { setSectionId(Number(e.target.value)); setSubjectId(""); }}>
						{sections.map((s) => <MenuItem key={s.sectionId} value={s.sectionId}>{s.className} – {s.sectionName}</MenuItem>)}
					</TextField>
					<TextField select required label="Subject" value={subjectId} onChange={(e) => setSubjectId(Number(e.target.value))}>
						{subjects.map((s) => <MenuItem key={s.id} value={s.id}>{s.name}</MenuItem>)}
					</TextField>
					<TextField required label="Title" value={title} onChange={(e) => setTitle(e.target.value)} />
					<TextField label="Instructions" multiline minRows={2} value={instructions} onChange={(e) => setInstructions(e.target.value)} />
					<TextField required type="datetime-local" label="Opens" value={opensAt} onChange={(e) => setOpensAt(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
					<TextField required type="datetime-local" label="Closes" value={closesAt} onChange={(e) => setClosesAt(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" disabled={saving}>Create draft</Button>
			</DialogActions>
		</Dialog>
	);
}

function ManageQuizDialog({ quiz, canWrite, onClose }: { quiz: QuizResponse; canWrite: boolean; onClose: () => void }) {
	const [questions, setQuestions] = useState<QuestionResponse[]>([]);
	const [bank, setBank] = useState<QuestionResponse[]>([]);
	const [pickBank, setPickBank] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [published, setPublished] = useState(quiz.status === "PUBLISHED");

	function refresh() {
		getQuizQuestions(quiz.id)
			.then(setQuestions)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load questions"));
	}

	// eslint-disable-next-line react-hooks/exhaustive-deps
	useEffect(refresh, [quiz.id]);

	useEffect(() => {
		if (canWrite && pickBank) {
			listQuestionBank(quiz.subjectId).then(setBank).catch(() => {});
		}
	}, [canWrite, pickBank, quiz.subjectId]);

	async function run(action: () => Promise<unknown>, failure: string) {
		try {
			await action();
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : failure);
		}
	}

	async function handlePublish() {
		try {
			await publishQuiz(quiz.id);
			setPublished(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to publish");
		}
	}

	const editable = canWrite && !published;

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="md">
			<DialogTitle>{quiz.title}</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
					{published && <Alert severity="success">Published - questions are locked.</Alert>}
					{questions.length === 0 && <Alert severity="info">No questions yet.</Alert>}
					{questions.map((q) => (
						<Paper key={q.id} variant="outlined" sx={{ p: 1.5 }}>
							<Stack direction="row" sx={{ justifyContent: "space-between" }}>
								<Typography variant="subtitle2">{q.ordinal}. {q.text} <Typography component="span" variant="caption" color="text.secondary">({q.marks} marks)</Typography></Typography>
								{editable && <IconButton size="small" aria-label="Remove question" onClick={() => run(() => removeQuizQuestion(quiz.id, q.id), "Failed to remove question")}><DeleteIcon fontSize="small" /></IconButton>}
							</Stack>
							{q.options.map((o) => (
								<Stack key={o.id} direction="row" spacing={0.5} sx={{ alignItems: "center", ml: 1 }}>
									{o.correct ? <CheckCircleIcon fontSize="small" color="success" /> : <span style={{ width: 20 }} />}
									<Typography variant="body2">{o.text}</Typography>
								</Stack>
							))}
						</Paper>
					))}
					{editable && (
						<>
							<Divider />
							<Typography variant="subtitle2">Add a question</Typography>
							<QuestionForm submitLabel="Add question" onSubmit={async (text, marks, options) => { await addQuizQuestion(quiz.id, { text, marks, options }); refresh(); }} />
							<Button size="small" onClick={() => setPickBank((v) => !v)}>{pickBank ? "Hide question bank" : "Pick from question bank"}</Button>
							{pickBank && (
								<Stack spacing={1}>
									{bank.length === 0 && <Alert severity="info">The bank has no {quiz.subjectName} questions yet.</Alert>}
									{bank.map((q) => (
										<Stack key={q.id} direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
											<Typography variant="body2">{q.text} ({q.marks})</Typography>
											<Button size="small" onClick={() => run(() => addQuizQuestionFromBank(quiz.id, q.id), "Failed to add from bank")}>Add</Button>
										</Stack>
									))}
								</Stack>
							)}
						</>
					)}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
				{editable && <Button variant="contained" disabled={questions.length === 0} onClick={handlePublish}>Publish</Button>}
			</DialogActions>
		</Dialog>
	);
}

function ResultsDialog({ quiz, onClose }: { quiz: QuizResponse; onClose: () => void }) {
	const [rows, setRows] = useState<QuizResultRow[]>([]);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getQuizResults(quiz.id)
			.then(setRows)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load results"));
	}, [quiz.id]);

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="sm">
			<DialogTitle>{quiz.title} – results</DialogTitle>
			<DialogContent>
				{error && <Alert severity="error">{error}</Alert>}
				<Table size="small">
					<TableHead>
						<TableRow>
							<TableCell>Roll</TableCell>
							<TableCell>Student</TableCell>
							<TableCell>Score</TableCell>
						</TableRow>
					</TableHead>
					<TableBody>
						{rows.map((row) => (
							<TableRow key={row.studentId}>
								<TableCell>{row.rollNumber ?? "—"}</TableCell>
								<TableCell>{row.studentName}</TableCell>
								<TableCell>{row.attempted ? `${row.score}/${row.maxScore} (${row.percentage?.toFixed(0)}%)` : <Chip size="small" variant="outlined" label="Not attempted" />}</TableCell>
							</TableRow>
						))}
					</TableBody>
				</Table>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	);
}

function QuestionBank({ scope }: { scope: LearningScope }) {
	const authorable = Array.from(new Set(scope.sections.flatMap((s) => s.writableSubjectIds)));
	const subjects = scope.subjects.filter((s) => authorable.includes(s.id));
	const [subjectId, setSubjectId] = useState<number | "">(subjects[0]?.id ?? "");
	const [items, setItems] = useState<QuestionResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		listQuestionBank(subjectId === "" ? undefined : subjectId)
			.then(setItems)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the question bank"));
	}

	// eslint-disable-next-line react-hooks/exhaustive-deps
	useEffect(refresh, [subjectId]);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Question bank</Typography>
				{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
				<TextField select size="small" label="Subject" value={subjectId} onChange={(e) => setSubjectId(e.target.value === "" ? "" : Number(e.target.value))} sx={{ maxWidth: 240 }}>
					{subjects.map((s) => <MenuItem key={s.id} value={s.id}>{s.name}</MenuItem>)}
				</TextField>
				{items.length === 0 && <Alert severity="info">No questions in the bank for this subject.</Alert>}
				{items.map((q) => (
					<Stack key={q.id} direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="body2">{q.text} <Typography component="span" variant="caption" color="text.secondary">({q.marks} marks, {q.options.length} options)</Typography></Typography>
						<IconButton size="small" aria-label="Delete bank question" onClick={async () => { try { await deleteBankQuestion(q.id); refresh(); } catch (err) { setError(err instanceof ApiError ? err.message : "Failed to delete"); } }}><DeleteIcon fontSize="small" /></IconButton>
					</Stack>
				))}
				{subjectId !== "" && (
					<>
						<Divider />
						<Typography variant="subtitle2">Add to bank</Typography>
						<QuestionForm submitLabel="Add to bank" onSubmit={async (text, marks, options) => { await addBankQuestion({ subjectId, text, marks, options }); refresh(); }} />
					</>
				)}
			</Stack>
		</Paper>
	);
}
