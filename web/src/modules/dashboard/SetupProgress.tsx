import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import LinearProgress from "@mui/material/LinearProgress";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import CheckIcon from "@mui/icons-material/Check";
import CloseIcon from "@mui/icons-material/Close";
import type { SetupChecklist } from "../../api/dashboard";
import { useAuth } from "../../auth/AuthContext";
import { colors } from "../../theme";

interface Step {
	key: keyof SetupChecklist;
	label: string;
	description: string;
	path: string;
	ctaLabel: string;
}

/** Order: Structure -> Academic setup -> Members -> Students. Roles and Fees are left out on
 * purpose - both are reachable from their own screens and aren't first-run blockers. Guidance,
 * not a gate - every linked screen is reachable normally regardless of progress state. */
const ALL_STEPS: Step[] = [
	{ key: "structureConfigured", label: "Structure", description: "School profile, business settings and current academic year", path: "/setup/structure", ctaLabel: "Go to structure" },
	{
		key: "academicSetupConfigured",
		label: "Academic setup",
		description: "Add classes and subjects for the current year",
		path: "/academics/setup",
		ctaLabel: "Set up classes",
	},
	{ key: "membersAdded", label: "Members", description: "Team members added", path: "/members", ctaLabel: "Invite members" },
	{ key: "studentsAdded", label: "Students", description: "Add your students", path: "/students", ctaLabel: "Add students" },
];

interface SetupProgressProps {
	checklist: SetupChecklist;
	onDismiss: () => void;
}

/** The steps that can still apply: one for a module the platform owner switched off can
 * never be completed, so it is dropped rather than leaving the guide stuck on it. */
function applicableSteps(isPathEnabled: (path: string) => boolean): Step[] {
	return ALL_STEPS.filter((step) => isPathEnabled(step.path));
}

/** True once every applicable step reports done - the dashboard uses this to retire the
 * quick-actions shortcuts along with the setup guide itself. */
export function isSetupComplete(checklist: SetupChecklist, isPathEnabled: (path: string) => boolean): boolean {
	const steps = applicableSteps(isPathEnabled);
	return steps.length === 0 || steps.every((step) => checklist[step.key]);
}

/** Post-login "next setup step" card (#97) - a numbered vertical stepper over the
 * SetupChecklist signal, shown until every step reports done or the user dismisses it
 * early (permanent per-user dismissal, for anyone who already knows the system). */
export function SetupProgress({ checklist, onDismiss }: SetupProgressProps) {
	const navigate = useNavigate();
	const { isPathEnabled } = useAuth();

	const steps = applicableSteps(isPathEnabled);
	const doneCount = steps.filter((step) => checklist[step.key]).length;
	if (steps.length === 0 || doneCount === steps.length) {
		return null;
	}
	const currentIndex = steps.findIndex((step) => !checklist[step.key]);

	return (
		<Paper sx={{ p: 3, height: "100%" }}>
			<Stack spacing={2}>
				<Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between" }}>
					<Typography variant="subtitle1">Next setup step</Typography>
					<Tooltip title="Hide this - you can always reach setup from Settings">
						<IconButton size="small" onClick={onDismiss} aria-label="Dismiss setup guide">
							<CloseIcon fontSize="small" />
						</IconButton>
					</Tooltip>
				</Stack>
				<Box>
					<Typography variant="body2" sx={{ color: colors.inkSoft, mb: 0.75 }}>
						<Box component="span" sx={{ color: colors.accent, fontWeight: 700 }}>
							{doneCount}
						</Box>{" "}
						of {steps.length} setup tasks complete
					</Typography>
					<LinearProgress
						variant="determinate"
						value={(doneCount / steps.length) * 100}
						sx={{
							height: 6,
							borderRadius: 3,
							backgroundColor: colors.paperSunken,
							"& .MuiLinearProgress-bar": { backgroundColor: colors.accent, borderRadius: 3 },
						}}
					/>
				</Box>
				<Stack spacing={0}>
					{steps.map((step, index) => {
						const done = checklist[step.key];
						const isCurrent = index === currentIndex;
						const isLast = index === steps.length - 1;
						return (
							<Box
								key={step.key}
								onClick={() => navigate(step.path)}
								sx={{
									display: "flex",
									alignItems: "flex-start",
									gap: 1.5,
									py: 1,
									cursor: "pointer",
									borderRadius: 1,
									px: 1,
									mx: -1,
									"&:hover": { bgcolor: colors.paperSunken },
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
											color: done ? "#fff" : isCurrent ? colors.accent : colors.inkFaint,
											backgroundColor: done ? colors.ok : isCurrent ? colors.accentSoft : "transparent",
											border: done ? "none" : `2px solid ${isCurrent ? colors.accent : colors.rule}`,
										}}
									>
										{done ? <CheckIcon sx={{ fontSize: 15 }} /> : index + 1}
									</Box>
									{!isLast && <Box sx={{ width: 2, flexGrow: 1, minHeight: 18, backgroundColor: colors.rule, my: 0.25 }} />}
								</Stack>
								<Box sx={{ flexGrow: 1, pt: 0.25, pb: isLast ? 0 : 1 }}>
									<Typography variant="body2" sx={{ color: colors.ink, fontWeight: 700 }}>
										{step.label}
									</Typography>
									<Typography variant="caption" sx={{ color: colors.inkSoft }}>
										{step.description}
									</Typography>
								</Box>
								{isCurrent && (
									<Button
										variant="contained"
										size="small"
										onClick={(event) => {
											event.stopPropagation();
											navigate(step.path);
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
			</Stack>
		</Paper>
	);
}
