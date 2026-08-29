import { createTheme, type Theme } from "@mui/material/styles";

/**
 * Design language: clean SaaS card UI - light neutral background, white surfaces,
 * navy-ink text, a single crimson accent used for primary actions and active state.
 * One sans face throughout; no monospace ledger styling.
 *
 * Tokens are duplicated per mode below, then exposed as CSS custom properties
 * (see `themeCssVars`) rather than as literal strings. `colors.*` therefore holds
 * `var(--op-*)` references: components importing `colors` stay theme-reactive
 * without needing to read from React context, because the browser resolves the
 * variable against whichever `data-theme` is set on <html>.
 */
export type ColorTokens = {
	paper: string;
	paperRaised: string;
	paperSunken: string;
	ink: string;
	inkSoft: string;
	inkFaint: string;
	rule: string;
	ruleStrong: string;
	accent: string;
	accentStrong: string;
	accentSoft: string;
	accentInk: string;
	ok: string;
	okBg: string;
	warn: string;
	warnBg: string;
	bad: string;
	badBg: string;
};

const lightTokens: ColorTokens = {
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

const darkTokens: ColorTokens = {
	paper: "#12161F",
	paperRaised: "#1A2030",
	paperSunken: "#0D1017",
	ink: "#E8ECF2",
	inkSoft: "#A9B4C2",
	inkFaint: "#6B7686",
	rule: "#2A3142",
	ruleStrong: "#7C97B3",
	accent: "#E0495C",
	accentStrong: "#C23347",
	accentSoft: "rgba(224,73,92,0.16)",
	accentInk: "#FFFFFF",
	ok: "#4CAF6C",
	okBg: "rgba(76,175,108,0.16)",
	warn: "#D9A441",
	warnBg: "rgba(217,164,65,0.16)",
	bad: "#E0495C",
	badBg: "rgba(224,73,92,0.14)",
};

function cssVarName(key: string): string {
	return `--op-${key.replace(/([A-Z])/g, "-$1").toLowerCase()}`;
}

function toCssVarRefs(tokens: ColorTokens): ColorTokens {
	return Object.fromEntries(Object.keys(tokens).map((key) => [key, `var(${cssVarName(key)})`])) as ColorTokens;
}

function toCssVarDeclarations(tokens: ColorTokens): Record<string, string> {
	return Object.fromEntries(Object.entries(tokens).map(([key, value]) => [cssVarName(key), value]));
}

/** Flat design tokens referencing CSS custom properties - safe to import anywhere and stays in sync with the active theme. */
export const colors: ColorTokens = toCssVarRefs(lightTokens);

/** Injected once via <GlobalStyles>; `[data-theme="dark"]` on <html> switches every `colors.*` reference at once. */
export const themeCssVars = {
	":root": toCssVarDeclarations(lightTokens),
	":root[data-theme='dark']": toCssVarDeclarations(darkTokens),
};

export const fontBody = '-apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
export const fontDisplay = fontBody;

export type ThemeMode = "light" | "dark";

export function buildTheme(mode: ThemeMode): Theme {
	const t = mode === "dark" ? darkTokens : lightTokens;
	return createTheme({
		palette: {
			mode,
			primary: { main: t.accent, dark: t.accentStrong, contrastText: t.accentInk },
			secondary: { main: t.ruleStrong, contrastText: t.accentInk },
			success: { main: t.ok },
			warning: { main: t.warn },
			error: { main: t.bad },
			background: { default: t.paper, paper: t.paperRaised },
			text: { primary: t.ink, secondary: t.inkSoft, disabled: t.inkFaint },
			divider: t.rule,
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
}
