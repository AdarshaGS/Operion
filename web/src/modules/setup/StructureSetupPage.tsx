import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
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
import { listCampuses } from "../../api/campuses";
import { getOrganisationProfile } from "../../api/organisationProfile";
import { colors } from "../../theme";
import { BusinessSettingsPanel } from "../settings/BusinessSettingsPanel";
import { CampusesPanel } from "../settings/CampusesPanel";
import { DepartmentsPanel } from "../settings/DepartmentsPanel";
import { DesignationsPanel } from "../settings/DesignationsPanel";
import { OrganisationProfilePanel } from "../settings/OrganisationProfilePanel";

interface Step {
	label: string;
	optional: boolean;
	render: () => React.ReactNode;
}

const STEPS: Step[] = [
	{ label: "Organisation profile", optional: false, render: () => <OrganisationProfilePanel /> },
	{ label: "Business settings", optional: false, render: () => <BusinessSettingsPanel /> },
	{ label: "Campuses / locations", optional: false, render: () => <CampusesPanel /> },
	{ label: "Departments", optional: true, render: () => <DepartmentsPanel /> },
	{ label: "Designations", optional: true, render: () => <DesignationsPanel /> },
];

interface ReviewStatus {
	profileComplete: boolean;
	campusExists: boolean;
}

function ReviewStep({ status }: { status: ReviewStatus | null }) {
	const navigate = useNavigate();
	const rows = status
		? [
				{ label: "Organisation profile", done: status.profileComplete },
				{ label: "Business settings", done: true },
				{ label: "At least one campus / location", done: status.campusExists },
			]
		: [];

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Review and finish</Typography>
				<Typography variant="body2" sx={{ color: colors.inkSoft }}>
					Departments and designations are optional and won't block completion.
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

/** Guided Structure Setup workflow - the Dashboard's "Go to structure" CTA lands here
 * instead of the general Settings page, since Structure spans several unrelated-looking
 * Settings sections. Renders the exact same panel components Settings uses for each
 * section, so data is stored once and both entry points stay in sync (product spec:
 * "Structure Setup workflow"). */
export function StructureSetupPage() {
	const navigate = useNavigate();
	const [activeStep, setActiveStep] = useState(0);
	const [reviewStatus, setReviewStatus] = useState<ReviewStatus | null>(null);

	const isReviewStep = activeStep === STEPS.length;

	useEffect(() => {
		if (!isReviewStep) return;
		Promise.all([getOrganisationProfile(), listCampuses()])
			.then(([profile, campuses]) => {
				setReviewStatus({
					profileComplete: Boolean(profile.primaryContactName && profile.primaryContactEmail),
					campusExists: campuses.some((campus) => campus.status === "ACTIVE"),
				});
			})
			.catch(() => setReviewStatus(null));
	}, [isReviewStep]);

	return (
		<Stack spacing={3}>
			<Box>
				<Typography variant="h4" sx={{ color: colors.ink }}>
					Structure setup
				</Typography>
				<Typography variant="body1" sx={{ color: colors.inkSoft, mt: 0.5 }}>
					Set up your organisation profile, business settings, and locations - one guided flow.
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
