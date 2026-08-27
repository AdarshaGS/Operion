import { Navigate, useNavigate, useParams } from "react-router-dom";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { SETTINGS_SECTIONS } from "./settingsSections";

/** One page per Settings section, reached from the SettingsPage landing grid - renders
 * that section's existing panel component with a title + back button, so it opens as
 * its own page instead of everything being stacked on one long Settings screen. */
export function SettingsSectionPage() {
	const { section: sectionKey } = useParams<{ section: string }>();
	const navigate = useNavigate();
	const section = SETTINGS_SECTIONS.find((candidate) => candidate.key === sectionKey);

	if (!section) {
		return <Navigate to="/settings" replace />;
	}

	return (
		<Stack spacing={3}>
			<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
				<IconButton onClick={() => navigate("/settings")} aria-label="Back to settings">
					<ArrowBackIcon />
				</IconButton>
				<Typography variant="h4" component="h1">
					{section.label}
				</Typography>
			</Stack>
			<Box>{section.panel}</Box>
		</Stack>
	);
}
