import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { login as loginRequest } from "../api/auth";
import { clearSession, getSession, setSession, type StoredSession } from "../api/tokenStore";

interface AuthContextValue {
	session: StoredSession | null;
	isAuthenticated: boolean;
	login: (organisationSlug: string, email: string, password: string) => Promise<void>;
	logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
	const [session, setSessionState] = useState<StoredSession | null>(() => getSession());

	const login = useCallback(async (organisationSlug: string, email: string, password: string) => {
		const response = await loginRequest({ organisationSlug, email, password });
		const newSession: StoredSession = {
			token: response.token,
			expiresAt: response.expiresAt,
			userId: response.userId,
			organisationId: response.organisationId,
		};
		setSession(newSession);
		setSessionState(newSession);
	}, []);

	const logout = useCallback(() => {
		clearSession();
		setSessionState(null);
	}, []);

	const value = useMemo<AuthContextValue>(
		() => ({ session, isAuthenticated: session !== null, login, logout }),
		[session, login, logout],
	);

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
	const context = useContext(AuthContext);
	if (!context) {
		throw new Error("useAuth must be used within an AuthProvider");
	}
	return context;
}
