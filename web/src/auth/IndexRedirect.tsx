import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

/** Post-login landing route (#30, extended by #210) - a PORTAL session (Guardian/Teacher
 * portal role, see PortalIdentityService) lands on /portal; otherwise Dashboard for
 * anyone with ORGANISATION_MANAGE (the principal/Org Admin audience #30 describes),
 * Students for everyone else. Waits for permissionsLoaded so it doesn't flash one
 * destination then redirect to another once /auth/me and /api/v1/me resolve. */
export function IndexRedirect() {
	const { hasPermission, permissionsLoaded, portalIdentity, isPathEnabled } = useAuth();

	if (!permissionsLoaded) {
		return null;
	}
	if (portalIdentity?.sessionType === "PORTAL") {
		return <Navigate to="/portal" replace />;
	}

	// A module the platform owner switched off can't be the landing page - fall through to
	// the next one that is on, ending at Settings which is core and always available.
	const preferred = hasPermission("ORGANISATION_MANAGE") ? ["/dashboard", "/students"] : ["/students"];
	const destination = [...preferred, "/settings"].find((path) => isPathEnabled(path)) ?? "/settings";
	return <Navigate to={destination} replace />;
}
