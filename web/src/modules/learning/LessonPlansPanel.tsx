import { useCallback, useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
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
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { ApiError } from "../../api/client";
import {
	approveLessonPlan,
	createLessonPlan,
	editLessonPlan,
	getLessonPlanCalendar,
	listLessonPlans,
	rejectLessonPlan,
	submitLessonPlan,
	type LearningScope,
	type LessonPlanResponse,
	type LessonPlanStatus,
} from "../../api/learning";
import { LessonPlanCalendar, PlanBody } from "./LessonPlanCalendar";

const STATUS_COLOR: Record<LessonPlanStatus, "default" | "info" | "success" | "error"> = {
	DRAFT: "default",
	SUBMITTED: "info",
	APPROVED: "success",
	REJECTED: "error",
};

/** Lesson planner (#229): teachers draft and submit, reviewers approve/reject, everyone sees the approved calendar. */
export function LessonPlansPanel({ scope }: { scope: LearningScope }) {
	const [view, setView] = useState<"plans" | "calendar">("plans");
	const [plans, setPlans] = useState<LessonPlanResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [editing, setEditing] = useState<LessonPlanResponse | "new" | null>(null);
	const [reviewing, setReviewing] = useState<LessonPlanResponse | null>(null);
	const canAuthor = scope.sections.some((s) => s.writableSubjectIds.length > 0);

	const refresh = useCallback(() => {
		listLessonPlans()
			.then(setPlans)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load lesson plans"));
	}, []);

	useEffect(refresh, [refresh]);

	async function handleSubmitPlan(id: number) {
		try {
			await submitLessonPlan(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to submit");
		}
	}

	return (
		<Stack spacing={2}>
			<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
				<ToggleButtonGroup size="small" exclusive value={view} onChange={(_, next) => next && setView(next)}>
					<ToggleButton value="plans">Plans</ToggleButton>
					<ToggleButton value="calendar">Calendar</ToggleButton>
				</ToggleButtonGroup>
				{canAuthor && view === "plans" && (
					<Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing("new")}>
						New plan
					</Button>
				)}
			</Stack>
			{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

			{view === "calendar" && <LessonPlanCalendar fetchPlans={getLessonPlanCalendar} />}

			{view === "plans" && (
				<Paper sx={{ p: 3 }}>
					{plans.length === 0 && <Alert severity="info">No lesson plans yet.</Alert>}
					{plans.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Date</TableCell>
										<TableCell>Plan</TableCell>
										<TableCell>Section</TableCell>
										<TableCell>Teacher</TableCell>
										<TableCell>Status</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{plans.map((plan) => (
										<TableRow key={plan.id}>
											<TableCell>{plan.planDate}</TableCell>
											<TableCell>
												{plan.title} <Typography component="span" variant="caption" color="text.secondary">· {plan.subjectName}</Typography>
												{plan.status === "REJECTED" && plan.reviewNote && (
													<Typography variant="caption" color="error" sx={{ display: "block" }}>Rejected: {plan.reviewNote}</Typography>
												)}
											</TableCell>
											<TableCell>{plan.className} – {plan.sectionName}</TableCell>
											<TableCell>{plan.authorName}</TableCell>
											<TableCell><Chip size="small" label={plan.status} color={STATUS_COLOR[plan.status]} /></TableCell>
											<TableCell align="right">
												<Button size="small" onClick={() => setReviewing(plan)}>{scope.reviewer && plan.status === "SUBMITTED" ? "Review" : "View"}</Button>
												{(plan.status === "DRAFT" || plan.status === "REJECTED") && (
													<>
														<Button size="small" onClick={() => setEditing(plan)}>Edit</Button>
														<Button size="small" onClick={() => handleSubmitPlan(plan.id)}>Submit</Button>
													</>
												)}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Paper>
			)}

			{editing && (
				<PlanDialog
					scope={scope}
					plan={editing === "new" ? null : editing}
					onClose={() => setEditing(null)}
					onSaved={() => {
						setEditing(null);
						refresh();
					}}
				/>
			)}
			{reviewing && (
				<ReviewDialog
					plan={reviewing}
					canReview={scope.reviewer && reviewing.status === "SUBMITTED"}
					onClose={() => setReviewing(null)}
					onDone={() => {
						setReviewing(null);
						refresh();
					}}
				/>
			)}
		</Stack>
	);
}

function PlanDialog({ scope, plan, onClose, onSaved }: { scope: LearningScope; plan: LessonPlanResponse | null; onClose: () => void; onSaved: () => void }) {
	const sections = scope.sections.filter((s) => s.writableSubjectIds.length > 0);
	const [sectionId, setSectionId] = useState<number | "">(plan?.sectionId ?? sections[0]?.sectionId ?? "");
	const [subjectId, setSubjectId] = useState<number | "">(plan?.subjectId ?? "");
	const [planDate, setPlanDate] = useState(plan?.planDate ?? "");
	const [title, setTitle] = useState(plan?.title ?? "");
	const [objectives, setObjectives] = useState(plan?.objectives ?? "");
	const [activities, setActivities] = useState(plan?.activities ?? "");
	const [resources, setResources] = useState(plan?.resources ?? "");
	const [error, setError] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);
	const subjects = scope.subjects.filter((s) => sections.find((sec) => sec.sectionId === sectionId)?.writableSubjectIds.includes(s.id));

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSaving(true);
		try {
			const body = { planDate, title, objectives: objectives || null, activities: activities || null, resources: resources || null };
			if (plan) {
				await editLessonPlan(plan.id, body);
			} else {
				if (sectionId === "" || subjectId === "") {
					setError("Choose a section and subject");
					setSaving(false);
					return;
				}
				await createLessonPlan({ ...body, sectionId, subjectId });
			}
			onSaved();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save lesson plan");
			setSaving(false);
		}
	}

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={handleSubmit}>
			<DialogTitle>{plan ? "Edit lesson plan" : "New lesson plan"}</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					{!plan && (
						<>
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
						</>
					)}
					<TextField required type="date" label="Date" value={planDate} onChange={(e) => setPlanDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
					<TextField required label="Title" value={title} onChange={(e) => setTitle(e.target.value)} />
					<TextField label="Objectives" multiline minRows={2} value={objectives} onChange={(e) => setObjectives(e.target.value)} />
					<TextField label="Activities" multiline minRows={3} value={activities} onChange={(e) => setActivities(e.target.value)} />
					<TextField label="Resources" multiline minRows={2} value={resources} onChange={(e) => setResources(e.target.value)} />
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" disabled={saving}>Save draft</Button>
			</DialogActions>
		</Dialog>
	);
}

function ReviewDialog({ plan, canReview, onClose, onDone }: { plan: LessonPlanResponse; canReview: boolean; onClose: () => void; onDone: () => void }) {
	const [note, setNote] = useState("");
	const [error, setError] = useState<string | null>(null);

	async function decide(approve: boolean) {
		try {
			await (approve ? approveLessonPlan(plan.id, note) : rejectLessonPlan(plan.id, note));
			onDone();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save the review");
		}
	}

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="sm">
			<DialogTitle>{plan.title}</DialogTitle>
			<DialogContent>
				{error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
				<PlanBody plan={plan} showSection />
				{plan.reviewNote && <Alert severity={plan.status === "REJECTED" ? "error" : "success"} sx={{ mt: 2 }}>{plan.reviewedByName}: {plan.reviewNote}</Alert>}
				{canReview && <TextField fullWidth sx={{ mt: 2 }} label="Review note (required to reject)" multiline minRows={2} value={note} onChange={(e) => setNote(e.target.value)} />}
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
				{canReview && (
					<>
						<Button color="error" onClick={() => decide(false)}>Reject</Button>
						<Button variant="contained" onClick={() => decide(true)}>Approve</Button>
					</>
				)}
			</DialogActions>
		</Dialog>
	);
}
