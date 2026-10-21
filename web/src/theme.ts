import { createTheme, type Theme } from "@mui/material/styles";

/**
 * Design language: soft SaaS card UI - lavender-tinted background, white (light) or deep navy
 * (dark) surfaces, a single indigo/violet accent for primary actions and the active nav item,
 * generously rounded cards with soft shadows. One sans face throughout.
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
	info: string;
	infoBg: string;
	/** Soft tinted backgrounds for icon tiles / stat cards, one per hue. */
	tintViolet: string;
	tintGreen: string;
	tintOrange: string;
	tintPink: string;
	tintBlue: string;
	/** Full CSS `background` values (gradients) - used for the hero banner and the active sidebar item. */
	heroBg: string;
	navActiveBg: string;
	navActiveInk: string;
	/** Surface the sidebar and top bar sit on. */
	chrome: string;
	shadowCard: string;
};

const lightTokens: ColorTokens = {
	paper: "#F5F6FC",
	paperRaised: "#FFFFFF",
	paperSunken: "#EEF0FA",
	ink: "#1A1D3B",
	inkSoft: "#555B7A",
	inkFaint: "#8B90AD",
	rule: "#E6E8F4",
	ruleStrong: "#8A91C8",
	accent: "#5B50E6",
	accentStrong: "#4337C8",
	accentSoft: "rgba(91,80,230,0.10)",
	accentInk: "#FFFFFF",
	ok: "#1E9F62",
	okBg: "rgba(30,159,98,0.12)",
	warn: "#D9730D",
	warnBg: "rgba(217,115,13,0.13)",
	bad: "#E0394A",
	badBg: "rgba(224,57,74,0.10)",
	info: "#2F7BE0",
	infoBg: "rgba(47,123,224,0.11)",
	tintViolet: "#EDEBFF",
	tintGreen: "#E3F6EC",
	tintOrange: "#FFEEDC",
	tintPink: "#FFE6EB",
	tintBlue: "#E3EEFF",
	heroBg: "linear-gradient(120deg, #E4E0FF 0%, #E9EEFF 55%, #DDEBFF 100%)",
	navActiveBg: "rgba(91,80,230,0.12)",
	navActiveInk: "#4337C8",
	chrome: "#FFFFFF",
	shadowCard: "0 1px 2px rgba(40,44,100,0.04), 0 6px 20px rgba(40,44,100,0.06)",
};

const darkTokens: ColorTokens = {
	paper: "#0B0E1C",
	paperRaised: "#131733",
	paperSunken: "#0A0C19",
	ink: "#EEF0FB",
	inkSoft: "#A8AECB",
	inkFaint: "#6E7596",
	rule: "#242A4D",
	ruleStrong: "#8E97D8",
	accent: "#8174FF",
	accentStrong: "#6355F0",
	accentSoft: "rgba(129,116,255,0.18)",
	accentInk: "#FFFFFF",
	ok: "#34D58A",
	okBg: "rgba(52,213,138,0.15)",
	warn: "#F5A524",
	warnBg: "rgba(245,165,36,0.15)",
	bad: "#FF6B78",
	badBg: "rgba(255,107,120,0.15)",
	info: "#5AA2FF",
	infoBg: "rgba(90,162,255,0.16)",
	tintViolet: "rgba(129,116,255,0.20)",
	tintGreen: "rgba(52,213,138,0.16)",
	tintOrange: "rgba(245,165,36,0.16)",
	tintPink: "rgba(255,107,120,0.16)",
	tintBlue: "rgba(90,162,255,0.17)",
	heroBg: "linear-gradient(120deg, #151A45 0%, #1B1F5E 50%, #172A66 100%)",
	navActiveBg: "linear-gradient(90deg, #6D3FE8 0%, #4F6BF0 100%)",
	navActiveInk: "#FFFFFF",
	chrome: "#0F1330",
	shadowCard: "0 1px 2px rgba(0,0,0,0.30), 0 8px 24px rgba(0,0,0,0.28)",
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

export const fontBody = '"Inter", -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
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
			info: { main: t.info },
			background: { default: t.paper, paper: t.paperRaised },
			text: { primary: t.ink, secondary: t.inkSoft, disabled: t.inkFaint },
			divider: t.rule,
		},
		shape: { borderRadius: 12 },
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
						borderRadius: 10,
						"&.Mui-selected": {
							backgroundColor: colors.accentSoft,
							"&:hover": { backgroundColor: colors.accentSoft },
						},
					},
				},
			},
			MuiPaper: {
				styleOverrides: {
					root: { backgroundImage: "none", borderRadius: 16 },
					elevation1: { boxShadow: colors.shadowCard },
					outlined: { borderColor: colors.rule, boxShadow: colors.shadowCard },
				},
			},
			// Alert is built on Paper, so it inherited the 16px card radius and read as a pill when one line tall.
			MuiAlert: {
				styleOverrides: { root: { borderRadius: 12 } },
			},
			MuiMenu: {
				styleOverrides: { paper: { borderRadius: 12, border: `1px solid ${colors.rule}` } },
			},
			MuiButton: {
				defaultProps: { disableElevation: true },
				styleOverrides: {
					root: { borderRadius: 10 },
					outlined: { borderColor: colors.rule, "&:hover": { borderColor: colors.accent, backgroundColor: colors.accentSoft } },
				},
				variants: [
					{
						props: { variant: "contained", color: "primary" },
						style: { background: `linear-gradient(135deg, ${colors.accent} 0%, ${colors.accentStrong} 100%)`, "&.Mui-disabled": { background: colors.paperSunken, color: colors.inkFaint } },
					},
				],
			},
			MuiChip: {
				styleOverrides: {
					root: { fontWeight: 600, borderRadius: 8 },
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
