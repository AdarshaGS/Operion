import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import CssBaseline from "@mui/material/CssBaseline";
import GlobalStyles from "@mui/material/GlobalStyles";
import { buildTheme, themeCssVars, type ThemeMode } from "./theme";

export type ThemePreference = ThemeMode | "system";

const STORAGE_KEY = "operion.themePreference";

function systemPrefersDark(): boolean {
	try {
		return window.matchMedia("(prefers-color-scheme: dark)").matches;
	} catch {
		return false;
	}
}

function readStoredPreference(): ThemePreference {
	try {
		const stored = localStorage.getItem(STORAGE_KEY);
		if (stored === "light" || stored === "dark") return stored;
	} catch {
		// per-viewer convenience only - fine to fall back to system default
	}
	return "system";
}

function resolveMode(preference: ThemePreference): ThemeMode {
	return preference === "system" ? (systemPrefersDark() ? "dark" : "light") : preference;
}

interface ThemeModeContextValue {
	preference: ThemePreference;
	mode: ThemeMode;
	setPreference: (preference: ThemePreference) => void;
}

const ThemeModeContext = createContext<ThemeModeContextValue | null>(null);

export function ThemeModeProvider({ children }: { children: ReactNode }) {
	const [preference, setPreferenceState] = useState<ThemePreference>(readStoredPreference);
	const [mode, setMode] = useState<ThemeMode>(() => resolveMode(preference));

	useEffect(() => {
		setMode(resolveMode(preference));
		if (preference !== "system") return undefined;
		let mediaQuery: MediaQueryList;
		try {
			mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
		} catch {
			return undefined;
		}
		const onChange = () => setMode(resolveMode("system"));
		mediaQuery.addEventListener("change", onChange);
		return () => mediaQuery.removeEventListener("change", onChange);
	}, [preference]);

	useEffect(() => {
		document.documentElement.dataset.theme = mode;
		document.documentElement.style.colorScheme = mode;
	}, [mode]);

	function setPreference(next: ThemePreference) {
		setPreferenceState(next);
		try {
			if (next === "system") localStorage.removeItem(STORAGE_KEY);
			else localStorage.setItem(STORAGE_KEY, next);
		} catch {
			// per-viewer convenience only - the choice just won't survive a reload
		}
	}

	const theme = useMemo(() => buildTheme(mode), [mode]);

	return (
		<ThemeModeContext.Provider value={{ preference, mode, setPreference }}>
			<MuiThemeProvider theme={theme}>
				<GlobalStyles styles={themeCssVars} />
				<CssBaseline />
				{children}
			</MuiThemeProvider>
		</ThemeModeContext.Provider>
	);
}

export function useThemeMode(): ThemeModeContextValue {
	const ctx = useContext(ThemeModeContext);
	if (!ctx) throw new Error("useThemeMode must be used within ThemeModeProvider");
	return ctx;
}
