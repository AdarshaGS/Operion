import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import LinearProgress from "@mui/material/LinearProgress";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import BarChartIcon from "@mui/icons-material/BarChart";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import FlagOutlinedIcon from "@mui/icons-material/FlagOutlined";
import FactCheckOutlinedIcon from "@mui/icons-material/FactCheckOutlined";
import StarBorderIcon from "@mui/icons-material/StarBorder";
import { Bar, BarChart, CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis } from "recharts";
import type { CycleOverview } from "../../api/performance";
import { EmptyNote, SectionCard } from "../../components/DashboardKit";
import { colors } from "../../theme";
import { formatPercent } from "./reviewFormat";

const CHART_HEIGHT = 240;
const AXIS_TICK = { fill: colors.inkSoft, fontSize: 12 };

export interface InsightsPanelProps {
	overview: CycleOverview | null;
	loading: boolean;
	error: string | null;
	onRetry: () => void;
	canManage: boolean;
	onOpenMetrics: () => void;
}

/**
 * Cycle-level roll-ups. Every figure comes from the same cycle as the dashboard. Departments are compared on one
 * scale only because the organisation has a single set of metrics, weights and targets; attendance is shown on its
 * own, not blended into the score.
 */
export function InsightsPanel({ overview, loading, error, onRetry, canManage, onOpenMetrics }: InsightsPanelProps) {
	if (error) {
		return <Alert severity="error" action={<Button color="inherit" size="small" onClick={onRetry}>Retry</Button>}>{error}</Alert>;
	}
	if (loading || !overview) {
		return <Skeleton variant="rounded" height={360} aria-label="Loading insights" />;
	}

	const { summary, insights } = overview;
	const completion = summary.totalStaff === 0 ? null : (summary.completed / summary.totalStaff) * 100;
	const departmentData = insights.departments
		.filter((d) => d.averageScore !== null)
		.map((d) => ({ name: d.department, score: Number(d.averageScore!.toFixed(1)), scored: d.scored, staff: d.staff }));
	const ratingData = insights.managerRatingCounts.map((count, i) => ({ rating: String(i + 1), count }));
	const ratingsGiven = insights.managerRatingCounts.reduce((a, b) => a + b, 0);
	const goals = insights.goals;
	const goalRate = goals.total === 0 ? null : (goals.achieved / goals.total) * 100;
	const trend = insights.presenceTrend.map((p) => ({ month: p.month, presence: Number(p.presencePercent.toFixed(1)), days: p.sample }));

	return (
		<Stack spacing={2.5}>
			{!overview.metricsConfigured && (
				<Alert severity="info" action={canManage ? <Button color="inherit" size="small" onClick={onOpenMetrics}>Choose metrics</Button> : undefined}>
					No metrics are being tracked, so there are no scores to summarise. Completion, ratings, goals and attendance are shown below.
				</Alert>
			)}
			<Box sx={{ display: "grid", gap: 2.5, gridTemplateColumns: { xs: "minmax(0, 1fr)", lg: "repeat(2, minmax(0, 1fr))" } }}>
				<SectionCard title="Review completion" icon={<FactCheckOutlinedIcon fontSize="small" />} tint="green">
					{completion === null ? (
						<EmptyNote>No staff are in this cycle yet.</EmptyNote>
					) : (
						<Stack spacing={1.5}>
							<Typography sx={{ fontSize: "2rem", fontWeight: 800, color: colors.ink }}>{Math.round(completion)}%</Typography>
							<LinearProgress variant="determinate" value={completion} sx={{ height: 8, borderRadius: 4 }} aria-label="Review completion" />
							<Typography variant="body2" color="text.secondary">
								{summary.completed} of {summary.totalStaff} reviews have a manager rating; {summary.pending} {summary.pending === 1 ? "is" : "are"} pending.
							</Typography>
						</Stack>
					)}
				</SectionCard>

				<SectionCard title="Manager rating distribution" icon={<StarBorderIcon fontSize="small" />} tint="violet">
					{ratingsGiven === 0 ? (
						<EmptyNote>No manager ratings have been recorded in this cycle yet.</EmptyNote>
					) : (
						<div role="img" aria-label={`Manager ratings: ${ratingData.map((d) => `${d.count} rated ${d.rating}`).join(", ")}`}>
							<ResponsiveContainer width="100%" height={CHART_HEIGHT}>
								<BarChart data={ratingData} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
									<CartesianGrid vertical={false} stroke={colors.rule} />
									<XAxis dataKey="rating" tick={AXIS_TICK} tickLine={false} axisLine={false} />
									<YAxis allowDecimals={false} tick={AXIS_TICK} tickLine={false} axisLine={false} />
									<RechartsTooltip cursor={{ fill: colors.accentSoft }} formatter={(value) => [`${value} staff`, "Rated"]} labelFormatter={(label) => `Rating ${label} of 5`} />
									<Bar dataKey="count" fill={colors.accent} radius={[4, 4, 0, 0]} maxBarSize={44} />
								</BarChart>
							</ResponsiveContainer>
							<Typography variant="caption" color="text.secondary">Manager ratings only; self and peer ratings are not mixed in. Rating 1 is lowest, 5 highest.</Typography>
						</div>
					)}
				</SectionCard>

				<SectionCard title="Average score by department" icon={<BarChartIcon fontSize="small" />} tint="blue">
					{departmentData.length === 0 ? (
						<EmptyNote>No department has a scored member yet.</EmptyNote>
					) : (
						<div role="img" aria-label={`Average score by department: ${departmentData.map((d) => `${d.name} ${d.score}%`).join(", ")}`}>
							<ResponsiveContainer width="100%" height={Math.max(CHART_HEIGHT, departmentData.length * 44 + 40)}>
								<BarChart data={departmentData} layout="vertical" margin={{ top: 4, right: 16, bottom: 0, left: 8 }}>
									<CartesianGrid horizontal={false} stroke={colors.rule} />
									<XAxis type="number" domain={[0, 100]} unit="%" tick={AXIS_TICK} tickLine={false} axisLine={false} />
									<YAxis type="category" dataKey="name" width={130} tick={AXIS_TICK} tickLine={false} axisLine={false} />
									<RechartsTooltip cursor={{ fill: colors.accentSoft }}
										formatter={(value, _n, item) => [`${value}% (${item.payload.scored} of ${item.payload.staff} staff scored)`, "Average"]} />
									<Bar dataKey="score" fill={colors.accent} radius={[0, 4, 4, 0]} maxBarSize={26} />
								</BarChart>
							</ResponsiveContainer>
							<Typography variant="caption" color="text.secondary">
								All departments are scored on the same metrics, weights and targets. Staff without a score are left out of the average, not counted as zero.
							</Typography>
						</div>
					)}
				</SectionCard>

				<SectionCard title="Goal completion" icon={<FlagOutlinedIcon fontSize="small" />} tint="orange">
					{goals.total === 0 ? (
						<EmptyNote>No goals have been set in this cycle yet. Add goals from a staff member's appraisal.</EmptyNote>
					) : (
						<Stack spacing={1.5}>
							<Typography sx={{ fontSize: "2rem", fontWeight: 800, color: colors.ink }}>{goalRate === null ? "—" : `${Math.round(goalRate)}%`}</Typography>
							<Typography variant="body2" color="text.secondary">{goals.achieved} of {goals.total} goals achieved.</Typography>
							<Box sx={{ display: "flex", height: 10, borderRadius: 5, overflow: "hidden", gap: "2px" }} role="img"
								aria-label={`${goals.achieved} achieved, ${goals.partlyAchieved} partly achieved, ${goals.missed} missed, ${goals.open} open`}>
								{[
									{ n: goals.achieved, color: colors.ok },
									{ n: goals.partlyAchieved, color: colors.info },
									{ n: goals.missed, color: colors.bad },
									{ n: goals.open, color: colors.rule },
								].filter((s) => s.n > 0).map((s, i) => <Box key={i} sx={{ flexGrow: s.n, backgroundColor: s.color }} />)}
							</Box>
							<Stack direction="row" sx={{ flexWrap: "wrap", columnGap: 2, rowGap: 0.5 }}>
								<Legend color={colors.ok} label="Achieved" n={goals.achieved} />
								<Legend color={colors.info} label="Partly achieved" n={goals.partlyAchieved} />
								<Legend color={colors.bad} label="Missed" n={goals.missed} />
								<Legend color={colors.rule} label="Open" n={goals.open} />
							</Stack>
						</Stack>
					)}
				</SectionCard>
			</Box>

			<SectionCard title="Attendance trend (separate from the score)" icon={<EventAvailableIcon fontSize="small" />} tint="pink">
				{trend.length === 0 ? (
					<EmptyNote>No staff attendance has been recorded within this cycle's dates.</EmptyNote>
				) : (
					<div role="img" aria-label={`Monthly staff presence: ${trend.map((d) => `${d.month} ${d.presence}%`).join(", ")}`}>
						<ResponsiveContainer width="100%" height={CHART_HEIGHT}>
							<LineChart data={trend} margin={{ top: 8, right: 16, bottom: 0, left: -8 }}>
								<CartesianGrid vertical={false} stroke={colors.rule} />
								<XAxis dataKey="month" tick={AXIS_TICK} tickLine={false} axisLine={false} />
								<YAxis domain={[0, 100]} unit="%" tick={AXIS_TICK} tickLine={false} axisLine={false} />
								<RechartsTooltip formatter={(value, _n, item) => [`${value}% (${item.payload.days} recorded days)`, "Presence"]} />
								{overview.presenceTarget !== null && (
									<ReferenceLine y={overview.presenceTarget} stroke={colors.inkSoft} strokeDasharray="4 4"
										label={{ value: `Target ${formatPercent(overview.presenceTarget)}`, fill: colors.inkSoft, fontSize: 12, position: "insideBottomRight" }} />
								)}
								<Line type="monotone" dataKey="presence" stroke={colors.accent} strokeWidth={2} dot={{ r: 4, fill: colors.accent }} activeDot={{ r: 6 }} />
							</LineChart>
						</ResponsiveContainer>
						<Typography variant="caption" color="text.secondary">
							Share of recorded days attended by this cycle's staff, per month. Approved leave counts as neither present nor absent. Attendance affects the overall score only if the Presence metric is switched on.
						</Typography>
					</div>
				)}
			</SectionCard>
		</Stack>
	);
}

function Legend({ color, label, n }: { color: string; label: string; n: number }) {
	return (
		<Stack direction="row" spacing={0.75} sx={{ alignItems: "center" }}>
			<Box aria-hidden sx={{ width: 10, height: 10, borderRadius: "3px", backgroundColor: color, flexShrink: 0 }} />
			<Typography variant="caption" color="text.secondary">{label} · {n}</Typography>
		</Stack>
	);
}
