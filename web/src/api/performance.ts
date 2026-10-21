import { api } from "./client";

export type KpiUnit = "PERCENT" | "PER_30_DAYS";

export interface KpiMetricConfig {
	metric: string;
	label: string;
	description: string;
	unit: KpiUnit;
	/** OUTCOME metrics are scored against the teacher's previous period, not a target. */
	kind: "STANDARD" | "OUTCOME";
	usesDays: boolean;
	maxTarget: number;
	enabled: boolean;
	weight: number;
	target: number;
	days: number | null;
}

export interface ScorecardCell {
	metric: string;
	/** Null when the teacher has no records for this metric in the period - "no data", not 0. */
	value: number | null;
	sample: number;
	score: number | null;
	/** Previous period's value; only outcome metrics carry it. */
	previous: number | null;
}

export interface ScorecardTeacher {
	personId: number;
	name: string;
	overallScore: number | null;
	cells: ScorecardCell[];
}

export interface Scorecard {
	from: string;
	to: string;
	metrics: KpiMetricConfig[];
	teachers: ScorecardTeacher[];
}

export interface AppraisalCycle {
	id: number;
	name: string;
	startDate: string;
	endDate: string;
	status: "OPEN" | "CLOSED";
}

export type FeedbackSource = "SELF" | "MANAGER" | "PEER";
export type GoalStatus = "OPEN" | "ACHIEVED" | "PARTLY_ACHIEVED" | "MISSED";

export interface Feedback {
	id: number;
	source: FeedbackSource;
	rating: number;
	comment: string | null;
	/** Hidden (null) for peer ratings a teacher looks at. */
	reviewerName: string | null;
	/** True for the rating the viewer gave themself, so a form can open pre-filled with it. */
	mine: boolean;
	updatedAt: string | null;
}

export interface Goal {
	id: number;
	title: string;
	description: string | null;
	status: GoalStatus;
	updatedAt: string | null;
}

/**
 * Review progress for one teacher in one cycle. The cycle model has no draft/approve stages, so this is derived:
 * completed = a MANAGER rating exists; final = the cycle is closed.
 */
export type ReviewStatus = "PENDING" | "REVIEWED" | "FINALIZED" | "NOT_REVIEWED";

/** Identity and presence block of an administrator's view of one appraisal. Absent in a teacher's own view. */
export interface StaffInfo {
	name: string;
	employeeCode: string | null;
	designation: string | null;
	department: string | null;
	presencePercent: number | null;
	presenceSample: number;
	presenceTarget: number | null;
	reviewStatus: ReviewStatus;
	managerRating: number | null;
}

export interface Appraisal {
	cycle: AppraisalCycle;
	scorecard: Scorecard;
	feedback: Feedback[];
	goals: Goal[];
	staff: StaffInfo | null;
}

export interface StaffRow {
	personId: number;
	name: string;
	employeeCode: string | null;
	designation: string | null;
	department: string | null;
	presencePercent: number | null;
	overallScore: number | null;
	reviewStatus: ReviewStatus;
	managerRating: number | null;
	selfAppraisalSubmitted: boolean;
	goalCount: number;
	goalsAchieved: number;
	lastUpdated: string | null;
}

export interface OverviewSummary {
	totalStaff: number;
	pending: number;
	completed: number;
	/** Over completed reviews that have a score; null when there is none. */
	averageScore: number | null;
	scoredCount: number;
	averageManagerRating: number | null;
}

export interface DepartmentScore {
	department: string;
	staff: number;
	scored: number;
	averageScore: number | null;
}

export interface GoalStats {
	total: number;
	open: number;
	achieved: number;
	partlyAchieved: number;
	missed: number;
}

export interface PresencePoint {
	month: string;
	presencePercent: number;
	sample: number;
}

export interface Insights {
	departments: DepartmentScore[];
	/** Index 0 is rating 1; manager ratings only. */
	managerRatingCounts: number[];
	goals: GoalStats;
	presenceTrend: PresencePoint[];
}

export interface CycleOverview {
	cycle: AppraisalCycle;
	metricsConfigured: boolean;
	presenceTarget: number | null;
	summary: OverviewSummary;
	staff: StaffRow[];
	insights: Insights;
}

export interface CycleSummary {
	cycle: AppraisalCycle;
	staffCount: number;
	completed: number;
	pending: number;
}

export interface FeedbackInput {
	source?: FeedbackSource;
	rating: number;
	comment: string;
}

const BASE = "/api/v1/performance";

export function listKpiMetrics(): Promise<KpiMetricConfig[]> {
	return api.get<KpiMetricConfig[]>(`${BASE}/metrics`);
}

export function configureKpiMetric(
	metric: string,
	request: { enabled: boolean; weight: number; target: number; days: number | null },
): Promise<KpiMetricConfig> {
	return api.put<KpiMetricConfig>(`${BASE}/metrics/${metric}`, request);
}

export function getScorecard(from: string, to: string): Promise<Scorecard> {
	return api.get<Scorecard>(`${BASE}/scorecard?from=${from}&to=${to}`);
}

export function getKpiSettings(): Promise<{ teacherSelfView: boolean }> {
	return api.get<{ teacherSelfView: boolean }>(`${BASE}/settings`);
}

export function updateKpiSettings(teacherSelfView: boolean): Promise<{ teacherSelfView: boolean }> {
	return api.put<{ teacherSelfView: boolean }>(`${BASE}/settings`, { teacherSelfView });
}

export function listCycles(): Promise<AppraisalCycle[]> {
	return api.get<AppraisalCycle[]>(`${BASE}/cycles`);
}

export function createCycle(request: { name: string; startDate: string; endDate: string }): Promise<AppraisalCycle> {
	return api.post<AppraisalCycle>(`${BASE}/cycles`, request);
}

export function closeCycle(cycleId: number): Promise<AppraisalCycle> {
	return api.post<AppraisalCycle>(`${BASE}/cycles/${cycleId}/close`);
}

export function listCycleSummaries(): Promise<CycleSummary[]> {
	return api.get<CycleSummary[]>(`${BASE}/cycles/summaries`);
}

export function getCycleOverview(cycleId: number): Promise<CycleOverview> {
	return api.get<CycleOverview>(`${BASE}/cycles/${cycleId}/overview`);
}

export function getAppraisal(cycleId: number, personId: number): Promise<Appraisal> {
	return api.get<Appraisal>(`${BASE}/cycles/${cycleId}/teachers/${personId}`);
}

export function giveFeedback(cycleId: number, personId: number, request: FeedbackInput): Promise<Feedback> {
	return api.post<Feedback>(`${BASE}/cycles/${cycleId}/teachers/${personId}/feedback`, request);
}

export function addGoal(cycleId: number, personId: number, title: string, description: string): Promise<Goal> {
	return api.post<Goal>(`${BASE}/cycles/${cycleId}/teachers/${personId}/goals`, { title, description });
}

export function updateGoal(goal: Goal, patch: { title?: string; description?: string | null; status?: GoalStatus }): Promise<Goal> {
	return api.put<Goal>(`${BASE}/goals/${goal.id}`, {
		title: patch.title ?? goal.title,
		description: patch.description === undefined ? goal.description : patch.description,
		status: patch.status ?? goal.status,
	});
}

export function getMyScorecard(from: string, to: string): Promise<Scorecard> {
	return api.get<Scorecard>(`${BASE}/me/scorecard?from=${from}&to=${to}`);
}

export function listMyCycles(): Promise<AppraisalCycle[]> {
	return api.get<AppraisalCycle[]>(`${BASE}/me/cycles`);
}

export function getMyAppraisal(cycleId: number): Promise<Appraisal> {
	return api.get<Appraisal>(`${BASE}/me/cycles/${cycleId}`);
}

export function submitSelfAppraisal(cycleId: number, request: FeedbackInput): Promise<Feedback> {
	return api.post<Feedback>(`${BASE}/me/cycles/${cycleId}/self-appraisal`, request);
}

export function addMyGoal(cycleId: number, title: string, description: string): Promise<Goal> {
	return api.post<Goal>(`${BASE}/me/cycles/${cycleId}/goals`, { title, description });
}
