import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

/** Post-login landing route (#30, extended by #210) - a PORTAL session (Guardian/Teacher
 * portal role, see PortalIdentityService) lands on /portal; otherwise Dashboard for
 * anyone with ORGANISATION_MANAGE (the principal/Org Admin audience #30 describes),
 * Students for everyone else. Waits for permissionsLoaded so it doesn't flash one
 * destination then redirect to another once /auth/me and /api/v1/me resolve. */
export function IndexRedirect() {
	const { hasPermission, permissionsLoaded, portalIdentity } = useAuth();

	if (!permissionsLoaded) {
		return null;
	}
	if (portalIdentity?.sessionType === "PORTAL") {
		return <Navigate to="/portal" replace />;
	}

	return <Navigate to={hasPermission("ORGANISATION_MANAGE") ? "/dashboard" : "/students"} replace />;
}
