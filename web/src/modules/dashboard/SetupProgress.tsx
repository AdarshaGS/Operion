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
import { colors } from "../../theme";

interface Step {
	key: keyof SetupChecklist;
	label: string;
	description: string;
	path: string;
	ctaLabel: string;
}

/** Order matches #97's stated sequence: Structure -> Roles -> Members -> Academic
 * setup -> Students -> Fees. Guidance, not a gate - every linked screen is reachable
 * normally regardless of progress state. */
const STEPS: Step[] = [
	{ key: "structureConfigured", label: "Structure", description: "School structure is ready", path: "/setup/structure", ctaLabel: "Go to structure" },
	{ key: "rolesConfigured", label: "Roles", description: "Roles and permissions set", path: "/settings/roles", ctaLabel: "Manage roles" },
	{ key: "membersAdded", label: "Members", description: "Team members added", path: "/members", ctaLabel: "Invite members" },
	{
		key: "academicSetupConfigured",
		label: "Academic setup",
		description: "Set up academic year, classes and subjects",
		path: "/academics/setup",
		ctaLabel: "Set up academic year",
	},
	{ key: "studentsAdded", label: "Students", description: "Add your students", path: "/students", ctaLabel: "Add students" },
	{ key: "feesConfigured", label: "Fees", description: "Configure fee structure", path: "/fees/setup", ctaLabel: "Configure fees" },
];

interface SetupProgressProps {
	checklist: SetupChecklist;
	onDismiss: () => void;
}

/** Post-login "next setup step" card (#97) - a numbered vertical stepper over the
 * SetupChecklist signal, shown until every step reports done or the user dismisses it
 * early (permanent per-user dismissal, for anyone who already knows the system). */
export function SetupProgress({ checklist, onDismiss }: SetupProgressProps) {
	const navigate = useNavigate();

	const doneCount = STEPS.filter((step) => checklist[step.key]).length;
	if (doneCount === STEPS.length) {
		return null;
	}
	const currentIndex = STEPS.findIndex((step) => !checklist[step.key]);

	return (
		<Paper sx={{ p: 3, height: "100%" }}>
			<Stack spacing={2}>
				<Stack direction="row" alignItems="center" justifyContent="space-between">
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
						of {STEPS.length} setup tasks complete
					</Typography>
					<LinearProgress
						variant="determinate"
						value={(doneCount / STEPS.length) * 100}
						sx={{
							height: 6,
							borderRadius: 3,
							backgroundColor: colors.paperSunken,
							"& .MuiLinearProgress-bar": { backgroundColor: colors.accent, borderRadius: 3 },
						}}
					/>
				</Box>
				<Stack spacing={0}>
					{STEPS.map((step, index) => {
						const done = checklist[step.key];
						const isCurrent = index === currentIndex;
						const isLast = index === STEPS.length - 1;
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
