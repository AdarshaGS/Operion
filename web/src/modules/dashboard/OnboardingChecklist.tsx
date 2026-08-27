import { useState } from "react";
import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import CloseIcon from "@mui/icons-material/Close";
import RadioButtonUncheckedIcon from "@mui/icons-material/RadioButtonUnchecked";
import type { SetupChecklist as SetupChecklistData } from "../../api/dashboard";
import { colors } from "../../theme";

const DISMISSED_KEY = "operion.onboardingDismissed";

interface Step {
	key: keyof SetupChecklistData;
	label: string;
	description: string;
	path: string;
}

/** Order matches #97's stated sequence: Structure -> Roles -> Members -> industry
 * modules/data. Guidance, not a gate - every linked screen is reachable normally
 * regardless of checklist state. */
const STEPS: Step[] = [
	{ key: "structureConfigured", label: "Structure", description: "Set up campuses, departments, and designations", path: "/settings" },
	{ key: "rolesConfigured", label: "Roles", description: "Review or add roles for your organisation", path: "/settings/roles" },
	{ key: "membersAdded", label: "Members", description: "Invite staff and grant them access", path: "/settings/users" },
	{
		key: "industryDataAdded",
		label: "Industry data",
		description: "Start adding students and academic data",
		path: "/students",
	},
];

function readDismissed(): boolean {
	try {
		return localStorage.getItem(DISMISSED_KEY) === "1";
	} catch {
		return false;
	}
}

interface OnboardingChecklistProps {
	checklist: SetupChecklistData;
}

export function OnboardingChecklist({ checklist }: OnboardingChecklistProps) {
	const navigate = useNavigate();
	const [dismissed, setDismissed] = useState(readDismissed);

	const allDone = STEPS.every((step) => checklist[step.key]);
	if (dismissed || allDone) {
		return null;
	}

	function dismiss() {
		setDismissed(true);
		try {
			localStorage.setItem(DISMISSED_KEY, "1");
		} catch {
			// per-viewer convenience only - fine to no-op when storage is unavailable
		}
	}

	return (
		<Paper sx={{ p: 2 }}>
			<Stack spacing={1.5}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="subtitle1">Getting started</Typography>
					<IconButton size="small" onClick={dismiss} aria-label="Dismiss getting-started checklist">
						<CloseIcon fontSize="small" />
					</IconButton>
				</Box>
				<Stack spacing={0.5}>
					{STEPS.map((step) => {
						const done = checklist[step.key];
						return (
							<Box
								key={step.key}
								onClick={() => navigate(step.path)}
								sx={{
									display: "flex",
									alignItems: "center",
									gap: 1.5,
									px: 1,
									py: 1,
									borderRadius: 1,
									cursor: "pointer",
									"&:hover": { bgcolor: colors.paperSunken },
								}}
							>
								{done ? (
									<CheckCircleIcon fontSize="small" sx={{ color: colors.ok }} />
								) : (
									<RadioButtonUncheckedIcon fontSize="small" sx={{ color: colors.inkFaint }} />
								)}
								<Box>
									<Typography variant="body2" sx={{ color: colors.ink, fontWeight: 600 }}>
										{step.label}
									</Typography>
									<Typography variant="caption" sx={{ color: colors.inkSoft }}>
										{step.description}
									</Typography>
								</Box>
							</Box>
						);
					})}
				</Stack>
			</Stack>
		</Paper>
	);
}
