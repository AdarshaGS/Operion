import { useState } from "react";
import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import ButtonBase from "@mui/material/ButtonBase";
import IconButton from "@mui/material/IconButton";
import LinearProgress from "@mui/material/LinearProgress";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import CheckIcon from "@mui/icons-material/Check";
import CloseIcon from "@mui/icons-material/Close";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import type { SetupChecklist } from "../../api/dashboard";
import { useAuth } from "../../auth/AuthContext";
import { colors } from "../../theme";
import { FROM_DASHBOARD_STATE } from "../../utils/useBackTarget";

type GroupKey = "foundation" | "academic" | "operations";

/** The playbook's three phases: label plus the theme tokens its numbers and caption use. */
const GROUPS: Record<GroupKey, { label: string; color: string; soft: string }> = {
	foundation: { label: "Foundation", color: colors.lifecycleFoundation, soft: colors.lifecycleFoundationSoft },
	academic: { label: "Academic & students", color: colors.lifecycleAcademic, soft: colors.lifecycleAcademicSoft },
	operations: { label: "Daily operations", color: colors.lifecycleOperations, soft: colors.lifecycleOperationsSoft },
};

interface Step {
	key: keyof SetupChecklist;
	label: string;
	description: string;
	path: string;
	ctaLabel: string;
	group: GroupKey;
	/** Happens later in the academic year, so it is listed but neither counted nor required to finish setup. */
	later?: boolean;
}

/** The school-lifecycle steps, in order. This array is the single place the list, labels, copy,
 * routes, call-to-actions and phase (colour) of each step are defined. The number shown beside a
 * step is not stored here: it is the step's position among the steps actually shown, so a school
 * with a module switched off sees 1..N with no gaps. Guidance, not a gate - every linked screen is
 * reachable normally regardless of progress state. Fee structure sits in the academic phase, before
 * the student steps, so students know their fees in advance; fee collection is ongoing daily work, not
 * first-run setup, so it is not a step. Attendance and fees reuse the older attendanceStarted /
 * feesConfigured flags and team members reuses membersAdded; the rest have flags of their own (see
 * SetupChecklist.java). Every step opens its screen with router state marking the Dashboard as the origin,
 * so that screen's Back control returns here (see useBackTarget). */
const ALL_STEPS: Step[] = [
	{ key: "institutionProfileConfigured", label: "Institution setup", description: "School profile and settings in one place", path: "/setup/structure", ctaLabel: "Set up school", group: "foundation" },
	{ key: "academicYearSet", label: "Academic year creation", description: "A clear container for the year", path: "/settings/academic-years", ctaLabel: "Create year", group: "foundation" },
	{ key: "membersAdded", label: "Team members & roles", description: "Teachers and staff who run attendance and marks", path: "/members", ctaLabel: "Invite members", group: "foundation" },
	{ key: "classesAndSectionsCreated", label: "Class & section creation", description: "The structure everything follows", path: "/academics/setup", ctaLabel: "Create classes", group: "academic" },
	{ key: "subjectsMapped", label: "Subject configuration", description: "Subjects mapped to classes", path: "/academics", ctaLabel: "Map subjects", group: "academic" },
	{ key: "feesConfigured", label: "Fee structure configuration", description: "Fees defined once, applied consistently", path: "/fees/setup", ctaLabel: "Set up fees", group: "academic" },
	{ key: "admissionsCompleted", label: "Student admissions", description: "Enquiries become admitted students", path: "/admissions", ctaLabel: "Open admissions", group: "academic" },
	{ key: "studentRecordsExist", label: "Student record management", description: "One record: profile, guardians, documents", path: "/students", ctaLabel: "Add students", group: "academic" },
	{ key: "attendanceStarted", label: "Attendance management", description: "Daily registers, locked and correctable", path: "/attendance", ctaLabel: "Mark attendance", group: "operations" },
	{ key: "examCreated", label: "Examination creation", description: "Exams scheduled subject by subject", path: "/examinations", ctaLabel: "Create exam", group: "operations" },
	{ key: "marksApproved", label: "Marks entry", description: "Entered once, approved before results", path: "/examinations", ctaLabel: "Enter marks", group: "operations" },
	{ key: "libraryStocked", label: "Library administration", description: "Books, issues, returns and fines tracked", path: "/library", ctaLabel: "Add books", group: "operations" },
	{ key: "academicYearClosed", label: "Academic year closure", description: "Close the year, move students forward", path: "/settings/academic-years", ctaLabel: "Close year", group: "foundation", later: true },
];

/** localStorage key for the viewer's expand/collapse choice - a per-browser convenience, not a server preference. */
const EXPANDED_STORAGE_KEY = "operion.dashboard.setupGuideExpanded";

function readExpanded(): boolean {
	try {
		return localStorage.getItem(EXPANDED_STORAGE_KEY) !== "0";
	} catch {
		return true;
	}
}

function writeExpanded(expanded: boolean) {
	try {
		localStorage.setItem(EXPANDED_STORAGE_KEY, expanded ? "1" : "0");
	} catch {
		// per-viewer convenience only - the choice just won't survive a reload
	}
}

interface SetupProgressProps {
	checklist: SetupChecklist;
	onDismiss: () => void;
}

/** The steps that can still apply: one for a module the platform owner switched off can
 * never be completed, so it is dropped rather than leaving the guide stuck on it. */
function applicableSteps(isPathEnabled: (path: string) => boolean): Step[] {
	return ALL_STEPS.filter((step) => isPathEnabled(step.path));
}

/** True once every applicable onboarding step reports done. The guide stays visible then (as a review of the steps). Steps
 * marked `later` (year closure) can't finish during onboarding, so they never hold the guide open. */
export function isSetupComplete(checklist: SetupChecklist, isPathEnabled: (path: string) => boolean): boolean {
	const steps = applicableSteps(isPathEnabled).filter((step) => !step.later);
	return steps.length === 0 || steps.every((step) => checklist[step.key]);
}

/** Post-login "next setup step" card (#97) - a numbered vertical stepper over the
 * SetupChecklist signal, following the school lifecycle in three coloured phases.
 * Stays on the dashboard after every onboarding step is done, so the steps can still be reviewed;
 * only the user's own dismissal (permanent, per-user, for anyone who already knows the system) hides it. It can be collapsed to a one-line
 * summary with its progress bar (the choice is remembered in this browser). Finished steps shrink
 * to a single line so the list stays short; the call-to-action sits on the current step. */
export function SetupProgress({ checklist, onDismiss }: SetupProgressProps) {
	const navigate = useNavigate();
	const { isPathEnabled } = useAuth();
	const [expanded, setExpanded] = useState(readExpanded);

	function toggleExpanded() {
		const next = !expanded;
		setExpanded(next);
		writeExpanded(next);
	}

	const steps = applicableSteps(isPathEnabled);
	const onboardingSteps = steps.filter((step) => !step.later);
	const doneCount = onboardingSteps.filter((step) => checklist[step.key]).length;
	if (onboardingSteps.length === 0) {
		return null;
	}
	// Once everything is done the card stays (steps all ticked, no call-to-action) so the steps can still be reviewed.
	const allDone = doneCount === onboardingSteps.length;
	const currentStep = onboardingSteps.find((step) => !checklist[step.key]);

	return (
		<Paper sx={{ p: 3, height: "100%" }}>
			<Stack spacing={2}>
				<Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between", gap: 1 }}>
					<ButtonBase
						onClick={toggleExpanded}
						aria-expanded={expanded}
						disableRipple
						sx={{ gap: 0.5, borderRadius: 1, textAlign: "left", justifyContent: "flex-start", flexGrow: 1, py: 0.25, "&:hover": { bgcolor: colors.paperSunken } }}
					>
						<ExpandMoreIcon
							fontSize="small"
							sx={{ color: colors.inkSoft, transform: expanded ? "none" : "rotate(-90deg)", transition: "transform 150ms" }}
						/>
						<Typography variant="subtitle1">
							{expanded
								? allDone
									? "Setup complete"
									: "Next setup step"
								: allDone
									? `Setup guide - all ${onboardingSteps.length} steps complete`
									: `Setup guide - ${doneCount} of ${onboardingSteps.length} complete`}
						</Typography>
					</ButtonBase>
					<Tooltip title="Hide this - you can always reach setup from Settings">
						<IconButton size="small" onClick={onDismiss} aria-label="Dismiss setup guide">
							<CloseIcon fontSize="small" />
						</IconButton>
					</Tooltip>
				</Stack>
				<Box>
					{expanded && (
						<Typography variant="body2" sx={{ color: colors.inkSoft, mb: 0.75 }}>
							<Box component="span" sx={{ color: colors.accent, fontWeight: 700 }}>
								{doneCount}
							</Box>{" "}
							of {onboardingSteps.length} setup tasks complete
						</Typography>
					)}
					<LinearProgress
						variant="determinate"
						value={(doneCount / onboardingSteps.length) * 100}
						sx={{
							height: 6,
							borderRadius: 3,
							backgroundColor: colors.paperSunken,
							"& .MuiLinearProgress-bar": { backgroundColor: colors.accent, borderRadius: 3 },
						}}
					/>
				</Box>
				{expanded && (
					<Stack spacing={2}>
						<Stack spacing={0}>
							{steps.map((step, index) => {
								const group = GROUPS[step.group];
								const done = checklist[step.key];
								const isCurrent = step === currentStep;
								const isLast = index === steps.length - 1;
								const startsGroup = index === 0 || steps[index - 1].group !== step.group;
								return (
									<Box
										key={step.key}
										onClick={() => navigate(step.path, { state: FROM_DASHBOARD_STATE })}
										sx={{
											display: "flex",
											alignItems: "flex-start",
											gap: 1.5,
											py: done ? 0.5 : 1,
											cursor: "pointer",
											borderRadius: 1,
											px: 1,
											mx: -1,
											backgroundColor: isCurrent ? group.soft : "transparent",
											"&:hover": { bgcolor: isCurrent ? group.soft : colors.paperSunken },
										}}
									>
										<Stack sx={{ alignItems: "center", alignSelf: "stretch" }}>
											<Box
												sx={{
													width: 26,
													height: 26,
													borderRadius: "50%",
													display: "flex",
													alignItems: "center",
													justifyContent: "center",
													flexShrink: 0,
													fontSize: "0.75rem",
													fontWeight: 700,
													color: done ? colors.accentInk : isCurrent ? group.color : colors.inkFaint,
													backgroundColor: done ? group.color : "transparent",
													border: done ? "none" : `2px solid ${isCurrent ? group.color : colors.rule}`,
												}}
											>
												{done ? <CheckIcon sx={{ fontSize: 15 }} /> : index + 1}
											</Box>
											{!isLast && <Box sx={{ width: 2, flexGrow: 1, minHeight: done ? 6 : 14, backgroundColor: colors.rule, my: 0.25 }} />}
										</Stack>
										<Box sx={{ flexGrow: 1, pt: 0.25, pb: isLast ? 0 : done ? 0.25 : 1 }}>
											{startsGroup && (
												<Typography
													variant="caption"
													sx={{ display: "block", color: group.color, fontWeight: 700, letterSpacing: "0.08em", textTransform: "uppercase", fontSize: "0.65rem", lineHeight: 1.4 }}
												>
													{group.label}
												</Typography>
											)}
											<Typography variant="body2" sx={{ color: done ? colors.inkSoft : colors.ink, fontWeight: 700 }}>
												{step.label}
											</Typography>
											{!done && (
												<Typography variant="caption" sx={{ color: colors.inkSoft, display: "block" }}>
													{step.description}
												</Typography>
											)}
											{!done && step.later && (
												<Typography variant="caption" sx={{ color: colors.inkFaint, display: "block", fontStyle: "italic" }}>
													Later in the year - not needed to finish setup
												</Typography>
											)}
										</Box>
										{isCurrent && (
											<Button
												variant="contained"
												size="small"
												onClick={(event) => {
													event.stopPropagation();
													navigate(step.path, { state: FROM_DASHBOARD_STATE });
												}}
												sx={{ flexShrink: 0, mt: 0.25 }}
											>
												{step.ctaLabel}
											</Button>
										)}
									</Box>
								);
							})}
						</Stack>
						<Typography variant="caption" sx={{ color: colors.inkFaint }}>
							Steps 1&ndash;{steps.length} follow one academic year, from setup to closure.
						</Typography>
					</Stack>
				)}
			</Stack>
		</Paper>
	);
}
