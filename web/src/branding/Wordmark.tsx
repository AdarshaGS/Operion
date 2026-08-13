import Box from "@mui/material/Box";
import { colors, fontDisplay } from "../theme";

interface SealProps {
	size?: number;
}

/** The stamp-seal mark — reused standalone (favicon-adjacent contexts) and inside Wordmark. */
export function Seal({ size = 34 }: SealProps) {
	return (
		<Box
			sx={{
				width: size,
				height: size,
				borderRadius: "50%",
				border: `2px solid ${colors.accent}`,
				display: "flex",
				alignItems: "center",
				justifyContent: "center",
				flexShrink: 0,
				position: "relative",
				transform: "rotate(-8deg)",
				"&::after": {
					content: '""',
					position: "absolute",
					inset: 4,
					border: `1px dashed ${colors.accent}`,
					borderRadius: "50%",
					opacity: 0.6,
				},
			}}
		>
			<Box component="span" sx={{ fontFamily: fontDisplay, fontWeight: 700, fontSize: size * 0.34, color: colors.accent }}>
				O
			</Box>
		</Box>
	);
}

interface WordmarkProps {
	size?: "small" | "medium";
	tagline?: string;
}

/** OPERION wordmark + seal, shared by the top bar, login screen, and marketing page. */
export function Wordmark({ size = "medium", tagline }: WordmarkProps) {
	const sealSize = size === "small" ? 24 : 34;
	return (
		<Box sx={{ display: "flex", alignItems: "center", gap: size === "small" ? 0.85 : 1.1 }}>
			<Seal size={sealSize} />
			<Box>
				<Box
					component="span"
					sx={{
						fontFamily: fontDisplay,
						fontWeight: 700,
						letterSpacing: "0.06em",
						fontSize: size === "small" ? "0.85rem" : "1.05rem",
						color: colors.ink,
						display: "block",
						lineHeight: 1.1,
					}}
				>
					OPERION
				</Box>
				{tagline && (
					<Box
						component="span"
						sx={{
							fontFamily: fontDisplay,
							fontSize: "0.6rem",
							letterSpacing: "0.14em",
							textTransform: "uppercase",
							color: colors.inkFaint,
						}}
					>
						{tagline}
					</Box>
				)}
			</Box>
		</Box>
	);
}
