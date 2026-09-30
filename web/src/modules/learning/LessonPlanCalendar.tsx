import { useEffect, useMemo, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { ApiError } from "../../api/client";
import type { LessonPlanResponse } from "../../api/learning";

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

function iso(date: Date): string {
	const month = String(date.getMonth() + 1).padStart(2, "0");
	const day = String(date.getDate()).padStart(2, "0");
	return `${date.getFullYear()}-${month}-${day}`;
}

/** Month grid of approved lesson plans (#229). The caller supplies the feed - teachers/admins use the
 * learning API, guardians the per-child one - so the same calendar serves all three. */
export function LessonPlanCalendar({ fetchPlans, showSection = true }: { fetchPlans: (from: string, to: string) => Promise<LessonPlanResponse[]>; showSection?: boolean }) {
	const [month, setMonth] = useState(() => {
		const now = new Date();
		return new Date(now.getFullYear(), now.getMonth(), 1);
	});
	const [plans, setPlans] = useState<LessonPlanResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [open, setOpen] = useState<LessonPlanResponse | null>(null);

	useEffect(() => {
		const from = iso(new Date(month.getFullYear(), month.getMonth(), 1));
		const to = iso(new Date(month.getFullYear(), month.getMonth() + 1, 0));
		fetchPlans(from, to)
			.then(setPlans)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the calendar"));
	}, [month, fetchPlans]);

	const cells = useMemo(() => {
		const first = new Date(month.getFullYear(), month.getMonth(), 1);
		const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
		const leading = (first.getDay() + 6) % 7; // Monday-first grid
		const result: (Date | null)[] = Array(leading).fill(null);
		for (let d = 1; d <= daysInMonth; d++) {
			result.push(new Date(month.getFullYear(), month.getMonth(), d));
		}
		while (result.length % 7 !== 0) {
			result.push(null);
		}
		return result;
	}, [month]);

	const byDate = useMemo(() => {
		const map = new Map<string, LessonPlanResponse[]>();
		for (const plan of plans) {
			map.set(plan.planDate, [...(map.get(plan.planDate) ?? []), plan]);
		}
		return map;
	}, [plans]);

	return (
		<Paper sx={{ p: 2 }}>
			<Stack spacing={1.5}>
				<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
					<Button size="small" startIcon={<ChevronLeftIcon />} onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}>
						Prev
					</Button>
					<Typography variant="subtitle1">{month.toLocaleString(undefined, { month: "long", year: "numeric" })}</Typography>
					<Button size="small" endIcon={<ChevronRightIcon />} onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}>
						Next
					</Button>
				</Stack>
				{error && <Alert severity="error">{error}</Alert>}
				<Box sx={{ display: "grid", gridTemplateColumns: "repeat(7, minmax(0, 1fr))", gap: 0.5 }}>
					{WEEKDAYS.map((d) => (
						<Typography key={d} variant="caption" color="text.secondary" sx={{ textAlign: "center" }}>
							{d}
						</Typography>
					))}
					{cells.map((date, i) => {
						const dayPlans = date ? (byDate.get(iso(date)) ?? []) : [];
						return (
							<Box key={i} sx={{ minHeight: 72, border: 1, borderColor: "divider", borderRadius: 0.5, p: 0.5, opacity: date ? 1 : 0.3 }}>
								{date && <Typography variant="caption" color="text.secondary">{date.getDate()}</Typography>}
								{dayPlans.map((plan) => (
									<Button key={plan.id} size="small" onClick={() => setOpen(plan)} sx={{ display: "block", textAlign: "left", textTransform: "none", p: 0, minWidth: 0, lineHeight: 1.2, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", width: "100%" }}>
										{plan.subjectName}: {plan.title}
									</Button>
								))}
							</Box>
						);
					})}
				</Box>
			</Stack>
			{open && (
				<Dialog open onClose={() => setOpen(null)} fullWidth maxWidth="sm">
					<DialogTitle>{open.title}</DialogTitle>
					<DialogContent>
						<PlanBody plan={open} showSection={showSection} />
					</DialogContent>
					<DialogActions>
						<Button onClick={() => setOpen(null)}>Close</Button>
					</DialogActions>
				</Dialog>
			)}
		</Paper>
	);
}

export function PlanBody({ plan, showSection }: { plan: LessonPlanResponse; showSection: boolean }) {
	return (
		<Stack spacing={1.5} sx={{ mt: 1 }}>
			<Typography variant="body2" color="text.secondary">
				{plan.planDate} · {plan.subjectName}
				{showSection ? ` · ${plan.className} – ${plan.sectionName}` : ""} · {plan.authorName}
			</Typography>
			{plan.objectives && (
				<div>
					<Typography variant="subtitle2">Objectives</Typography>
					<Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>{plan.objectives}</Typography>
				</div>
			)}
			{plan.activities && (
				<div>
					<Typography variant="subtitle2">Activities</Typography>
					<Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>{plan.activities}</Typography>
				</div>
			)}
			{plan.resources && (
				<div>
					<Typography variant="subtitle2">Resources</Typography>
					<Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>{plan.resources}</Typography>
				</div>
			)}
		</Stack>
	);
}
