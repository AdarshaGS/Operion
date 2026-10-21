import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import ButtonBase from "@mui/material/ButtonBase";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { colors } from "../theme";

export type Tint = "violet" | "green" | "orange" | "pink" | "blue";

const TINT_BG: Record<Tint, string> = {
	violet: colors.tintViolet,
	green: colors.tintGreen,
	orange: colors.tintOrange,
	pink: colors.tintPink,
	blue: colors.tintBlue,
};

const TINT_INK: Record<Tint, string> = {
	violet: colors.accent,
	green: colors.ok,
	orange: colors.warn,
	pink: colors.bad,
	blue: colors.info,
};

/** Rounded square holding an icon, tinted per hue - the visual anchor of every dashboard card. */
export function IconTile({ icon, tint = "violet", size = 44 }: { icon: ReactNode; tint?: Tint; size?: number }) {
	return (
		<Box
			sx={{
				width: size,
				height: size,
				borderRadius: "12px",
				display: "flex",
				alignItems: "center",
				justifyContent: "center",
				flexShrink: 0,
				color: TINT_INK[tint],
				backgroundColor: TINT_BG[tint],
			}}
		>
			{icon}
		</Box>
	);
}

/** Card with an icon + title header and an optional "View all"-style action. */
export function SectionCard({
	title,
	icon,
	tint = "violet",
	actionLabel,
	actionPath,
	headerExtra,
	children,
	sx,
}: {
	title: string;
	icon?: ReactNode;
	tint?: Tint;
	actionLabel?: string;
	actionPath?: string;
	headerExtra?: ReactNode;
	children: ReactNode;
	sx?: object;
}) {
	const navigate = useNavigate();
	return (
		<Paper sx={{ p: 2.5, height: "100%", display: "flex", flexDirection: "column", minWidth: 0, ...sx }}>
			<Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", columnGap: 1, rowGap: 1.5, mb: 2 }}>
				<Stack direction="row" sx={{ alignItems: "center", gap: 1.25, minWidth: 0 }}>
					{icon && <IconTile icon={icon} tint={tint} size={34} />}
					<Typography variant="h6" sx={{ fontSize: "1rem", color: colors.ink }}>
						{title}
					</Typography>
				</Stack>
				<Stack direction="row" sx={{ alignItems: "center", gap: 1 }}>
					{headerExtra}
					{actionLabel && actionPath && (
						<ButtonBase
							onClick={() => navigate(actionPath)}
							sx={{
								px: 1.25,
								py: 0.5,
								gap: 0.5,
								borderRadius: "8px",
								fontSize: "0.78rem",
								fontWeight: 600,
								color: colors.accent,
								backgroundColor: colors.accentSoft,
								whiteSpace: "nowrap",
							}}
						>
							{actionLabel}
							<ArrowForwardIcon sx={{ fontSize: 14 }} />
						</ButtonBase>
					)}
				</Stack>
			</Stack>
			<Box sx={{ flexGrow: 1, minHeight: 0 }}>{children}</Box>
		</Paper>
	);
}

/** Compact headline figure for list and workspace pages: small icon tile, label, value and an optional detail line.
 * StatCard is the roomier variant for the dashboard. */
export function KpiTile({
	icon,
	tint,
	label,
	value,
	detail,
	onClick,
}: {
	icon: ReactNode;
	tint: Tint;
	label: string;
	value: string | number;
	detail?: ReactNode;
	onClick?: () => void;
}) {
	return (
		<Paper
			onClick={onClick}
			sx={{ p: 1.75, display: "flex", alignItems: "center", gap: 1.5, minWidth: 0, cursor: onClick ? "pointer" : "default" }}
		>
			<IconTile icon={icon} tint={tint} size={40} />
			<Box sx={{ minWidth: 0 }}>
				<Typography variant="caption" sx={{ color: colors.inkSoft, display: "block" }}>
					{label}
				</Typography>
				<Typography variant="h6" sx={{ fontWeight: 800, color: colors.ink, lineHeight: 1.2 }}>
					{value}
				</Typography>
				{detail && (
					<Typography variant="caption" sx={{ color: colors.inkSoft }} component="div">
						{detail}
					</Typography>
				)}
			</Box>
		</Paper>
	);
}

/** Headline number: tinted icon tile, label, big value, optional status chip and a link into the module. */
export function StatCard({
	icon,
	tint = "violet",
	label,
	value,
	chip,
	chipTone = "neutral",
	linkLabel,
	linkPath,
	onClick,
}: {
	icon: ReactNode;
	tint?: Tint;
	label: string;
	value: string | number;
	chip?: string;
	chipTone?: "good" | "warn" | "bad" | "neutral";
	linkLabel?: string;
	linkPath?: string;
	onClick?: () => void;
}) {
	const navigate = useNavigate();
	const chipColors = {
		good: { fg: colors.ok, bg: colors.okBg },
		warn: { fg: colors.warn, bg: colors.warnBg },
		bad: { fg: colors.bad, bg: colors.badBg },
		neutral: { fg: colors.inkSoft, bg: colors.paperSunken },
	}[chipTone];
	return (
		<Paper
			onClick={onClick ?? (linkPath ? () => navigate(linkPath) : undefined)}
			sx={{
				p: 2.5,
				minWidth: 0,
				position: "relative",
				overflow: "hidden",
				containerType: "inline-size",
				cursor: onClick || linkPath ? "pointer" : "default",
				transition: "transform 120ms, box-shadow 120ms",
				"&:hover": onClick || linkPath ? { transform: "translateY(-2px)" } : undefined,
				// Soft tinted wave in the corner, as in the target design.
				"&::after": {
					content: '""',
					position: "absolute",
					right: -30,
					bottom: -40,
					width: 170,
					height: 110,
					borderRadius: "50%",
					backgroundColor: TINT_BG[tint],
					opacity: 0.7,
					pointerEvents: "none",
				},
			}}
		>
			<Box
				sx={{
					position: "relative",
					zIndex: 1,
					display: "flex",
					gap: 2,
					// A narrow card (4-up on a small laptop) stacks the icon above the text instead of clipping the value.
					"@container (max-width: 270px)": { flexDirection: "column", gap: 1 },
				}}
			>
				<IconTile icon={icon} tint={tint} size={56} />
				<Box sx={{ minWidth: 0 }}>
					<Typography variant="body2" sx={{ color: colors.inkSoft, fontWeight: 500 }}>
						{label}
					</Typography>
					<Typography sx={{ fontSize: "1.9rem", fontWeight: 800, lineHeight: 1.2, color: colors.ink, letterSpacing: "-0.02em" }}>{value}</Typography>
					{chip && (
						<Box component="span" sx={{ display: "inline-block", mt: 0.5, px: 1, py: 0.15, borderRadius: "8px", fontSize: "0.75rem", fontWeight: 700, color: chipColors.fg, backgroundColor: chipColors.bg }}>
							{chip}
						</Box>
					)}
					{linkLabel && (
						<Stack direction="row" sx={{ alignItems: "center", mt: 0.5, color: colors.accent, fontSize: "0.8rem", fontWeight: 600 }}>
							{linkLabel}
							<ChevronRightIcon sx={{ fontSize: 16 }} />
						</Stack>
					)}
				</Box>
			</Box>
		</Paper>
	);
}

/** Responsive grid of StatCards. With `columns` it uses that many equal columns on wide screens
 * (so six tiles read as 3x2, not 5+1); without it, as many equal columns as fit. */
export function StatGrid({ children, columns }: { children: ReactNode; columns?: number }) {
	return (
		<Box
			sx={{
				display: "grid",
				gap: 2.5,
				gridTemplateColumns: columns
					? { xs: "minmax(0, 1fr)", sm: "repeat(2, minmax(0, 1fr))", lg: `repeat(${columns}, minmax(0, 1fr))` }
					: "repeat(auto-fit, minmax(200px, 1fr))",
			}}
		>
			{children}
		</Box>
	);
}

/** Muted empty-state line used inside cards. */
export function EmptyNote({ children }: { children: ReactNode }) {
	return (
		<Stack sx={{ py: 3, alignItems: "center", textAlign: "center" }}>
			<Typography variant="body2" sx={{ color: colors.inkSoft }}>
				{children}
			</Typography>
		</Stack>
	);
}

/** Greeting banner: gradient surface, school illustration and a quote card. */
export function HeroBanner({ title, subtitle, quote }: { title: ReactNode; subtitle: string; quote?: string }) {
	return (
		<Paper
			sx={{
				position: "relative",
				overflow: "hidden",
				minHeight: 150,
				p: { xs: 2.5, md: 3.5 },
				display: "flex",
				alignItems: "center",
				justifyContent: "space-between",
				gap: 3,
				background: colors.heroBg,
				border: `1px solid ${colors.rule}`,
			}}
		>
			<Box sx={{ position: "relative", zIndex: 2, minWidth: 0 }}>
				<Typography component="h1" sx={{ fontSize: { xs: "1.6rem", md: "2.1rem" }, fontWeight: 800, letterSpacing: "-0.02em", color: colors.ink }}>
					{title}
				</Typography>
				<Typography sx={{ mt: 0.5, color: colors.inkSoft }}>{subtitle}</Typography>
			</Box>
			<Box aria-hidden sx={{ position: "absolute", right: 290, bottom: 0, height: "100%", display: "none", "@media (min-width: 1400px)": { display: "block" }, zIndex: 1 }}>
				<SchoolIllustration />
			</Box>
			{quote && (
				<Box
					sx={{
						position: "relative",
						zIndex: 2,
						display: { xs: "none", md: "block" },
						width: 250,
						flexShrink: 0,
						p: 2,
						borderRadius: "16px",
						backgroundColor: colors.paperRaised,
						border: `1px solid ${colors.rule}`,
						boxShadow: colors.shadowCard,
						opacity: 0.96,
					}}
				>
					<Typography sx={{ fontSize: "1.6rem", lineHeight: 0.8, color: colors.accent, fontWeight: 800 }}>“</Typography>
					<Typography variant="body2" sx={{ color: colors.ink }}>
						{quote}
					</Typography>
					<Box sx={{ mt: 1, width: 28, height: 3, borderRadius: 2, backgroundColor: colors.accent }} />
				</Box>
			)}
		</Paper>
	);
}

/** A small flat school-building scene - purely decorative, drawn with theme tokens so it follows light/dark. */
function SchoolIllustration() {
	return (
		<svg viewBox="0 0 360 150" height="100%" style={{ display: "block" }} role="presentation">
			<ellipse cx="180" cy="146" rx="170" ry="8" fill={colors.accent} opacity="0.10" />
			{/* trees */}
			<g fill={colors.ok} opacity="0.85">
				<circle cx="28" cy="96" r="24" />
				<circle cx="52" cy="108" r="20" />
				<circle cx="334" cy="98" r="24" />
				<circle cx="310" cy="110" r="19" />
			</g>
			<g fill={colors.inkFaint} opacity="0.7">
				<rect x="38" y="112" width="5" height="34" />
				<rect x="321" y="112" width="5" height="34" />
			</g>
			{/* building */}
			<rect x="82" y="64" width="196" height="82" rx="6" fill={colors.paperRaised} stroke={colors.rule} />
			<rect x="150" y="40" width="60" height="106" rx="6" fill={colors.paperRaised} stroke={colors.rule} />
			<polygon points="140,42 180,18 220,42" fill={colors.accent} />
			<rect x="170" y="106" width="20" height="40" rx="3" fill={colors.accentStrong} />
			<g fill={colors.info} opacity="0.55">
				{[96, 118, 232, 254].map((x) => (
					<rect key={`a${x}`} x={x} y={80} width="14" height="16" rx="2" />
				))}
				{[96, 118, 232, 254].map((x) => (
					<rect key={`b${x}`} x={x} y={106} width="14" height="16" rx="2" />
				))}
				<rect x="164" y="56" width="12" height="14" rx="2" />
				<rect x="184" y="56" width="12" height="14" rx="2" />
				<rect x="164" y="78" width="12" height="14" rx="2" />
				<rect x="184" y="78" width="12" height="14" rx="2" />
			</g>
			{/* students */}
			<g>
				<circle cx="120" cy="128" r="6" fill={colors.warn} />
				<rect x="114" y="134" width="12" height="12" rx="4" fill={colors.accent} />
				<circle cx="240" cy="128" r="6" fill={colors.bad} />
				<rect x="234" y="134" width="12" height="12" rx="4" fill={colors.info} />
			</g>
		</svg>
	);
}
