import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

/** Post-login landing route (#30) - Dashboard for anyone with ORGANISATION_MANAGE
 * (the principal/Org Admin audience the ticket describes), Students for everyone else.
 * Waits for permissionsLoaded so it doesn't flash one destination then redirect to the
 * other once /auth/me resolves. */
export function IndexRedirect() {
	const { hasPermission, permissionsLoaded } = useAuth();

	if (!permissionsLoaded) {
		return null;
	}

	return <Navigate to={hasPermission("ORGANISATION_MANAGE") ? "/dashboard" : "/students"} replace />;
}
