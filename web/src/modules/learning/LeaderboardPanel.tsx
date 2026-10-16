import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import EmojiEventsIcon from "@mui/icons-material/EmojiEvents";
import { ApiError } from "../../api/client";
import {
	getStudentLeaderboard,
	getTeacherLeaderboard,
	grantReward,
	listSectionRewards,
	listSectionStudents,
	type LearningScope,
	type LeaderboardTrack,
	type Reward,
	type SectionStudent,
	type StudentLeaderboardEntry,
	type TeacherLeaderboardEntry,
} from "../../api/learning";

const TRACK_LABEL: Record<LeaderboardTrack, string> = { QUIZ: "Quizzes", ACADEMIC: "Academic", REWARD: "Rewards" };
const SCORE_HEADER: Record<LeaderboardTrack, string> = { QUIZ: "Avg quiz %", ACADEMIC: "Marks %", REWARD: "Points" };

/** Student leaderboards per section, ranked separately for quizzes / academic assessments / rewards, plus reward granting and (admins) the activity-based teacher board (#231). */
export function LeaderboardPanel({ scope }: { scope: LearningScope }) {
	const [sectionId, setSectionId] = useState<number | "">(scope.sections[0]?.sectionId ?? "");
	const [track, setTrack] = useState<LeaderboardTrack>("QUIZ");
	const [entries, setEntries] = useState<StudentLeaderboardEntry[]>([]);
	const [rewards, setRewards] = useState<Reward[]>([]);
	const [teachers, setTeachers] = useState<TeacherLeaderboardEntry[] | null>(null);
	const [granting, setGranting] = useState<StudentLeaderboardEntry | "pick" | null>(null);
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		if (sectionId === "") {
			return;
		}
		getStudentLeaderboard(sectionId, track)
			.then(setEntries)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the leaderboard"));
		listSectionRewards(sectionId)
			.then(setRewards)
			.catch(() => setRewards([]));
	}

	// eslint-disable-next-line react-hooks/exhaustive-deps
	useEffect(refresh, [sectionId, track]);

	useEffect(() => {
		if (scope.manager) {
			getTeacherLeaderboard().then(setTeachers).catch(() => setTeachers(null));
		}
	}, [scope.manager]);

	// Rewards are granted by managers and by teachers of the section; a view-only user can only read the boards.
	const canGrant = scope.manager || scope.sections.some((s) => s.sectionId === sectionId && s.writableSubjectIds.length > 0);

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Stack direction="row" spacing={2} sx={{ alignItems: "center", flexWrap: "wrap" }}>
						<TextField select size="small" label="Section" value={sectionId} onChange={(e) => setSectionId(Number(e.target.value))} sx={{ minWidth: 200 }}>
							{scope.sections.map((s) => <MenuItem key={s.sectionId} value={s.sectionId}>{s.className} – {s.sectionName}</MenuItem>)}
						</TextField>
						<ToggleButtonGroup size="small" exclusive value={track} onChange={(_, next) => next && setTrack(next)}>
							{(Object.keys(TRACK_LABEL) as LeaderboardTrack[]).map((t) => <ToggleButton key={t} value={t}>{TRACK_LABEL[t]}</ToggleButton>)}
						</ToggleButtonGroup>
						<Button startIcon={<EmojiEventsIcon />} variant="outlined" disabled={sectionId === "" || !canGrant} onClick={() => setGranting("pick")}>Grant reward</Button>
					</Stack>
					{entries.length === 0 && <Alert severity="info">Nobody in this section has a {TRACK_LABEL[track].toLowerCase()} score yet.</Alert>}
					{entries.length > 0 && (
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Rank</TableCell>
									<TableCell>Student</TableCell>
									<TableCell>{SCORE_HEADER[track]}</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{entries.map((e) => (
									<TableRow key={e.enrollmentId}>
										<TableCell>{e.rank}</TableCell>
										<TableCell>{e.studentName}</TableCell>
										<TableCell>{track === "REWARD" ? e.score : e.score.toFixed(1)}</TableCell>
										<TableCell align="right"><Button size="small" onClick={() => setGranting(e)}>Reward</Button></TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					)}
					{track === "REWARD" && rewards.length > 0 && (
						<>
							<Divider />
							<Typography variant="subtitle2">Recent rewards</Typography>
							{rewards.slice(0, 10).map((r) => (
								<Typography key={r.id} variant="body2">{r.studentName}: {r.title} (+{r.points}){r.reason ? ` - ${r.reason}` : ""} <Typography component="span" variant="caption" color="text.secondary">by {r.grantedByName}</Typography></Typography>
							))}
						</>
					)}
				</Stack>
			</Paper>

			{teachers && (
				<Paper sx={{ p: 3 }}>
					<Typography variant="h6">Teacher leaderboard</Typography>
					<Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
						Ranked by activity, not ratings: homework assigned ×2, quizzes published ×3, materials shared ×1, lesson plans approved ×3, answer sheets released ×2.
					</Typography>
					{teachers.length === 0 && <Alert severity="info">No teacher activity yet.</Alert>}
					{teachers.length > 0 && (
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Rank</TableCell>
									<TableCell>Teacher</TableCell>
									<TableCell>Points</TableCell>
									<TableCell>Homework</TableCell>
									<TableCell>Quizzes</TableCell>
									<TableCell>Materials</TableCell>
									<TableCell>Plans</TableCell>
									<TableCell>Sheets</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{teachers.map((t) => (
									<TableRow key={t.teacherName}>
										<TableCell>{t.rank}</TableCell>
										<TableCell>{t.teacherName}</TableCell>
										<TableCell>{t.points}</TableCell>
										<TableCell>{t.homeworkAssigned}</TableCell>
										<TableCell>{t.quizzesPublished}</TableCell>
										<TableCell>{t.materialsShared}</TableCell>
										<TableCell>{t.lessonPlansApproved}</TableCell>
										<TableCell>{t.sheetsReleased}</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					)}
				</Paper>
			)}

			{granting && (
				<GrantDialog
					sectionId={sectionId}
					initialEnrollmentId={granting === "pick" ? null : granting.enrollmentId}
					onClose={() => setGranting(null)}
					onGranted={() => { setGranting(null); refresh(); }}
				/>
			)}
		</Stack>
	);
}

function GrantDialog({ sectionId, initialEnrollmentId, onClose, onGranted }: { sectionId: number | ""; initialEnrollmentId: number | null; onClose: () => void; onGranted: () => void }) {
	const [students, setStudents] = useState<SectionStudent[]>([]);
	const [enrollmentId, setEnrollmentId] = useState<number | "">(initialEnrollmentId ?? "");
	const [title, setTitle] = useState("");
	const [points, setPoints] = useState("5");
	const [reason, setReason] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		if (sectionId !== "") {
			listSectionStudents(sectionId).then(setStudents).catch(() => setStudents([]));
		}
	}, [sectionId]);

	async function handleGrant() {
		if (enrollmentId === "") {
			setError("Choose a student");
			return;
		}
		setSaving(true);
		try {
			await grantReward(enrollmentId, title, Number(points), reason.trim() || null);
			onGranted();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to grant the reward");
			setSaving(false);
		}
	}

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="xs">
			<DialogTitle>Grant reward</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField select label="Student" value={enrollmentId} onChange={(e) => setEnrollmentId(Number(e.target.value))}>
						{students.map((s) => <MenuItem key={s.enrollmentId} value={s.enrollmentId}>{s.rollNumber ? `${s.rollNumber}. ` : ""}{s.studentName}</MenuItem>)}
					</TextField>
					<TextField required label="Title" placeholder="e.g. Helped a classmate" value={title} onChange={(e) => setTitle(e.target.value)} />
					<TextField required type="number" label="Points (1-100)" value={points} onChange={(e) => setPoints(e.target.value)} slotProps={{ htmlInput: { min: 1, max: 100 } }} />
					<TextField label="Reason (optional)" multiline minRows={2} value={reason} onChange={(e) => setReason(e.target.value)} />
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button variant="contained" disabled={saving} onClick={handleGrant}>Grant</Button>
			</DialogActions>
		</Dialog>
	);
}
