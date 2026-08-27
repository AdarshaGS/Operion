import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import ButtonBase from "@mui/material/ButtonBase";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { colors } from "../../theme";
import { SETTINGS_SECTIONS } from "./settingsSections";

/** Landing page for Settings - a list of what can be configured, each opening its own
 * page (SettingsSectionPage) rather than every panel being stacked inline on one long
 * page. Same underlying panels as before, just organized like a menu instead of a wall
 * of forms. */
export function SettingsPage() {
	const navigate = useNavigate();

	return (
		<Stack spacing={3}>
			<Grid container>
				{SETTINGS_SECTIONS.map((section) => (
					<Grid size={{ xs: 12, sm: 6 }} key={section.key}>
						<ButtonBase
							onClick={() => navigate(`/settings/${section.key}`)}
							sx={{
								display: "block",
								width: "100%",
								textAlign: "left",
								px: 2,
								py: 1.75,
								borderBottom: `1px solid ${colors.rule}`,
								"&:hover": { bgcolor: colors.paperSunken },
							}}
						>
							<Stack direction="row" spacing={1.5} sx={{ alignItems: "flex-start" }}>
								<Box sx={{ color: colors.ruleStrong, mt: 0.25 }}>{section.icon}</Box>
								<Box>
									<Typography variant="subtitle1" sx={{ color: colors.ink }}>
										{section.label}
									</Typography>
									<Typography variant="body2" sx={{ color: colors.inkSoft }}>
										{section.description}
									</Typography>
								</Box>
							</Stack>
						</ButtonBase>
					</Grid>
				))}
			</Grid>
		</Stack>
	);
}
