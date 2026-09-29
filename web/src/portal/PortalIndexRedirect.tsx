import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { PortalSelectPage } from "./PortalSelectPage";

const PATH_BY_TYPE: Record<string, string> = {
	GUARDIAN: "/portal/guardian",
	TEACHER: "/portal/teacher",
};

/** Landing route for a PORTAL session (/portal) - a single context goes straight to its
 * dashboard, more than one shows a switcher (a person can hold both Guardian and Teacher
 * portal roles at once, see PortalIdentityService), and zero shows a plain explainer
 * rather than a blank/broken page. */
export function PortalIndexRedirect() {
	const { portalIdentity } = useAuth();
	const contexts = portalIdentity?.portalContexts ?? [];

	if (contexts.length === 1) {
		return <Navigate to={PATH_BY_TYPE[contexts[0].type]} replace />;
	}
	return <PortalSelectPage />;
}
