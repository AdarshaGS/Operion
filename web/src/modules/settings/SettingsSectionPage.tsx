import { Navigate, useNavigate, useParams } from "react-router-dom";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useBackTarget } from "../../utils/useBackTarget";
import { SectionBackContext } from "./PanelTitle";
import { useVisibleSettingsGroups } from "./settingsSections";

/** One page per Settings section, reached from the SettingsPage landing grid - renders
 * that section's existing panel component with a title + back button, so it opens as
 * its own page instead of everything being stacked on one long Settings screen. */
export function SettingsSectionPage() {
	const { section: sectionKey } = useParams<{ section: string }>();
	const navigate = useNavigate();
	// Opened from the Dashboard setup card (e.g. Academic year creation), Back returns there, not to Settings.
	const back = useBackTarget("/settings");
	const groups = useVisibleSettingsGroups();
	const section = groups.flatMap((group) => group.sections).find((candidate) => candidate.key === sectionKey);

	if (!section) {
		return <Navigate to="/settings" replace />;
	}

	const backNode = (
		<IconButton onClick={() => navigate(back.path)} aria-label={back.fromDashboard ? "Back to dashboard" : "Back to settings"}>
			<ArrowBackIcon />
		</IconButton>
	);

	// The panel draws its own title (via PanelTitle) with the back arrow beside it.
	if (section.selfTitled) {
		return (
			<SectionBackContext.Provider value={backNode}>
				<Box>{section.panel}</Box>
			</SectionBackContext.Provider>
		);
	}

	return (
		<Stack spacing={3}>
			<Stack direction="row" spacing={0.5} sx={{ alignItems: "center", ml: -1 }}>
				{backNode}
				<Typography variant="h6" component="h1">
					{section.label}
				</Typography>
			</Stack>
			<Box>{section.panel}</Box>
		</Stack>
	);
}
