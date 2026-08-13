import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { login as loginRequest, me as meRequest } from "../api/auth";
import { clearSession, getSession, setSession, type StoredSession } from "../api/tokenStore";

export interface Profile {
	personId: number | null;
	personName: string | null;
	email: string | null;
	roleNames: string[];
	organisationName: string | null;
}

interface AuthContextValue {
	session: StoredSession | null;
	isAuthenticated: boolean;
	/** True once the initial /auth/me fetch (or its absence, when logged out) has resolved. */
	permissionsLoaded: boolean;
	profile: Profile | null;
	hasPermission: (code: string) => boolean;
	hasAnyPermission: (codes: string[]) => boolean;
	login: (organisationSlug: string, email: string, password: string) => Promise<void>;
	logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
	const [session, setSessionState] = useState<StoredSession | null>(() => getSession());
	const [permissions, setPermissions] = useState<Set<string>>(new Set());
	const [permissionsLoaded, setPermissionsLoaded] = useState(false);
	const [profile, setProfile] = useState<Profile | null>(null);

	useEffect(() => {
		if (!session) {
			setPermissions(new Set());
			setProfile(null);
			setPermissionsLoaded(true);
			return;
		}
		setPermissionsLoaded(false);
		meRequest()
			.then((response) => {
				setPermissions(new Set(response.permissions));
				setProfile({
					personId: response.personId,
					personName: response.personName,
					email: response.email,
					roleNames: response.roleNames,
					organisationName: response.organisationName,
				});
			})
			.finally(() => setPermissionsLoaded(true));
	}, [session]);

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

	const hasPermission = useCallback((code: string) => permissions.has(code), [permissions]);
	const hasAnyPermission = useCallback((codes: string[]) => codes.some((code) => permissions.has(code)), [permissions]);

	const value = useMemo<AuthContextValue>(
		() => ({
			session,
			isAuthenticated: session !== null,
			permissionsLoaded,
			profile,
			hasPermission,
			hasAnyPermission,
			login,
			logout,
		}),
		[session, permissionsLoaded, profile, hasPermission, hasAnyPermission, login, logout],
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
