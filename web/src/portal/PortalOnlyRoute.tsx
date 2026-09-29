import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

/** Blocks an ADMIN session from the portal route tree - the mirror of AdminOnlyRoute. */
export function PortalOnlyRoute() {
	const { permissionsLoaded, portalIdentity } = useAuth();

	if (!permissionsLoaded) {
		return null;
	}
	if (portalIdentity?.sessionType !== "PORTAL") {
		return <Navigate to="/" replace />;
	}
	return <Outlet />;
}
