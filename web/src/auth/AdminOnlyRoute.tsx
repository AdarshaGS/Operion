import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "./AuthContext";

/** Blocks a PORTAL session from the admin shell (#210) - the mirror of PortalOnlyRoute
 * in web/src/portal. Nests inside ProtectedRoute, so isAuthenticated is already true. */
export function AdminOnlyRoute() {
	const { permissionsLoaded, portalIdentity } = useAuth();

	if (!permissionsLoaded) {
		return null;
	}
	if (portalIdentity?.sessionType === "PORTAL") {
		return <Navigate to="/portal" replace />;
	}
	return <Outlet />;
}
