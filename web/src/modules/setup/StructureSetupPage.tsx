import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
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
import { listAcademicYears } from "../../api/academicYears";
import { listCampuses } from "../../api/campuses";
import { getOrganisationProfile } from "../../api/organisationProfile";
import { getOrganisationSettings } from "../../api/organisationSettings";
import { colors } from "../../theme";
import { AcademicYearsPanel } from "../settings/AcademicYearsPanel";
import { BusinessSettingsPanel } from "../settings/BusinessSettingsPanel";
import { CampusesPanel } from "../settings/CampusesPanel";
import { OrganisationProfilePanel } from "../settings/OrganisationProfilePanel";

type StepKey = "profile" | "business" | "academicYear" | "campuses";

interface Step {
	key: StepKey;
	label: string;
	optional: boolean;
	/** onSaved advances the wizard - only form-style panels (one Save button) wire it up;
	 * list panels (years, campuses) add rows in place and move on via Continue. */
	render: (onSaved: () => void) => React.ReactNode;
}

/** Minimal onboarding - just enough to establish the school. Fees, departments and
 * designations are deliberately not here: they're part of the school setup template (Academic setup),
 * and the Dashboard's setup card points to what comes next. */
const STEPS: Step[] = [
	{ key: "profile", label: "Organisation profile", optional: false, render: (onSaved) => <OrganisationProfilePanel onSaved={onSaved} /> },
	{ key: "business", label: "Business settings", optional: false, render: (onSaved) => <BusinessSettingsPanel onSaved={onSaved} /> },
	{
		key: "academicYear",
		label: "Academic year",
		optional: false,
		render: () => (
			<Stack spacing={2}>
				<Alert severity="info">Add your academic year, then use "Mark current" on it to continue.</Alert>
				<AcademicYearsPanel />
			</Stack>
		),
	},
	// Optional: a "Main Campus" is created at provisioning; add more only if the school has them.
	{ key: "campuses", label: "Campuses / locations", optional: true, render: () => <CampusesPanel /> },
];

interface ReviewStatus {
	profileComplete: boolean;
	businessConfigured: boolean;
	currentAcademicYear: boolean;
	activeCampusCount: number;
}

/** What "done" means for each required step, and what to tell the user when it isn't.
 * Mirrors DashboardController.isStructureConfigured on the backend. */
const REQUIREMENTS: Partial<Record<StepKey, { label: string; done: (status: ReviewStatus) => boolean; message: string }>> = {
	profile: {
		label: "Organisation profile (primary contact name and email)",
		done: (s) => s.profileComplete,
		message: "Add the primary contact name and email, then save.",
	},
	business: { label: "Business settings (timezone)", done: (s) => s.businessConfigured, message: "Choose a timezone, then save." },
	academicYear: {
		label: "A current academic year",
		done: (s) => s.currentAcademicYear,
		message: 'Add an academic year and mark it as current to continue.',
	},
};

function requiredDone(status: ReviewStatus): boolean {
	return Object.values(REQUIREMENTS).every((requirement) => requirement.done(status));
}

async function loadStatus(): Promise<ReviewStatus> {
	const [profile, settings, years, campuses] = await Promise.all([
		getOrganisationProfile(),
		getOrganisationSettings(),
		listAcademicYears(),
		listCampuses(),
	]);
	return {
		profileComplete: Boolean(profile.primaryContactName?.trim() && profile.primaryContactEmail?.trim()),
		businessConfigured: Boolean(settings.timezone),
		currentAcademicYear: years.some((year) => year.current && year.status !== "CLOSED"),
		activeCampusCount: campuses.filter((campus) => campus.status === "ACTIVE").length,
	};
}

function StatusRow({ done, label }: { done: boolean; label: string }) {
	return (
		<Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
			{done ? (
				<CheckCircleIcon fontSize="small" sx={{ color: colors.ok }} />
			) : (
				<RadioButtonUncheckedIcon fontSize="small" sx={{ color: colors.inkFaint }} />
			)}
			<Typography variant="body2" sx={{ color: colors.ink }}>
				{label}
			</Typography>
		</Stack>
	);
}

function ReviewStep({ status }: { status: ReviewStatus | null }) {
	const navigate = useNavigate();
	const ready = status !== null && requiredDone(status);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Review and finish</Typography>
				{status === null ? (
					<Typography variant="body2" sx={{ color: colors.inkSoft }}>
						Checking your setup...
					</Typography>
				) : (
					<>
						<Stack spacing={1}>
							{Object.entries(REQUIREMENTS).map(([key, requirement]) => (
								<StatusRow key={key} done={requirement.done(status)} label={requirement.label} />
							))}
						</Stack>
						<Typography variant="body2" sx={{ color: colors.inkSoft }}>
							Optional: {status.activeCampusCount} active campus / location{status.activeCampusCount === 1 ? "" : "s"}. Fee
							structures, departments, designations and subjects come with the school setup template in Academic setup.
						</Typography>
						{!ready && <Alert severity="warning">Complete the required items above to finish setup.</Alert>}
					</>
				)}
				<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
					<Button variant="contained" disabled={!ready} onClick={() => navigate("/dashboard")}>
						Finish setup
					</Button>
				</Box>
			</Stack>
		</Paper>
	);
}

/** Guided Structure Setup workflow - the Dashboard's "Go to structure" CTA lands here
 * instead of the general Settings page, since Structure spans several unrelated-looking
 * Settings sections. Renders the exact same panel components Settings uses for each
 * section, so data is stored once and both entry points stay in sync (product spec:
 * "Structure Setup workflow"). */
export function StructureSetupPage() {
	const navigate = useNavigate();
	const [activeStep, setActiveStep] = useState(0);
	const [reviewStatus, setReviewStatus] = useState<ReviewStatus | null>(null);
	const [completed, setCompleted] = useState<Set<StepKey>>(new Set());
	const [stepError, setStepError] = useState<string | null>(null);
	const [checking, setChecking] = useState(false);

	const isReviewStep = activeStep === STEPS.length;
	// Linear flow: a step is reachable only once every step before it is complete.
	const firstIncomplete = STEPS.findIndex((step) => !completed.has(step.key));
	const reachable = firstIncomplete === -1 ? STEPS.length : firstIncomplete;

	function goTo(index: number) {
		setStepError(null);
		setActiveStep(index);
	}

	async function advance() {
		const step = STEPS[activeStep];
		const requirement = REQUIREMENTS[step.key];
		if (requirement) {
			setChecking(true);
			try {
				const status = await loadStatus();
				if (!requirement.done(status)) {
					setStepError(requirement.message);
					return;
				}
			} catch {
				setStepError("Couldn't verify this step - try again.");
				return;
			} finally {
				setChecking(false);
			}
		}
		setCompleted((prev) => new Set(prev).add(step.key));
		goTo(activeStep + 1);
	}

	// Resume a returning user: only the unbroken run of already-done required steps from the
	// start counts as complete (so a defaulted timezone never ticks a step ahead of an
	// unfinished one), and the wizard opens on the first step still to do.
	useEffect(() => {
		loadStatus()
			.then((status) => {
				const done = new Set<StepKey>();
				for (const step of STEPS) {
					const requirement = REQUIREMENTS[step.key];
					if (!requirement || !requirement.done(status)) break;
					done.add(step.key);
				}
				setCompleted(done);
				setActiveStep(done.size);
			})
			.catch(() => undefined);
	}, []);

	useEffect(() => {
		if (!isReviewStep) return;
		loadStatus()
			.then(setReviewStatus)
			.catch(() => setReviewStatus(null));
	}, [isReviewStep]);

	return (
		<Stack spacing={3}>
			<Box>
				<Typography variant="h4" sx={{ color: colors.ink }}>
					Structure setup
				</Typography>
				<Typography variant="body1" sx={{ color: colors.inkSoft, mt: 0.5 }}>
					Establish your school: profile, business settings and the current academic year. Everything else can be configured later.
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
					{STEPS.map((step, index) => (
						<Step key={step.key} completed={completed.has(step.key) && index !== activeStep} disabled={index > reachable}>
							<StepButton onClick={() => goTo(index)}>
								{step.label}
								{step.optional && (
									<Typography variant="caption" sx={{ display: "block", color: colors.inkFaint }}>
										Optional
									</Typography>
								)}
							</StepButton>
						</Step>
					))}
					<Step disabled={reachable < STEPS.length}>
						<StepButton onClick={() => goTo(STEPS.length)}>Review and finish</StepButton>
					</Step>
				</Stepper>
			</Paper>

			{stepError && <Alert severity="warning">{stepError}</Alert>}

			{isReviewStep ? <ReviewStep status={reviewStatus} /> : STEPS[activeStep].render(advance)}

			<Stack direction="row" spacing={1.5} sx={{ justifyContent: "space-between" }}>
				<Button onClick={() => navigate("/dashboard")} sx={{ color: colors.inkSoft }}>
					Exit to dashboard
				</Button>
				<Stack direction="row" spacing={1.5}>
					<Button disabled={activeStep === 0} onClick={() => goTo(activeStep - 1)}>
						Back
					</Button>
					{!isReviewStep && (
						<Button variant="contained" onClick={advance} disabled={checking}>
							Continue
						</Button>
					)}
				</Stack>
			</Stack>
		</Stack>
	);
}
