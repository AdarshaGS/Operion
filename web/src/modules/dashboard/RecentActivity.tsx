import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import AssignmentIcon from "@mui/icons-material/Assignment";
import { colors } from "../../theme";

/** Static empty state for now - there is no activity-feed backend yet, so this is a
 * placeholder slot rather than a feature, matching the target layout's own empty state. */
export function RecentActivity() {
	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="subtitle1">Recent activity</Typography>
				<Box sx={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 1, py: 5 }}>
					<AssignmentIcon sx={{ fontSize: 32, color: colors.inkFaint }} />
					<Typography variant="body2" sx={{ color: colors.ink, fontWeight: 700 }}>
						No recent activity
					</Typography>
					<Typography variant="caption" sx={{ color: colors.inkSoft }}>
						Your recent actions will appear here.
					</Typography>
				</Box>
			</Stack>
		</Paper>
	);
}
