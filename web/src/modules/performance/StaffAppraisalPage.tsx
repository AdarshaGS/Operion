import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useParams, useSearchParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import MenuItem from "@mui/material/MenuItem";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useAuth } from "../../auth/AuthContext";
import { addGoal, getAppraisal, giveFeedback, listCycles, updateGoal, type Appraisal, type AppraisalCycle } from "../../api/performance";
import { loadErrorMessage } from "../hr/loadError";
import { CloseCycleDialog } from "./CloseCycleDialog";
import { EvaluationSection } from "./EvaluationSection";
import { GoalsSection } from "./GoalsSection";
import { ReviewStatusChip } from "./ReviewStatusChip";
import { pickCycle } from "./reviewFormat";
import { PresenceSection, StaffInformationSection, WorkflowSection } from "./StaffSideSections";

const RATING_FORM_ID = "appraisal-rating-form";

/** One staff member's appraisal for one cycle - /performance/staff/:personId?cycle=:id. */
export function StaffAppraisalPage() {
	const personId = Number(useParams().personId);
	const [searchParams, setSearchParams] = useSearchParams();
	const { hasAnyPermission } = useAuth();
	const canReview = hasAnyPermission(["KPI_REVIEW"]);
	const canManage = hasAnyPermission(["KPI_MANAGE"]);
	const [cycles, setCycles] = useState<AppraisalCycle[] | null>(null);
	const [appraisal, setAppraisal] = useState<Appraisal | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [closing, setClosing] = useState(false);

	const requested = Number(searchParams.get("cycle")) || null;
	const cycle = cycles ? pickCycle(cycles, requested) : null;
	const cycleId = cycle?.id ?? null;

	const loadCycles = useCallback(() => {
		listCycles().then(setCycles).catch((err) => setError(loadErrorMessage(err, "Failed to load the appraisal cycles")));
	}, []);
	useEffect(loadCycles, [loadCycles]);

	const load = useCallback(() => {
		if (cycleId === null || !Number.isFinite(personId)) {
			return Promise.resolve();
		}
		setError(null);
		return getAppraisal(cycleId, personId).then(setAppraisal).catch((err) => setError(loadErrorMessage(err, "Failed to load the appraisal")));
	}, [cycleId, personId]);

	useEffect(() => {
		setAppraisal(null);
		void load();
	}, [load]);

	const reload = (action: Promise<unknown>) => action.then(load);
	const backTo = `/performance${cycleId === null ? "" : `?cycle=${cycleId}`}`;
	const staff = appraisal?.staff ?? null;
	const firstName = staff?.name.split(" ")[0] ?? "this person";

	function focusRatingForm() {
		const form = document.getElementById(RATING_FORM_ID);
		form?.scrollIntoView({ behavior: "smooth", block: "center" });
		form?.querySelector<HTMLElement>("input,textarea")?.focus({ preventScroll: true });
	}

	return (
		<Stack spacing={2.5}>
			<Box>
				<Button component={RouterLink} to={backTo} startIcon={<ArrowBackIcon />} size="small" sx={{ mb: 1, ml: -1 }}>Back to Performance</Button>
				<Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ justifyContent: "space-between", alignItems: { md: "flex-start" } }}>
					<Box>
						<Stack direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap", rowGap: 0.5 }}>
							<Typography component="h1" variant="h4" sx={{ overflowWrap: "anywhere", fontSize: { xs: "1.6rem", sm: "2.125rem" } }}>{staff?.name ?? (appraisal || error ? "Appraisal" : <Skeleton width={220} />)}</Typography>
							{staff && <ReviewStatusChip status={staff.reviewStatus} />}
						</Stack>
						{staff && (
							<Typography color="text.secondary">
								{[staff.designation, staff.department, staff.employeeCode].filter(Boolean).join(" · ") || "No staff profile details recorded"}
							</Typography>
						)}
					</Box>
					{cycles && cycles.length > 0 && (
						<TextField select size="small" label="Appraisal cycle" value={cycleId ?? ""} sx={{ minWidth: 220 }}
							onChange={(e) => setSearchParams({ cycle: e.target.value }, { replace: true })}>
							{cycles.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}{c.status === "CLOSED" ? " (closed)" : ""}</MenuItem>)}
						</TextField>
					)}
				</Stack>
			</Box>

			{error && <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => void load()}>Retry</Button>}>{error}</Alert>}
			{cycles && cycles.length === 0 && (
				<Alert severity="info">There are no appraisal cycles yet, so there is no appraisal to show. Create a cycle from the Performance page first.</Alert>
			)}
			{!appraisal && !error && cycleId !== null && (
				<Box sx={{ display: "grid", gap: 2.5, gridTemplateColumns: { xs: "1fr", lg: "minmax(0, 2fr) minmax(0, 1fr)" } }} aria-busy="true">
					<Skeleton variant="rounded" height={420} />
					<Skeleton variant="rounded" height={420} />
				</Box>
			)}

			{appraisal && cycleId !== null && staff && (
				<Box sx={{ display: "grid", gap: 2.5, alignItems: "start", gridTemplateColumns: { xs: "minmax(0, 1fr)", lg: "minmax(0, 2fr) minmax(0, 1fr)" } }}>
					<Stack spacing={2.5} sx={{ minWidth: 0 }}>
						<EvaluationSection appraisal={appraisal} canRate={canReview} formId={RATING_FORM_ID}
							onRate={(input) => reload(giveFeedback(cycleId, personId, input))} />
						<GoalsSection goals={appraisal.goals} staffFirstName={firstName} cycleOpen={appraisal.cycle.status === "OPEN"} canManage={canReview}
							onAdd={(title, description) => reload(addGoal(cycleId, personId, title, description))}
							onUpdate={(goal, patch) => reload(updateGoal(goal, patch))} />
					</Stack>
					<Stack spacing={2.5} sx={{ minWidth: 0 }}>
						<StaffInformationSection staff={staff} appraisal={appraisal} />
						<PresenceSection staff={staff} appraisal={appraisal} />
						<WorkflowSection staff={staff} appraisal={appraisal} canReview={canReview} canManage={canManage}
							onRateNow={focusRatingForm} onCloseCycle={() => setClosing(true)} />
					</Stack>
				</Box>
			)}

			{closing && appraisal && (
				<CloseCycleDialog cycle={appraisal.cycle} pending={null} onClose={() => setClosing(false)}
					onClosed={() => { setClosing(false); loadCycles(); void load(); }} />
			)}
		</Stack>
	);
}
