import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { platformLogin } from "../api/platformAuth";
import {
	clearPlatformSession,
	getPlatformSession,
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
