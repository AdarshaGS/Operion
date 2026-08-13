import { createTheme } from "@mui/material/styles";

/**
 * Design language: the school register — ruled ledger paper, fountain-pen ink, a
 * stamp-crimson accent used sparingly. Headings and anything tabular (money, roll
 * numbers, dates) use a monospace face, echoing a ledger's ruled columns; running text
 * stays on a plain sans for legibility in dense forms and tables.
 */
export const colors = {
	paper: "#EEEFE7",
	paperRaised: "#F8F8F3",
	paperSunken: "#E3E5DA",
	ink: "#16233A",
	inkSoft: "#4B5A6E",
	inkFaint: "#8B97A0",
	rule: "#C9D0C7",
	ruleStrong: "#5C7A99",
	accent: "#A32638",
	accentStrong: "#7E1E2B",
	accentInk: "#FFF7F2",
	ok: "#2F7A4F",
	okBg: "rgba(47,122,79,0.13)",
	warn: "#97661B",
	warnBg: "rgba(151,102,27,0.15)",
	bad: "#A32638",
	badBg: "rgba(163,38,56,0.12)",
};

export const fontDisplay = 'ui-monospace, "SF Mono", "Cascadia Code", "Courier New", monospace';
export const fontBody = '-apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';

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
	shape: { borderRadius: 5 },
	typography: {
		fontFamily: fontBody,
		h1: { fontFamily: fontDisplay, fontWeight: 700, letterSpacing: "-0.01em" },
		h2: { fontFamily: fontDisplay, fontWeight: 700, letterSpacing: "-0.01em" },
		h3: { fontFamily: fontDisplay, fontWeight: 700 },
		h4: { fontFamily: fontDisplay, fontWeight: 700 },
		h5: { fontFamily: fontDisplay, fontWeight: 700 },
		h6: { fontFamily: fontDisplay, fontWeight: 700, fontSize: "1.05rem" },
		subtitle1: { fontFamily: fontDisplay, fontWeight: 600 },
		subtitle2: { fontFamily: fontDisplay, fontWeight: 600, fontSize: "0.8rem", letterSpacing: "0.02em" },
		button: { fontFamily: fontBody, fontWeight: 600, textTransform: "none" },
		overline: { fontFamily: fontDisplay, letterSpacing: "0.12em" },
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
					"&.Mui-selected": {
						borderLeftColor: colors.accent,
						backgroundColor: colors.paperSunken,
						"&:hover": { backgroundColor: colors.paperSunken },
					},
				},
			},
		},
		MuiPaper: {
			styleOverrides: {
				root: { backgroundImage: "none" },
				elevation1: { boxShadow: "0 1px 2px rgba(22,35,58,0.09), 0 1px 1px rgba(22,35,58,0.05)" },
				outlined: { borderColor: colors.rule },
			},
		},
		MuiButton: {
			defaultProps: { disableElevation: true },
			styleOverrides: {
				root: { borderRadius: 4 },
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
					fontFamily: fontDisplay,
					fontSize: "0.68rem",
					letterSpacing: "0.04em",
					textTransform: "uppercase",
					color: colors.inkFaint,
					backgroundColor: colors.paperSunken,
				},
			},
		},
		MuiOutlinedInput: {
			styleOverrides: {
				root: {
					borderRadius: 4,
					"&.Mui-focused .MuiOutlinedInput-notchedOutline": { borderColor: colors.accent },
				},
			},
		},
	},
});
