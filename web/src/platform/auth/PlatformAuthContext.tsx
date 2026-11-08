import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { platformLogin } from "../api/platformAuth";
import {
	clearPlatformSession,
	getPlatformSession,
	onPlatformSessionChangedElsewhere,
	onPlatformSessionCleared,
	setPlatformSession,
	type StoredPlatformSession,
} from "../api/platformTokenStore";

interface PlatformAuthContextValue {
	session: StoredPlatformSession | null;
	isAuthenticated: boolean;
	login: (email: string, password: string) => Promise<void>;
	logout: () => void;
}

const PlatformAuthContext = createContext<PlatformAuthContextValue | null>(null);

export function PlatformAuthProvider({ children }: { children: ReactNode }) {
	const [session, setSessionState] = useState<StoredPlatformSession | null>(() => getPlatformSession());

	// A rejected token (401 in platformClient) clears the stored session outside React; another tab
	// logging out or in changes it under us. Either way, follow storage so the route guard redirects.
	useEffect(() => onPlatformSessionCleared(() => setSessionState(null)), []);
	useEffect(() => onPlatformSessionChangedElsewhere((stored) => setSessionState((current) => (current?.token === stored?.token ? current : stored))), []);

	const login = useCallback(async (email: string, password: string) => {
		const response = await platformLogin({ email, password });
		const newSession: StoredPlatformSession = {
			token: response.token,
			expiresAt: response.expiresAt,
			platformAdminId: response.platformAdminId,
			email,
		};
		setPlatformSession(newSession);
		setSessionState(newSession);
	}, []);

	const logout = useCallback(() => {
		clearPlatformSession();
		setSessionState(null);
	}, []);

	const value = useMemo<PlatformAuthContextValue>(
		() => ({ session, isAuthenticated: session !== null, login, logout }),
		[session, login, logout],
	);

	return <PlatformAuthContext.Provider value={value}>{children}</PlatformAuthContext.Provider>;
}

export function usePlatformAuth(): PlatformAuthContextValue {
	const context = useContext(PlatformAuthContext);
	if (!context) {
		throw new Error("usePlatformAuth must be used within a PlatformAuthProvider");
	}
	return context;
}
