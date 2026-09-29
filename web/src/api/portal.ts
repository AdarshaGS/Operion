import { api } from "./client";

export type PortalSessionType = "ADMIN" | "PORTAL";

export type PortalContextType = "GUARDIAN" | "TEACHER";

export interface ChildSummary {
	studentId: number;
	personId: number;
	name: string;
}

export interface PortalContext {
	type: PortalContextType;
	guardianId: number | null;
	children: ChildSummary[] | null;
	teacherPersonId: number | null;
}

export interface PortalIdentity {
	sessionType: PortalSessionType;
	portalContexts: PortalContext[];
}

/** Routing gate for admin shell vs. self-service portal shell - see PortalIdentityService
 * on the backend. Distinct from auth.ts's me() (/api/v1/auth/me), which stays the admin
 * shell's identity/permissions source and is unaffected by this. */
export function getPortalIdentity(): Promise<PortalIdentity> {
	return api.get<PortalIdentity>("/api/v1/me");
}
