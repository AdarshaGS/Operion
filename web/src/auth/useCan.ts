import { useAuth } from "./AuthContext";

/**
 * Boolean form of <Can> for the cases a wrapper can't express - disabling a button instead of
 * hiding it, or dropping a whole table column. Pass the same permission code(s) the backend
 * endpoint behind the control carries in @RequirePermission. UX sugar only: the backend still
 * enforces every permission independently.
 */
export function useCan(...anyOf: string[]): boolean {
	const { hasAnyPermission } = useAuth();
	return hasAnyPermission(anyOf);
}
