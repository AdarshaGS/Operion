import { useEffect, useMemo, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Step from "@mui/material/Step";
import StepButton from "@mui/material/StepButton";
import Stepper from "@mui/material/Stepper";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { applyRollover, listAcademicYears, type AcademicYearResponse } from "../../api/academicYears";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections } from "../../api/sections";
import {
	applyPromotions,
	previewPromotion,
	type PromotionOutcome,
	type PromotionPreviewClass,
	type PromotionProposal,
} from "../../api/promotions";
import { useAuth } from "../../auth/AuthContext";
import { filterByCampus, useSelectedCampusId } from "../../layout/selectedCampus";
import { colors } from "../../theme";
import { useSchoolClassLabel } from "../../utils/useSchoolClassLabel";
import {
	GRADUATE_TARGET,
	applyReadiness,
	buildClassMap,
	buildDecisions,
	classTargetValue,
	countChoices,
	exceptionBlocker,
	filterPreviewByCampus,
	flattenStudents,
	hasNoTarget,
	initialRowState,
	mergeOutcomes,
	reconcileRowStates,
	sectionOptionsFor,
	setAllChoices,
	summariseOutcomes,
	withChoice,
	type ChoiceCounts,
	type PreviewRow,
	type PromotionContext,
	type RowChoice,
	type RowStates,
	type SectionChoice,
	type SourceClassCampuses,
} from "./promotionWizard";

const STEP_LABELS = ["Years", "Class plan", "Students & apply"];

const PROPOSAL_LABEL: Record<PromotionProposal, string> = {
	PROMOTE: "Promote",
	REPEAT: "Repeat",
	GRADUATE: "Graduate",
	NEEDS_DECISION: "Needs a decision",
	ALREADY_DONE: "Already done",
};

const EMPTY_CONTEXT: PromotionContext = { repeatSections: {}, sectionNames: {} };

function errorMessage(err: unknown, fallback: string): string {
	return err instanceof ApiError ? err.message : fallback;
}

function sectionLabel(section: SectionChoice): string {
	if (section.remainingCapacity === 0) return `${section.name} (full)`;
	if (typeof section.remainingCapacity === "number") return `${section.name} (${section.remainingCapacity} left)`;
	return section.name;
}

/** Everything the preview response does not carry: where a repeater can go, and the source sections' names. */
async function loadContext(
	sourceYearId: number,
	targetYearId: number,
): Promise<{ context: PromotionContext; targetClasses: SchoolClassResponse[]; sourceClassCampuses: SourceClassCampuses; campusNames: Record<number, string> }> {
	const [sourceClasses, targetClasses] = await Promise.all([listSchoolClasses(sourceYearId), listSchoolClasses(targetYearId)]);
	const [sourceSections, targetSections] = await Promise.all([
		Promise.all(sourceClasses.map((c) => listSections(c.id))).then((lists) => lists.flat()),
		Promise.all(targetClasses.map((c) => listSections(c.id))).then((lists) => lists.flat()),
	]);
	const repeatSections: Record<number, SectionChoice[]> = {};
	for (const sourceClass of sourceClasses) {
		const sameGrade = targetClasses.filter((t) => t.gradeLevelId === sourceClass.gradeLevelId && t.campusId === sourceClass.campusId);
		repeatSections[sourceClass.id] = targetSections
			.filter((s) => s.status === "ACTIVE" && sameGrade.some((t) => t.id === s.schoolClassId))
			.map((s) => ({ id: s.id, name: s.name, remainingCapacity: undefined }));
	}
	return {
		context: { repeatSections, sectionNames: Object.fromEntries(sourceSections.map((s) => [s.id, s.name])) },
		targetClasses: targetClasses.filter((c) => c.status === "ACTIVE"),
		sourceClassCampuses: Object.fromEntries(sourceClasses.map((c) => [c.id, c.campusId])),
		campusNames: Object.fromEntries([...sourceClasses, ...targetClasses].filter((c) => c.campusName).map((c) => [c.campusId, c.campusName])),
	};
}

/**
 * Year-end promotion wizard: pick the years, review which class each class moves into, then decide per student
 * and apply. The preview (read-only) pre-fills every decision; nothing is written until Apply, and each
 * student succeeds or fails on its own so a full section or a missing target never stops the rest.
 */
export function StudentPromotionPage() {
	const classLabel = useSchoolClassLabel();
	const { hasPermission } = useAuth();
	const canGraduate = hasPermission("STUDENT_EXIT_MANAGE");
	const canCopyStructure = hasPermission("ORGANISATION_MANAGE");
	const selectedCampusId = useSelectedCampusId();

	const [activeStep, setActiveStep] = useState(0);
	const [years, setYears] = useState<AcademicYearResponse[]>([]);
	const [sourceYearId, setSourceYearId] = useState("");
	const [targetYearId, setTargetYearId] = useState("");
	const [effectiveDate, setEffectiveDate] = useState("");

	const [preview, setPreview] = useState<PromotionPreviewClass[] | null>(null);
	const [overrides, setOverrides] = useState<Record<number, number | null>>({});
	const [context, setContext] = useState<PromotionContext>(EMPTY_CONTEXT);
	const [loadedTargetClasses, setLoadedTargetClasses] = useState<SchoolClassResponse[]>([]);
	const [sourceClassCampuses, setSourceClassCampuses] = useState<SourceClassCampuses>({});
	const [campusNames, setCampusNames] = useState<Record<number, string>>({});
	const [rows, setRows] = useState<RowStates>({});
	const [outcomes, setOutcomes] = useState<Record<number, PromotionOutcome>>({});

	const [error, setError] = useState<string | null>(null);
	const [busy, setBusy] = useState<"preview" | "reclass" | "apply" | "structure" | null>(null);

	useEffect(() => {
		listAcademicYears()
			.then((loaded) => {
				setYears(loaded);
				const current = loaded.find((y) => y.current);
				if (current) {
					setSourceYearId(String(current.id));
					setEffectiveDate(current.endDate);
					const next = loaded
						.filter((y) => y.startDate > current.startDate)
						.sort((a, b) => a.startDate.localeCompare(b.startDate))[0];
					if (next) setTargetYearId(String(next.id));
				}
			})
			.catch(() => setError("Failed to load academic years"));
	}, []);

	const hasOutcomes = Object.keys(outcomes).length > 0;
	// Strictly the campus picked in the top bar: the plan, the student tables, the counts and Apply all work from
	// the visible classes only, so students of another campus are neither shown nor sent. `preview` stays complete
	// so a campus switch needs no new preview and no decision is lost.
	const visiblePreview = useMemo(
		() => (preview ? filterPreviewByCampus(preview, sourceClassCampuses, selectedCampusId) : null),
		[preview, sourceClassCampuses, selectedCampusId],
	);
	const targetClasses = useMemo(() => filterByCampus(loadedTargetClasses, selectedCampusId), [loadedTargetClasses, selectedCampusId]);
	const campusName = selectedCampusId === null ? null : (campusNames[selectedCampusId] ?? "the selected campus");
	const allRows = useMemo(() => (visiblePreview ? flattenStudents(visiblePreview) : []), [visiblePreview]);
	const readiness = useMemo(
		() => (visiblePreview ? applyReadiness(visiblePreview, rows, context, outcomes) : null),
		[visiblePreview, rows, context, outcomes],
	);
	const counts = useMemo(() => (visiblePreview ? countChoices(visiblePreview, rows, outcomes) : null), [visiblePreview, rows, outcomes]);
	const summary = useMemo(() => summariseOutcomes(Object.values(outcomes)), [outcomes]);
	const failedRows = allRows.filter((r) => outcomes[r.student.studentId] && !outcomes[r.student.studentId].succeeded);

	/** Any change to the years makes the earlier preview meaningless. */
	function resetPreview() {
		setPreview(null);
		setOverrides({});
		setRows({});
		setOutcomes({});
		setContext(EMPTY_CONTEXT);
		setLoadedTargetClasses([]);
		setSourceClassCampuses({});
		setCampusNames({});
		setError(null);
	}

	function changeSourceYear(value: string) {
		setSourceYearId(value);
		if (value === targetYearId) setTargetYearId("");
		resetPreview();
	}

	function changeTargetYear(value: string) {
		setTargetYearId(value);
		resetPreview();
	}

	function startOver() {
		resetPreview();
		setActiveStep(0);
	}

	/** The target year has no classes to promote into yet: copy the source year's classes and sections across
	 * (the same CLASSES rollover step as Settings > Academic years; re-running it adds nothing twice), then re-plan. */
	async function copyStructure() {
		setBusy("structure");
		setError(null);
		try {
			await applyRollover(Number(targetYearId), { sourceYearId: Number(sourceYearId), steps: ["CLASSES"], feeIncreasePercent: null });
		} catch (err) {
			setError(errorMessage(err, "Failed to copy the classes into the target year"));
			setBusy(null);
			return;
		}
		await runPreview();
	}

	async function runPreview() {
		setBusy("preview");
		setError(null);
		try {
			const [classes, loaded] = await Promise.all([
				previewPromotion({
					sourceAcademicYearId: Number(sourceYearId),
					targetAcademicYearId: Number(targetYearId),
					effectiveDate,
				}),
				loadContext(Number(sourceYearId), Number(targetYearId)),
			]);
			setOverrides({});
			setOutcomes({});
			setContext(loaded.context);
			setLoadedTargetClasses(loaded.targetClasses);
			setSourceClassCampuses(loaded.sourceClassCampuses);
			setCampusNames(loaded.campusNames);
			setPreview(classes);
			setRows(reconcileRowStates({}, null, classes, loaded.context, canGraduate));
			setActiveStep(1);
		} catch (err) {
			setError(errorMessage(err, "Failed to preview the promotion"));
		} finally {
			setBusy(null);
		}
	}

	/** Re-previews with every edited row's override (not just this one), so other rows keep their choice. */
	async function changeClassTarget(sourceClassId: number, value: string) {
		const nextOverrides = { ...overrides, [sourceClassId]: value === GRADUATE_TARGET ? null : Number(value) };
		setBusy("reclass");
		setError(null);
		try {
			const classes = await previewPromotion({
				sourceAcademicYearId: Number(sourceYearId),
				targetAcademicYearId: Number(targetYearId),
				effectiveDate,
				classMap: buildClassMap(nextOverrides),
			});
			setOverrides(nextOverrides);
			setRows((prev) => reconcileRowStates(prev, preview, classes, context, canGraduate));
			setPreview(classes);
		} catch (err) {
			setError(errorMessage(err, "Failed to update the class plan"));
		} finally {
			setBusy(null);
		}
	}

	function setChoice(row: PreviewRow, choice: RowChoice) {
		setRows((prev) => ({
			...prev,
			[row.student.studentId]: withChoice(prev[row.student.studentId], choice, row.student, row.cls, context),
		}));
	}

	/** Sets the decision on every row eligible for it; other rows (and their sections) are left as the admin set them. */
	function setAll(choice: RowChoice) {
		if (!visiblePreview) return;
		setRows((prev) => setAllChoices(visiblePreview, prev, choice, context, outcomes, canGraduate));
	}

	function setSection(studentId: number, sectionId: number) {
		setRows((prev) => ({ ...prev, [studentId]: { ...prev[studentId], sectionId } }));
	}

	async function submit(onlyIds?: Set<number>) {
		if (!visiblePreview) return;
		const decisions = buildDecisions(visiblePreview, rows, outcomes, onlyIds);
		if (decisions.length === 0) return;
		setBusy("apply");
		setError(null);
		try {
			const result = await applyPromotions({
				targetAcademicYearId: Number(targetYearId),
				promotionDate: effectiveDate,
				decisions,
			});
			setOutcomes((prev) => mergeOutcomes(prev, result));
		} catch (err) {
			setError(errorMessage(err, "Failed to apply promotions"));
		} finally {
			setBusy(null);
		}
	}

	const retryReady = failedRows.length > 0 && applyReadiness(visiblePreview ?? [], rows, context, outcomes).missingSection === 0;
	const working = busy !== null;

	return (
		<Stack spacing={3}>
			<Box>
				<Typography variant="h4" sx={{ color: colors.ink }}>
					Year-end promotion
				</Typography>
				<Typography variant="body1" sx={{ color: colors.inkSoft, mt: 0.5 }}>
					Preview where each class and student moves, adjust what you need, then apply. Each student is handled on their own, so one
					problem (a full section, a missing target) doesn't stop the rest. Set up next year's classes and sections first.
				</Typography>
			</Box>

			<Paper sx={{ p: { xs: 2, sm: 3 } }}>
				<Stepper
					activeStep={activeStep}
					nonLinear
					alternativeLabel
					sx={{
						"& .MuiStepIcon-root.Mui-completed": { color: colors.ok },
						"& .MuiStepConnector-root.Mui-completed .MuiStepConnector-line": { borderColor: colors.ok },
					}}
				>
					{STEP_LABELS.map((label, index) => (
						<Step
							key={label}
							completed={preview !== null && index < activeStep}
							// Steps need a preview; once students were moved the earlier steps are locked so the plan cannot drift from what was applied.
							disabled={working || (index > 0 && preview === null) || (index < 2 && hasOutcomes)}
						>
							<StepButton onClick={() => setActiveStep(index)}>{label}</StepButton>
						</Step>
					))}
				</Stepper>
			</Paper>

			{error && <Alert severity="error">{error}</Alert>}

			{activeStep === 0 && (
				<YearsStep
					years={years}
					sourceYearId={sourceYearId}
					targetYearId={targetYearId}
					effectiveDate={effectiveDate}
					busy={busy === "preview"}
					onSource={changeSourceYear}
					onTarget={changeTargetYear}
					onDate={setEffectiveDate}
					onPreview={runPreview}
				/>
			)}

			{activeStep > 0 && preview && campusName && (
				<Alert severity="info">Showing {campusName} only - switch to All campuses to include others.</Alert>
			)}

			{activeStep === 1 && visiblePreview && (
				<ClassPlanStep
					preview={visiblePreview}
					campusScoped={campusName !== null}
					targetClasses={targetClasses}
					classLabel={classLabel}
					busy={working}
					reclassifying={busy === "reclass"}
					canGraduate={canGraduate}
					canCopyStructure={canCopyStructure}
					copying={busy === "structure"}
					onCopyStructure={copyStructure}
					onChange={changeClassTarget}
					onBack={() => setActiveStep(0)}
					onContinue={() => setActiveStep(2)}
				/>
			)}

			{activeStep === 2 && preview && readiness && counts && (
				<StudentsStep
					rows={allRows}
					states={rows}
					context={context}
					outcomes={outcomes}
					canGraduate={canGraduate}
					busy={working}
					applying={busy === "apply"}
					counts={counts}
					undecided={readiness.undecided}
					missingSection={readiness.missingSection}
					toApply={readiness.toApply}
					ready={readiness.ready}
					summary={hasOutcomes ? summary : null}
					failedRows={failedRows}
					retryReady={retryReady}
					onChoice={setChoice}
					onSection={setSection}
					onSetAll={setAll}
					onBack={() => setActiveStep(1)}
					onApply={() => submit()}
					onRetry={() => submit(new Set(failedRows.map((r) => r.student.studentId)))}
					onStartOver={startOver}
				/>
			)}
		</Stack>
	);
}

interface YearsStepProps {
	years: AcademicYearResponse[];
	sourceYearId: string;
	targetYearId: string;
	effectiveDate: string;
	busy: boolean;
	onSource: (value: string) => void;
	onTarget: (value: string) => void;
	onDate: (value: string) => void;
	onPreview: () => void;
}

function YearsStep({ years, sourceYearId, targetYearId, effectiveDate, busy, onSource, onTarget, onDate, onPreview }: YearsStepProps) {
	return (
		<Paper sx={{ p: { xs: 2, sm: 3 } }}>
			<Stack spacing={2}>
				<Typography variant="h6">Choose the years</Typography>
				<Stack direction={{ xs: "column", md: "row" }} spacing={2}>
					<TextField select label="From year" value={sourceYearId} disabled={busy} onChange={(e) => onSource(e.target.value)} sx={{ minWidth: 200 }}>
						{years.map((y) => (
							<MenuItem key={y.id} value={String(y.id)}>
								{y.name}
							</MenuItem>
						))}
					</TextField>
					<TextField select label="To year" value={targetYearId} disabled={busy} onChange={(e) => onTarget(e.target.value)} sx={{ minWidth: 200 }}>
						{years
							.filter((y) => String(y.id) !== sourceYearId)
							.map((y) => (
								<MenuItem key={y.id} value={String(y.id)}>
									{y.name}
								</MenuItem>
							))}
					</TextField>
					<TextField
						label="Effective date"
						type="date"
						value={effectiveDate}
						disabled={busy}
						onChange={(e) => onDate(e.target.value)}
						slotProps={{ inputLabel: { shrink: true } }}
					/>
				</Stack>
				<Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
					<Button variant="contained" disabled={busy || !sourceYearId || !targetYearId || !effectiveDate} onClick={onPreview}>
						Preview
					</Button>
					{busy && <CircularProgress size={20} aria-label="Loading preview" />}
				</Stack>
			</Stack>
		</Paper>
	);
}

interface ClassPlanStepProps {
	preview: PromotionPreviewClass[];
	/** A campus is picked in the top bar, so an empty plan means "none in this campus". */
	campusScoped: boolean;
	targetClasses: SchoolClassResponse[];
	classLabel: (schoolClass: SchoolClassResponse) => string;
	busy: boolean;
	reclassifying: boolean;
	canGraduate: boolean;
	canCopyStructure: boolean;
	copying: boolean;
	onCopyStructure: () => void;
	onChange: (sourceClassId: number, value: string) => void;
	onBack: () => void;
	onContinue: () => void;
}

function ClassPlanStep({
	preview,
	campusScoped,
	targetClasses,
	classLabel,
	busy,
	reclassifying,
	canGraduate,
	canCopyStructure,
	copying,
	onCopyStructure,
	onChange,
	onBack,
	onContinue,
}: ClassPlanStepProps) {
	const classesWithoutTarget = preview.filter(hasNoTarget).length;
	return (
		<Paper sx={{ p: { xs: 2, sm: 3 } }}>
			<Stack spacing={2}>
				<Typography variant="h6">Class plan</Typography>
				<Typography variant="body2" color="text.secondary">
					Each class moves into one class of the target year. Change a row's target if the default is wrong; the student suggestions
					update to match.
				</Typography>
				{preview.length === 0 && (
					<Alert severity="info">
						No classes with active students were found in the source year{campusScoped ? " for this campus" : ""}.
					</Alert>
				)}
				{classesWithoutTarget > 0 && (
					<Alert
						severity="warning"
						action={
							canCopyStructure ? (
								<Button color="inherit" size="small" onClick={onCopyStructure} disabled={busy || copying}>
									{copying ? "Copying…" : "Copy classes from source year"}
								</Button>
							) : undefined
						}
					>
						{classesWithoutTarget === 1 ? "1 class has" : `${classesWithoutTarget} classes have`} no class to move into, because the target year does not have the next
						grade's classes yet.{" "}
						{canCopyStructure
							? "Copy the source year's classes and sections into the target year, and the targets and sections fill in automatically."
							: "Ask someone who can manage the organisation to prepare the target year (Settings > Academic years), then preview again."}
					</Alert>
				)}
				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Class</TableCell>
								<TableCell>Students</TableCell>
								<TableCell>Proposed action</TableCell>
								<TableCell>Target class</TableCell>
								<TableCell>Note</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{preview.map((cls) => {
								const targetLabel = targetClasses.find((c) => c.id === cls.proposedTargetSchoolClassId);
								return (
									<TableRow key={cls.sourceSchoolClassId}>
										<TableCell>{cls.label}</TableCell>
										<TableCell>{cls.students.length}</TableCell>
										<TableCell>
											{cls.proposedAction === "GRADUATE" ? "Graduate" : targetLabel ? `Promote to ${classLabel(targetLabel)}` : "Promote"}
										</TableCell>
										<TableCell>
											<TextField
												select
												size="small"
												value={classTargetValue(cls)}
												disabled={busy}
												onChange={(e) => onChange(cls.sourceSchoolClassId, e.target.value)}
												sx={{ minWidth: 240 }}
												slotProps={{ htmlInput: { "aria-label": `Target class for ${cls.label}` } }}
											>
												{targetClasses.map((c) => (
													<MenuItem key={c.id} value={String(c.id)}>
														{classLabel(c)}
													</MenuItem>
												))}
												<MenuItem value={GRADUATE_TARGET} disabled={!canGraduate}>
													Graduate
												</MenuItem>
											</TextField>
										</TableCell>
										<TableCell>
											{hasNoTarget(cls) && <Chip size="small" color="warning" label="No target class" sx={{ mr: 1 }} />}
											{cls.reason && (
												<Typography variant="body2" color="text.secondary" component="span">
													{cls.reason}
												</Typography>
											)}
										</TableCell>
									</TableRow>
								);
							})}
						</TableBody>
					</Table>
				</TableContainer>
				<Stack direction="row" spacing={1.5} sx={{ justifyContent: "space-between", alignItems: "center" }}>
					<Button onClick={onBack} disabled={busy} sx={{ color: colors.inkSoft }}>
						Back
					</Button>
					<Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
						{reclassifying && <CircularProgress size={20} aria-label="Updating the plan" />}
						<Button variant="contained" onClick={onContinue} disabled={busy || preview.length === 0}>
							Continue to students
						</Button>
					</Stack>
				</Stack>
			</Stack>
		</Paper>
	);
}

interface StudentsStepProps {
	rows: PreviewRow[];
	states: RowStates;
	context: PromotionContext;
	outcomes: Record<number, PromotionOutcome>;
	canGraduate: boolean;
	busy: boolean;
	applying: boolean;
	counts: ChoiceCounts;
	undecided: number;
	missingSection: number;
	toApply: number;
	ready: boolean;
	summary: { succeeded: number; skipped: number; failed: number } | null;
	failedRows: PreviewRow[];
	retryReady: boolean;
	onChoice: (row: PreviewRow, choice: RowChoice) => void;
	onSection: (studentId: number, sectionId: number) => void;
	onSetAll: (choice: RowChoice) => void;
	onBack: () => void;
	onApply: () => void;
	onRetry: () => void;
	onStartOver: () => void;
}

function StudentsStep(props: StudentsStepProps) {
	const { rows, summary, failedRows, outcomes, busy, applying } = props;
	const [bulkChoice, setBulkChoice] = useState<RowChoice>("PROMOTE");
	// Exceptions first: those are the rows the school's records could not settle.
	const exceptions = rows.filter((r) => r.student.proposedAction === "NEEDS_DECISION");
	const others = rows.filter((r) => r.student.proposedAction !== "NEEDS_DECISION");

	return (
		<Stack spacing={2}>
			{summary && (
				<Paper sx={{ p: { xs: 2, sm: 3 } }}>
					<Stack spacing={1.5}>
						<Typography variant="h6">Results</Typography>
						<Alert severity={summary.failed > 0 ? "warning" : "success"}>
							{summary.succeeded} succeeded, {summary.skipped} skipped (already applied), {summary.failed} failed.
						</Alert>
						{failedRows.length > 0 && (
							<Stack component="ul" spacing={0.5} sx={{ m: 0, pl: 3 }}>
								{failedRows.map((r) => (
									<Typography component="li" variant="body2" color="error" key={r.student.studentId}>
										{r.student.name} ({r.student.admissionNumber}): {outcomes[r.student.studentId].error ?? "Failed"}
									</Typography>
								))}
							</Stack>
						)}
						<Stack direction="row" spacing={1.5}>
							{failedRows.length > 0 && (
								<Button variant="contained" disabled={busy || !props.retryReady} onClick={props.onRetry}>
									Retry failed
								</Button>
							)}
							<Button onClick={props.onStartOver} disabled={busy}>
								Start over
							</Button>
						</Stack>
						{failedRows.length > 0 && !props.retryReady && (
							<Typography variant="body2" color="text.secondary">
								Choose a target section for every failed student who is promoted or repeating, then retry.
							</Typography>
						)}
					</Stack>
				</Paper>
			)}

			{!summary && rows.length > 0 && (
				<Paper sx={{ p: { xs: 2, sm: 3 } }}>
					<Stack spacing={1.5}>
						<Typography variant="h6">Decide in bulk</Typography>
						<Typography variant="body2" color="text.secondary">
							Everyone starts on Promote (or Graduate in a class with no next grade). Set a decision for all students at once, then change
							individual students in the tables below.
						</Typography>
						<Stack direction={{ xs: "column", md: "row" }} spacing={1.5} sx={{ alignItems: { md: "center" } }}>
							<TextField
								select
								size="small"
								label="Set all to"
								value={bulkChoice}
								disabled={busy}
								onChange={(e) => setBulkChoice(e.target.value as RowChoice)}
								sx={{ minWidth: 160 }}
								slotProps={{ htmlInput: { "aria-label": "Set all to" } }}
							>
								<MenuItem value="PROMOTE">Promote</MenuItem>
								<MenuItem value="REPEAT">Repeat</MenuItem>
								<MenuItem value="GRADUATE" disabled={!props.canGraduate}>
									Graduate
								</MenuItem>
								<MenuItem value="SKIP">Skip</MenuItem>
							</TextField>
							<Button variant="outlined" disabled={busy} onClick={() => props.onSetAll(bulkChoice)}>
								Set all
							</Button>
							<ChoiceSummary counts={props.counts} />
						</Stack>
						{bulkChoice === "PROMOTE" && (
							<Typography variant="caption" color="text.secondary">
								Students whose class has no target class are not changed.
							</Typography>
						)}
					</Stack>
				</Paper>
			)}

			<Paper sx={{ p: { xs: 2, sm: 3 } }}>
				<Stack spacing={2}>
					<Typography variant="h6">Needs a decision ({exceptions.length})</Typography>
					<Typography variant="body2" color="text.secondary">
						No published passing result, or the class has no target. These start on Promote where the class has somewhere to go; change
						the ones who should repeat, graduate or be skipped. Students who cannot be promoted need a decision.
					</Typography>
					{exceptions.length === 0 ? (
						<Typography variant="body2" color="text.secondary">
							No students need a decision.
						</Typography>
					) : (
						<StudentTable {...props} items={exceptions} caption="Students who need a decision" />
					)}
				</Stack>
			</Paper>

			<Paper sx={{ p: { xs: 2, sm: 3 } }}>
				<Stack spacing={2}>
					<Typography variant="h6">Other students ({others.length})</Typography>
					{others.length === 0 ? (
						<Typography variant="body2" color="text.secondary">
							No other students.
						</Typography>
					) : (
						<StudentTable {...props} items={others} caption="Other students" />
					)}
				</Stack>
			</Paper>

			{!summary && (
				<Stack direction="row" spacing={1.5} sx={{ justifyContent: "space-between", alignItems: "center" }}>
					<Button onClick={props.onBack} disabled={busy} sx={{ color: colors.inkSoft }}>
						Back
					</Button>
					<Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
						{applying && <CircularProgress size={20} aria-label="Applying promotions" />}
						<ApplyHint undecided={props.undecided} missingSection={props.missingSection} toApply={props.toApply} />
						<Button variant="contained" disabled={busy || !props.ready} onClick={props.onApply}>
							Apply
						</Button>
					</Stack>
				</Stack>
			)}
		</Stack>
	);
}

function ChoiceSummary({ counts }: { counts: ChoiceCounts }) {
	const parts = [
		`Promote ${counts.promote}`,
		`Repeat ${counts.repeat}`,
		`Graduate ${counts.graduate}`,
		`Skip ${counts.skip}`,
		`Undecided ${counts.undecided}`,
	];
	if (counts.alreadyDone > 0) parts.push(`Already done ${counts.alreadyDone}`);
	return (
		<Typography variant="body2" color={counts.undecided > 0 ? "error" : "text.secondary"} aria-live="polite" data-testid="choice-summary">
			{parts.join(" - ")}
		</Typography>
	);
}

function ApplyHint({ undecided, missingSection, toApply }: { undecided: number; missingSection: number; toApply: number }) {
	let text: string | null = null;
	if (undecided > 0) text = `${undecided} student${undecided === 1 ? " needs" : "s need"} a decision.`;
	else if (missingSection > 0) text = `${missingSection} promoted or repeating student${missingSection === 1 ? " needs" : "s need"} a target section.`;
	else if (toApply === 0) text = "Nothing to apply.";
	else text = `${toApply} student${toApply === 1 ? "" : "s"} will be updated.`;
	return (
		<Typography variant="body2" color="text.secondary">
			{text}
		</Typography>
	);
}

interface StudentTableProps extends StudentsStepProps {
	items: PreviewRow[];
	caption: string;
}

function StudentTable({ items, caption, states, context, outcomes, canGraduate, busy, onChoice, onSection }: StudentTableProps) {
	return (
		<TableContainer>
			<Table size="small" aria-label={caption}>
				<TableHead>
					<TableRow>
						<TableCell>Student</TableCell>
						<TableCell>Class</TableCell>
						<TableCell>Suggestion</TableCell>
						<TableCell>Decision</TableCell>
						<TableCell>Target section</TableCell>
						<TableCell>Result</TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{items.map((row) => {
						const { student, cls } = row;
						const state = states[student.studentId] ?? initialRowState(student, cls, context, canGraduate);
						const outcome = outcomes[student.studentId];
						const done = outcome?.succeeded === true;
						const alreadyDone = student.proposedAction === "ALREADY_DONE";
						const locked = busy || done || alreadyDone;
						const options = sectionOptionsFor(state.choice, cls, context);
						const selected = options.find((o) => o.id === state.sectionId);
						const needsSection = state.choice === "PROMOTE" || state.choice === "REPEAT";
						return (
							<TableRow key={student.studentId}>
								<TableCell>
									{student.name}
									<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
										{student.admissionNumber}
									</Typography>
								</TableCell>
								<TableCell>{cls.label}</TableCell>
								<TableCell>
									<Typography variant="body2">{PROPOSAL_LABEL[student.proposedAction]}</Typography>
									{student.reason && (
										<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
											{student.reason}
										</Typography>
									)}
								</TableCell>
								<TableCell>
									<TextField
										select
										size="small"
										value={state.choice ?? ""}
										disabled={locked}
										error={state.choice === null}
										onChange={(e) => onChoice(row, e.target.value as RowChoice)}
										sx={{ minWidth: 130 }}
										slotProps={{ htmlInput: { "aria-label": `Decision for ${student.name}` } }}
									>
										<MenuItem value="PROMOTE">Promote</MenuItem>
										<MenuItem value="REPEAT">Repeat</MenuItem>
										<MenuItem value="GRADUATE" disabled={!canGraduate}>
											Graduate
										</MenuItem>
										<MenuItem value="SKIP">Skip</MenuItem>
									</TextField>
									{state.choice === null && !alreadyDone && (
										<Typography variant="caption" color="error" sx={{ display: "block" }}>
											{exceptionBlocker(cls, canGraduate) ?? "Choose a decision"}
										</Typography>
									)}
								</TableCell>
								<TableCell>
									{state.choice === "GRADUATE" || state.choice === "SKIP" || alreadyDone ? (
										<Typography variant="body2" color="text.secondary">
											{alreadyDone ? "Already in the target year" : state.choice === "GRADUATE" ? "Leaves the school" : "Stays where they are"}
										</Typography>
									) : (
										<Stack spacing={0.5}>
											<TextField
												select
												size="small"
												value={state.sectionId == null ? "" : String(state.sectionId)}
												disabled={locked}
												error={needsSection && state.sectionId == null}
												onChange={(e) => onSection(student.studentId, Number(e.target.value))}
												sx={{ minWidth: 200 }}
												slotProps={{ htmlInput: { "aria-label": `Target section for ${student.name}` } }}
											>
												{options.map((o) => (
													<MenuItem key={o.id} value={String(o.id)}>
														{sectionLabel(o)}
													</MenuItem>
												))}
											</TextField>
											{selected?.remainingCapacity === 0 && <Chip size="small" color="warning" label="Section full" sx={{ alignSelf: "flex-start" }} />}
											{needsSection && options.length === 0 && (
												<Typography variant="caption" color="error">
													No section available
												</Typography>
											)}
										</Stack>
									)}
								</TableCell>
								<TableCell>
									{outcome &&
										(outcome.succeeded ? (
											<Typography variant="body2" color="success.main">
												{outcome.skipped ? "Already applied" : "Done"}
											</Typography>
										) : (
											<Typography variant="body2" color="error">
												Failed
											</Typography>
										))}
								</TableCell>
							</TableRow>
						);
					})}
				</TableBody>
			</Table>
		</TableContainer>
	);
}
