import { ApiError } from "../../api/client";

/** A 403 on a read means the caller lacks access - say so in plain words instead of the raw permission code, and never let it read as "nothing here yet". */
export function loadErrorMessage(err: unknown, fallback: string): string {
	if (err instanceof ApiError && err.status === 403) return "You don't have access to this section.";
	return err instanceof ApiError ? err.message : fallback;
}
