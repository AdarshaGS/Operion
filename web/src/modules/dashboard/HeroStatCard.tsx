import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { colors } from "../../theme";

interface HeroStatCardProps {
	icon: React.ReactNode;
	label: string;
	value: string | number;
	linkLabel: string;
	linkPath: string;
}

/** The three headline stats at the top of the Dashboard (Active students / Today's
 * attendance / Fees due) - each a shortcut into its module, not just a number. */
export function HeroStatCard({ icon, label, value, linkLabel, linkPath }: HeroStatCardProps) {
	const navigate = useNavigate();

	return (
		<Paper sx={{ p: 3, flex: "1 1 240px", minWidth: 240 }}>
			<Stack direction="row" spacing={2}>
				<Box
					sx={{
						width: 44,
						height: 44,
						borderRadius: 2,
						display: "flex",
						alignItems: "center",
						justifyContent: "center",
						flexShrink: 0,
						color: colors.accent,
						backgroundColor: colors.accentSoft,
					}}
				>
					{icon}
				</Box>
				<Box sx={{ flexGrow: 1 }}>
					<Typography variant="body2" sx={{ color: colors.inkSoft }}>
						{label}
					</Typography>
					<Typography variant="h4" sx={{ color: colors.ink, my: 0.25 }}>
						{value}
					</Typography>
					<Box
						onClick={() => navigate(linkPath)}
						sx={{
							display: "inline-flex",
							alignItems: "center",
							gap: 0.25,
							cursor: "pointer",
							color: colors.ruleStrong,
							"&:hover": { textDecoration: "underline" },
						}}
					>
						<Typography variant="caption" sx={{ fontWeight: 600 }}>
							{linkLabel}
						</Typography>
						<ChevronRightIcon sx={{ fontSize: 14 }} />
					</Box>
				</Box>
			</Stack>
		</Paper>
	);
}
