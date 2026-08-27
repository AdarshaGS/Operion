import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { colors } from "../../theme";

interface StatTileProps {
	label: string;
	value: string | number;
	sublabel?: string;
}

/** Small reusable stat card for the Dashboard - label, a big number, optional
 * sub-label (e.g. a percentage or a count breakdown). Existing theme tokens only, no
 * new colors. */
export function StatTile({ label, value, sublabel }: StatTileProps) {
	return (
		<Paper sx={{ p: 2, height: "100%" }}>
			<Stack spacing={0.5}>
				<Typography variant="body2" sx={{ color: colors.inkSoft }}>
					{label}
				</Typography>
				<Typography variant="h4" sx={{ color: colors.ink }}>
					{value}
				</Typography>
				{sublabel && (
					<Typography variant="caption" sx={{ color: colors.inkFaint }}>
						{sublabel}
					</Typography>
				)}
			</Stack>
		</Paper>
	);
}
