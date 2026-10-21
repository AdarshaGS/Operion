import { useCallback, useEffect, useMemo, useRef, useState, type SyntheticEvent } from "react";
import { useSearchParams } from "react-router-dom";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { useAuth } from "../../auth/AuthContext";
import { getCycleOverview, listCycleSummaries, type CycleOverview, type CycleSummary } from "../../api/performance";
import { loadErrorMessage } from "../hr/loadError";
import { CreateCycleDialog } from "./CreateCycleDialog";
import { CyclesPanel } from "./CyclesPanel";
import { InsightsPanel } from "./InsightsPanel";
import { KpiMetricsPanel } from "./KpiMetricsPanel";
import { OverviewPanel } from "./OverviewPanel";
import { formatPeriod, pickCycle } from "./reviewFormat";
import { ScorecardPanel } from "./ScorecardPanel";

type PerformanceTab = "overview" | "scorecard" | "cycles" | "insights" | "metrics";

export function PerformancePage() {
	const { hasAnyPermission } = useAuth();
	const canManage = hasAnyPermission(["KPI_MANAGE"]);
	const canReview = hasAnyPermission(["KPI_REVIEW"]);
	const [searchParams, setSearchParams] = useSearchParams();
	const [tab, setTab] = useState<PerformanceTab>("overview");
	const [summaries, setSummaries] = useState<CycleSummary[] | null>(null);
	const [summariesError, setSummariesError] = useState<string | null>(null);
	const [overview, setOverview] = useState<CycleOverview | null>(null);
	const [overviewError, setOverviewError] = useState<string | null>(null);
	const [overviewLoading, setOverviewLoading] = useState(false);
	const [creating, setCreating] = useState(false);
	const request = useRef(0);

	const cycles = useMemo(() => (summaries ?? []).map((s) => s.cycle), [summaries]);
	const requested = Number(searchParams.get("cycle")) || null;
	const cycle = pickCycle(cycles, requested);
	const cycleId = cycle?.id ?? null;

	const loadSummaries = useCallback(() => {
		setSummariesError(null);
		return listCycleSummaries()
			.then(setSummaries)
			.catch((err) => setSummariesError(loadErrorMessage(err, "Failed to load the appraisal cycles")));
	}, []);

	useEffect(() => {
		void loadSummaries();
	}, [loadSummaries]);

	const needsOverview = tab === "overview" || tab === "insights";
	const loadOverview = useCallback((id: number) => {
		const mine = ++request.current;
		setOverviewLoading(true);
		setOverviewError(null);
		getCycleOverview(id)
			.then((data) => mine === request.current && setOverview(data))
			.catch((err) => mine === request.current && setOverviewError(loadErrorMessage(err, "Failed to load the cycle overview")))
			.finally(() => mine === request.current && setOverviewLoading(false));
	}, []);

	// One overview call serves both the Overview and Insights tabs; switching tabs reuses it.
	useEffect(() => {
		if (needsOverview && cycleId !== null && overview?.cycle.id !== cycleId) {
			loadOverview(cycleId);
		}
	}, [needsOverview, cycleId, overview?.cycle.id, loadOverview]);

	function selectCycle(id: number) {
		setSearchParams({ cycle: String(id) }, { replace: true });
	}

	function refreshAll() {
		void loadSummaries();
		if (cycleId !== null) {
			loadOverview(cycleId);
		}
	}

	const showCycleSelector = (tab === "overview" || tab === "insights") && cycles.length > 0;
	const noCycles = summaries !== null && cycles.length === 0;

	return (
		<Stack spacing={2.5}>
			<Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ justifyContent: "space-between", alignItems: { md: "flex-start" } }}>
				<Box>
					<Typography component="h1" variant="h4">Performance</Typography>
					<Typography color="text.secondary">Manage staff appraisals, review progress, and performance goals.</Typography>
				</Box>
				<Stack direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap", rowGap: 1 }}>
					{showCycleSelector && (
						<TextField select size="small" label="Appraisal cycle" value={cycleId ?? ""} onChange={(e) => selectCycle(Number(e.target.value))} sx={{ minWidth: 220 }}>
							{cycles.map((c) => (
								<MenuItem key={c.id} value={c.id}>{c.name}{c.status === "CLOSED" ? " (closed)" : ""}</MenuItem>
							))}
						</TextField>
					)}
					{canManage && tab !== "metrics" && (
						<Button variant="outlined" onClick={() => setTab("metrics")}>Configure metrics</Button>
					)}
					{canManage && (
						<Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Create appraisal cycle</Button>
					)}
				</Stack>
			</Stack>

			{showCycleSelector && cycle && (
				<Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap", rowGap: 0.5 }}>
					<Chip size="small" label={cycle.status === "OPEN" ? "Open" : "Closed"} color={cycle.status === "OPEN" ? "success" : "default"} variant="outlined" />
					<Typography variant="body2" color="text.secondary">Review period {formatPeriod(cycle.startDate, cycle.endDate)}</Typography>
				</Stack>
			)}

			<Box sx={{ borderBottom: 1, borderColor: "divider" }}>
				<Tabs value={tab} variant="scrollable" scrollButtons="auto" onChange={(_e: SyntheticEvent, value: PerformanceTab) => setTab(value)}>
					<Tab label="Overview" value="overview" />
					<Tab label="Scorecard" value="scorecard" />
					<Tab label="Appraisal cycles" value="cycles" />
					<Tab label="Insights" value="insights" />
					{canManage && <Tab label="Metrics" value="metrics" />}
				</Tabs>
			</Box>

			{needsOverview && noCycles ? (
				<NoCycles canManage={canManage} onCreate={() => setCreating(true)} />
			) : (
				<>
					{tab === "overview" && (
						<OverviewPanel overview={overview} loading={overviewLoading || summaries === null} error={overviewError ?? summariesError}
							onRetry={refreshAll} canReview={canReview} canManage={canManage} onOpenMetrics={() => setTab("metrics")} />
					)}
					{tab === "insights" && (
						<InsightsPanel overview={overview} loading={overviewLoading || summaries === null} error={overviewError ?? summariesError}
							onRetry={refreshAll} canManage={canManage} onOpenMetrics={() => setTab("metrics")} />
					)}
				</>
			)}
			{tab === "scorecard" && <ScorecardPanel cycleId={cycleId} />}
			{tab === "cycles" && (
				<CyclesPanel summaries={summaries} error={summariesError} onRetry={() => void loadSummaries()} canManage={canManage}
					onCreate={() => setCreating(true)} onOpenCycle={(id) => { selectCycle(id); setTab("overview"); }}
					onChanged={() => { setOverview(null); void loadSummaries(); }} />
			)}
			{tab === "metrics" && canManage && <KpiMetricsPanel />}

			{creating && (
				<CreateCycleDialog onClose={() => setCreating(false)}
					onCreated={(created) => {
						setCreating(false);
						setOverview(null);
						void loadSummaries().then(() => selectCycle(created.id));
					}} />
			)}
		</Stack>
	);
}

function NoCycles({ canManage, onCreate }: { canManage: boolean; onCreate: () => void }) {
	return (
		<Paper variant="outlined" sx={{ p: 5, textAlign: "center" }}>
			<Stack spacing={1.5} sx={{ alignItems: "center" }}>
				<Typography variant="h6">No appraisal cycles yet</Typography>
				<Typography color="text.secondary" sx={{ maxWidth: 480 }}>
					An appraisal cycle is a named review period. Once one exists, you can see which staff are pending review, record ratings and set goals.
				</Typography>
				{canManage ? (
					<Button variant="contained" startIcon={<AddIcon />} onClick={onCreate}>Create appraisal cycle</Button>
				) : (
					<Typography variant="body2" color="text.secondary">Ask an administrator who manages performance to create the first cycle.</Typography>
				)}
			</Stack>
		</Paper>
	);
}
