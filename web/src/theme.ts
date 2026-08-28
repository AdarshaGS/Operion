import { createTheme } from "@mui/material/styles";

/**
 * Design language: clean SaaS card UI — light neutral background, white surfaces,
 * navy-ink text, a single crimson accent used for primary actions and active state.
 * One sans face throughout; no monospace ledger styling.
 */
export const colors = {
	paper: "#F4F5F8",
	paperRaised: "#FFFFFF",
	paperSunken: "#EEF0F4",
	ink: "#16233A",
	inkSoft: "#4B5A6E",
	inkFaint: "#8B97A0",
	rule: "#E3E6EC",
	ruleStrong: "#5C7A99",
	accent: "#A32638",
	accentStrong: "#7E1E2B",
	accentSoft: "rgba(163,38,56,0.08)",
	accentInk: "#FFFFFF",
	ok: "#2F7A4F",
	okBg: "rgba(47,122,79,0.12)",
	warn: "#97661B",
	warnBg: "rgba(151,102,27,0.14)",
	bad: "#A32638",
	badBg: "rgba(163,38,56,0.10)",
};

export const fontBody = '-apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
export const fontDisplay = fontBody;

export const theme = createTheme({
	palette: {
		mode: "light",
		primary: { main: colors.accent, dark: colors.accentStrong, contrastText: colors.accentInk },
		secondary: { main: colors.ruleStrong, contrastText: colors.accentInk },
		success: { main: colors.ok },
		warning: { main: colors.warn },
		error: { main: colors.bad },
		background: { default: colors.paper, paper: colors.paperRaised },
		text: { primary: colors.ink, secondary: colors.inkSoft, disabled: colors.inkFaint },
		divider: colors.rule,
	},
	shape: { borderRadius: 10 },
	typography: {
		fontFamily: fontBody,
		h1: { fontWeight: 700, letterSpacing: "-0.01em" },
		h2: { fontWeight: 700, letterSpacing: "-0.01em" },
		h3: { fontWeight: 700 },
		h4: { fontWeight: 700 },
		h5: { fontWeight: 700 },
		h6: { fontWeight: 700, fontSize: "1.05rem" },
		subtitle1: { fontWeight: 600 },
		subtitle2: { fontWeight: 600, fontSize: "0.8rem", letterSpacing: "0.02em" },
		button: { fontWeight: 600, textTransform: "none" },
		overline: { letterSpacing: "0.12em" },
	},
	components: {
		MuiCssBaseline: {
			styleOverrides: {
				body: { backgroundColor: colors.paper },
				table: { fontVariantNumeric: "tabular-nums" },
			},
		},
		MuiAppBar: {
			styleOverrides: {
				root: {
					backgroundColor: colors.paperRaised,
					color: colors.ink,
					boxShadow: "none",
					borderBottom: `1px solid ${colors.rule}`,
				},
			},
		},
		MuiDrawer: {
			styleOverrides: {
				paper: {
					backgroundColor: colors.paperRaised,
					borderRight: `1px solid ${colors.rule}`,
					backgroundImage: "none",
				},
			},
		},
		MuiListItemButton: {
			styleOverrides: {
				root: {
					borderLeft: "3px solid transparent",
					borderRadius: 8,
					"&.Mui-selected": {
						borderLeftColor: colors.accent,
						backgroundColor: colors.accentSoft,
						"&:hover": { backgroundColor: colors.accentSoft },
					},
				},
			},
		},
		MuiPaper: {
			styleOverrides: {
				root: { backgroundImage: "none" },
				elevation1: { boxShadow: "0 1px 2px rgba(16,24,40,0.04), 0 1px 3px rgba(16,24,40,0.06)" },
				outlined: { borderColor: colors.rule },
			},
		},
		MuiButton: {
			defaultProps: { disableElevation: true },
			styleOverrides: {
				root: { borderRadius: 8 },
				outlined: { borderColor: colors.ruleStrong },
			},
		},
		MuiChip: {
			styleOverrides: {
				root: { fontWeight: 600 },
			},
		},
		MuiTableCell: {
			styleOverrides: {
				head: {
					fontSize: "0.68rem",
					letterSpacing: "0.04em",
					textTransform: "uppercase",
					fontWeight: 700,
					color: colors.inkFaint,
					backgroundColor: colors.paperSunken,
				},
			},
		},
		MuiOutlinedInput: {
			styleOverrides: {
				root: {
					borderRadius: 8,
					"&.Mui-focused .MuiOutlinedInput-notchedOutline": { borderColor: colors.accent },
				},
			},
		},
	},
});
