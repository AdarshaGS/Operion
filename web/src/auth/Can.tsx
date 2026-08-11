import type { ReactNode } from "react";
import { useAuth } from "./AuthContext";

interface CanProps {
	/** Rendered if the caller holds ANY of these permission codes. */
	anyOf: string[];
	children: ReactNode;
}

/** Gates its children behind the caller's resolved permission set from /auth/me. UX sugar only - the backend still enforces every permission independently via PermissionInterceptor. */
export function Can({ anyOf, children }: CanProps) {
	const { hasAnyPermission } = useAuth();
	if (!hasAnyPermission(anyOf)) {
		return null;
	}
	return <>{children}</>;
}
