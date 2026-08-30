import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Step from "@mui/material/Step";
import StepButton from "@mui/material/StepButton";
import Stepper from "@mui/material/Stepper";
import Typography from "@mui/material/Typography";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import RadioButtonUncheckedIcon from "@mui/icons-material/RadioButtonUnchecked";
import { getAcademicSetupStatus } from "../../api/academicSetupStatus";
import { listAcademicYears } from "../../api/academicYears";
import { listGradeLevels } from "../../api/gradeLevels";
import { colors } from "../../theme";
import { AcademicYearsPanel } from "../settings/AcademicYearsPanel";
import { GradeLevelsPanel } from "./GradeLevelsPanel";
import { SchoolClassesPanel } from "./SchoolClassesPanel";
import { SubjectsPanel } from "./SubjectsPanel";

interface WizardStep {
	label: string;
	optional: boolean;
	render: () => React.ReactNode;
}

const STEPS: WizardStep[] = [
	{ label: "Academic year", optional: false, render: () => <AcademicYearsPanel /> },
	{ label: "Grade levels", optional: false, render: () => <GradeLevelsPanel /> },
	{ label: "Subjects", optional: true, render: () => <SubjectsPanel /> },
	{ label: "Classes / sections", optional: false, render: () => <SchoolClassesPanel /> },
];

interface ReviewStatus {
	academicYearCurrent: boolean;
	gradeLevelExists: boolean;
	classesAndSectionsReady: boolean;
}

function ReviewStep({ status }: { status: ReviewStatus | null }) {
	const navigate = useNavigate();
	const rows = status
		? [
				{ label: "Academic year marked current", done: status.academicYearCurrent },
				{ label: "At least one grade level", done: status.gradeLevelExists },
				{ label: "At least one class with a section", done: status.classesAndSectionsReady },
			]
		: [];

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Review and finish</Typography>
				<Typography variant="body2" sx={{ color: colors.inkSoft }}>
					Subjects are optional and won't block completion - a class needs a section before students can be enrolled into it.
				</Typography>
				<Stack spacing={1}>
					{rows.map((row) => (
						<Stack key={row.label} direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
							{row.done ? (
								<CheckCircleIcon fontSize="small" sx={{ color: colors.ok }} />
							) : (
								<RadioButtonUncheckedIcon fontSize="small" sx={{ color: colors.inkFaint }} />
							)}
							<Typography variant="body2" sx={{ color: colors.ink }}>
								{row.label}
							</Typography>
						</Stack>
					))}
				</Stack>
				<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
					<Button variant="contained" onClick={() => navigate("/dashboard")}>
						Finish setup
					</Button>
				</Box>
			</Stack>
		</Paper>
	);
}

/** Guided first-run flow for #201's "Academic setup" onboarding step - distinct from
 * AcademicsPage, which is the day-to-day grade/subject/class management screen an
 * already-running school uses (#202). Renders the exact same panel components that
 * screen and Settings use for each section, so data is stored once and every entry
 * point stays in sync, same pattern as StructureSetupPage. */
export function AcademicSetupPage() {
	const navigate = useNavigate();
	// Set when a screen (e.g. student admission) redirects here because academic setup
	// isn't done yet - see StudentCreatePage's prerequisite check.
	const blockedMessage = (useLocation().state as { blockedMessage?: string } | null)?.blockedMessage;
	const [activeStep, setActiveStep] = useState(0);
	const [reviewStatus, setReviewStatus] = useState<ReviewStatus | null>(null);

	const isReviewStep = activeStep === STEPS.length;

	useEffect(() => {
		if (!isReviewStep) return;
		Promise.all([listAcademicYears(), listGradeLevels(), getAcademicSetupStatus()])
			.then(([years, gradeLevels, setupStatus]) => {
				setReviewStatus({
					academicYearCurrent: years.some((year) => year.current),
					gradeLevelExists: gradeLevels.length > 0,
					classesAndSectionsReady: setupStatus.configured,
				});
			})
			.catch(() => setReviewStatus(null));
	}, [isReviewStep]);

	return (
		<Stack spacing={3}>
			{blockedMessage && <Alert severity="warning">{blockedMessage}</Alert>}
			<Box>
				<Typography variant="h4" sx={{ color: colors.ink }}>
					Academic setup
				</Typography>
				<Typography variant="body1" sx={{ color: colors.inkSoft, mt: 0.5 }}>
					Set up your academic year, grade levels, subjects, and classes - one guided flow, in the right order.
				</Typography>
			</Box>

			<Paper sx={{ p: { xs: 2, sm: 3 } }}>
				<Stepper activeStep={activeStep} nonLinear alternativeLabel>
					{STEPS.map((step, index) => (
						<Step key={step.label}>
							<StepButton onClick={() => setActiveStep(index)}>
								{step.label}
								{step.optional && (
									<Typography variant="caption" sx={{ display: "block", color: colors.inkFaint }}>
										Optional
									</Typography>
								)}
							</StepButton>
						</Step>
					))}
					<Step>
						<StepButton onClick={() => setActiveStep(STEPS.length)}>Review and finish</StepButton>
					</Step>
				</Stepper>
			</Paper>

			{isReviewStep ? <ReviewStep status={reviewStatus} /> : STEPS[activeStep].render()}

			<Stack direction="row" spacing={1.5} sx={{ justifyContent: "space-between" }}>
				<Button onClick={() => navigate("/dashboard")} sx={{ color: colors.inkSoft }}>
					Exit to dashboard
				</Button>
				<Stack direction="row" spacing={1.5}>
					<Button disabled={activeStep === 0} onClick={() => setActiveStep((step) => step - 1)}>
						Back
					</Button>
					{!isReviewStep && (
						<Button variant="contained" onClick={() => setActiveStep((step) => step + 1)}>
							{STEPS[activeStep].optional ? "Save and continue" : "Continue"}
						</Button>
					)}
				</Stack>
			</Stack>
		</Stack>
	);
}
