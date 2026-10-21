import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import {
	claimInvite as claimInviteRequest,
	claimStaffInvite as claimStaffInviteRequest,
	login as loginRequest,
	logout as logoutRequest,
	me as meRequest,
} from "../api/auth";
import { getPortalIdentity, type PortalIdentity } from "../api/portal";
import { moduleForPath } from "./modules";
import { clearSession, getSession, onSessionCleared, setSession, type StoredSession } from "../api/tokenStore";

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
	/** Routing gate resolved alongside /auth/me - null until permissionsLoaded, or when logged out. */
	portalIdentity: PortalIdentity | null;
	hasPermission: (code: string) => boolean;
	hasAnyPermission: (codes: string[]) => boolean;
	/** False when the platform owner switched this module off for the organisation. */
	isModuleEnabled: (code: string) => boolean;
	/** Same check keyed by route - true for core routes that belong to no module. */
	isPathEnabled: (path: string) => boolean;
	/** Re-reads which modules are switched off - after the School Starter Pack chooses them, no re-login needed. */
	refreshModules: () => Promise<void>;
	login: (organisationSlug: string, email: string, password: string) => Promise<void>;
	claimInvite: (organisationSlug: string, token: string, password: string) => Promise<void>;
	claimStaffInvite: (organisationSlug: string, token: string, password: string) => Promise<void>;
	logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
	const [session, setSessionState] = useState<StoredSession | null>(() => getSession());
	const [permissions, setPermissions] = useState<Set<string>>(new Set());
	const [disabledModules, setDisabledModules] = useState<Set<string>>(new Set());
	const [permissionsLoaded, setPermissionsLoaded] = useState(false);
	const [profile, setProfile] = useState<Profile | null>(null);
	const [portalIdentity, setPortalIdentity] = useState<PortalIdentity | null>(null);

	useEffect(() => onSessionCleared(() => setSessionState(null)), []);

	useEffect(() => {
		if (!session) {
			setPermissions(new Set());
			setDisabledModules(new Set());
			setProfile(null);
			setPortalIdentity(null);
			setPermissionsLoaded(true);
			return;
		}
		setPermissionsLoaded(false);
		Promise.all([meRequest(), getPortalIdentity()])
			.then(([response, identity]) => {
				setPermissions(new Set(response.permissions));
				setDisabledModules(new Set(response.disabledModules ?? []));
				setProfile({
					personId: response.personId,
					personName: response.personName,
					email: response.email,
					roleNames: response.roleNames,
					organisationName: response.organisationName,
				});
				setPortalIdentity(identity);
			})
			.finally(() => setPermissionsLoaded(true));
	}, [session]);

	const applySession = useCallback((response: { token: string; expiresAt: string; userId: number; organisationId: number }) => {
		const newSession: StoredSession = {
			token: response.token,
			expiresAt: response.expiresAt,
			userId: response.userId,
			organisationId: response.organisationId,
		};
		setSession(newSession);
		setSessionState(newSession);
	}, []);

	const login = useCallback(
		async (organisationSlug: string, email: string, password: string) => {
			applySession(await loginRequest({ organisationSlug, email, password }));
		},
		[applySession],
	);

	const claimInvite = useCallback(
		async (organisationSlug: string, token: string, password: string) => {
			applySession(await claimInviteRequest({ organisationSlug, token, password }));
		},
		[applySession],
	);

	const claimStaffInvite = useCallback(
		async (organisationSlug: string, token: string, password: string) => {
			applySession(await claimStaffInviteRequest({ organisationSlug, token, password }));
		},
		[applySession],
	);

	/** Best-effort - the local session is cleared either way, even if the backend call
	 * fails (network down, token already expired), same tolerance client.ts already has
	 * for a 401 clearing the session rather than surfacing an error. */
	const logout = useCallback(async () => {
		try {
			await logoutRequest();
		} catch {
			// Local session is still cleared below - see comment above.
		}
		clearSession();
		setSessionState(null);
	}, []);

	const hasPermission = useCallback((code: string) => permissions.has(code), [permissions]);
	const hasAnyPermission = useCallback((codes: string[]) => codes.some((code) => permissions.has(code)), [permissions]);
	const isModuleEnabled = useCallback((code: string) => !disabledModules.has(code), [disabledModules]);
	const isPathEnabled = useCallback(
		(path: string) => {
			const code = moduleForPath(path);
			return code === null || !disabledModules.has(code);
		},
		[disabledModules],
	);

	const refreshModules = useCallback(async () => {
		const response = await meRequest();
		setDisabledModules(new Set(response.disabledModules ?? []));
	}, []);

	const value = useMemo<AuthContextValue>(
		() => ({
			session,
			isAuthenticated: session !== null,
			permissionsLoaded,
			profile,
			portalIdentity,
			hasPermission,
			hasAnyPermission,
			isModuleEnabled,
			refreshModules,
			isPathEnabled,
			login,
			claimInvite,
			claimStaffInvite,
			logout,
		}),
		[
			session,
			permissionsLoaded,
			profile,
			portalIdentity,
			hasPermission,
			hasAnyPermission,
			isModuleEnabled,
			refreshModules,
			isPathEnabled,
			login,
			claimInvite,
			claimStaffInvite,
			logout,
		],
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
