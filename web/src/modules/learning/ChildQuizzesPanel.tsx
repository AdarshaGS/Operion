import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import Paper from "@mui/material/Paper";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import {
	getChildQuizHistory,
	getChildQuizQuestions,
	getChildQuizResult,
	getChildQuizzes,
	submitChildQuizAttempt,
	type AttemptQuestion,
	type AttemptResult,
	type AttemptSummary,
	type ChildQuiz,
} from "../../api/learning";

function when(iso: string): string {
	return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

/** Quizzes for one linked child: attempt while open (once), see the score, and the per-student history (#227). */
export function ChildQuizzesPanel({ studentId }: { studentId: number }) {
	const [quizzes, setQuizzes] = useState<ChildQuiz[] | null>(null);
	const [history, setHistory] = useState<AttemptSummary[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [attempting, setAttempting] = useState<ChildQuiz | null>(null);
	const [result, setResult] = useState<AttemptResult | null>(null);

	function refresh() {
		Promise.all([getChildQuizzes(studentId), getChildQuizHistory(studentId)])
			.then(([q, h]) => {
				setQuizzes(q);
				setHistory(h);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load quizzes"));
	}

	// eslint-disable-next-line react-hooks/exhaustive-deps
	useEffect(refresh, [studentId]);

	async function openResult(quiz: ChildQuiz) {
		try {
			setResult(await getChildQuizResult(studentId, quiz.id));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to load the result");
		}
	}

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
			{quizzes?.length === 0 && <Alert severity="info">No quizzes have been published yet.</Alert>}
			{quizzes?.map((quiz) => (
				<Paper key={quiz.id} sx={{ p: 2 }}>
					<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }} spacing={2}>
						<Stack spacing={0.5}>
							<Typography variant="subtitle1">{quiz.title} <Typography component="span" variant="body2" color="text.secondary">· {quiz.subjectName}</Typography></Typography>
							<Typography variant="body2" color="text.secondary">{when(quiz.opensAt)} to {when(quiz.closesAt)} · {quiz.questionCount} questions, {quiz.totalMarks} marks</Typography>
							{quiz.instructions && <Typography variant="body2">{quiz.instructions}</Typography>}
						</Stack>
						<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
							{quiz.attempted ? <Chip color="success" label={`${quiz.score}/${quiz.maxScore}`} /> : <Chip variant="outlined" label={quiz.availability} color={quiz.availability === "OPEN" ? "warning" : "default"} />}
							{quiz.attempted && <Button size="small" onClick={() => openResult(quiz)}>Review</Button>}
							{!quiz.attempted && quiz.availability === "OPEN" && <Button size="small" variant="contained" onClick={() => setAttempting(quiz)}>Attempt</Button>}
						</Stack>
					</Stack>
				</Paper>
			))}
			{history.length > 0 && (
				<Paper sx={{ p: 2 }}>
					<Typography variant="subtitle1" sx={{ mb: 1 }}>Quiz history</Typography>
					{history.map((h) => (
						<Stack key={h.quizId} direction="row" sx={{ justifyContent: "space-between" }}>
							<Typography variant="body2">{h.quizTitle} · {h.subjectName} · {when(h.submittedAt)}</Typography>
							<Typography variant="body2">{h.score}/{h.maxScore} ({h.percentage.toFixed(0)}%)</Typography>
						</Stack>
					))}
				</Paper>
			)}
			{attempting && <AttemptDialog studentId={studentId} quiz={attempting} onClose={() => setAttempting(null)} onSubmitted={() => { setAttempting(null); refresh(); }} />}
			{result && (
				<Dialog open onClose={() => setResult(null)} fullWidth maxWidth="sm">
					<DialogTitle>{result.summary.quizTitle}: {result.summary.score}/{result.summary.maxScore}</DialogTitle>
					<DialogContent>
						<Stack spacing={2} sx={{ mt: 1 }}>
							{!result.correctAnswersShown && <Alert severity="info">The correct answers are shown once the quiz closes.</Alert>}
							{result.answers.map((a, i) => (
								<Stack key={a.questionId} spacing={0.5}>
									<Typography variant="subtitle2">{i + 1}. {a.text} <Chip size="small" label={a.correct ? `+${a.marksAwarded}` : "0"} color={a.correct ? "success" : "error"} sx={{ ml: 1 }} /></Typography>
									{a.options.map((o) => (
										<Typography key={o.id} variant="body2" sx={{ ml: 1, fontWeight: o.id === a.selectedOptionId || o.id === a.correctOptionId ? 600 : 400, color: o.id === a.correctOptionId ? "success.main" : o.id === a.selectedOptionId ? "error.main" : "text.primary" }}>
											{o.id === a.selectedOptionId ? "● " : "○ "}{o.text}{o.id === a.correctOptionId ? " ✓" : ""}
										</Typography>
									))}
								</Stack>
							))}
						</Stack>
					</DialogContent>
					<DialogActions><Button onClick={() => setResult(null)}>Close</Button></DialogActions>
				</Dialog>
			)}
		</Stack>
	);
}

function AttemptDialog({ studentId, quiz, onClose, onSubmitted }: { studentId: number; quiz: ChildQuiz; onClose: () => void; onSubmitted: () => void }) {
	const [questions, setQuestions] = useState<AttemptQuestion[]>([]);
	const [answers, setAnswers] = useState<Record<number, number>>({});
	const [error, setError] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		getChildQuizQuestions(studentId, quiz.id)
			.then(setQuestions)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the questions"));
	}, [studentId, quiz.id]);

	async function handleSubmit() {
		setSaving(true);
		try {
			await submitChildQuizAttempt(studentId, quiz.id, questions.map((q) => ({ questionId: q.id, optionId: answers[q.id] ?? null })));
			onSubmitted();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to submit the quiz");
			setSaving(false);
		}
	}

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="sm">
			<DialogTitle>{quiz.title}</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<Alert severity="warning">Only one attempt is allowed - answers can't be changed after submitting.</Alert>
					{questions.map((q, i) => (
						<FormControl key={q.id}>
							<Typography variant="subtitle2">{i + 1}. {q.text} <Typography component="span" variant="caption" color="text.secondary">({q.marks} marks)</Typography></Typography>
							<RadioGroup value={answers[q.id] ?? ""} onChange={(e) => setAnswers((prev) => ({ ...prev, [q.id]: Number(e.target.value) }))}>
								{q.options.map((o) => <FormControlLabel key={o.id} value={o.id} control={<Radio size="small" />} label={o.text} />)}
							</RadioGroup>
						</FormControl>
					))}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button variant="contained" disabled={saving || questions.length === 0} onClick={handleSubmit}>Submit quiz</Button>
			</DialogActions>
		</Dialog>
	);
}
