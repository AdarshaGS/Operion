import { useEffect, useState } from "react";

/** Removes one query parameter from the address bar without a navigation or reload, keeping the
 * router's own history state intact. No-op when the parameter isn't there. */
export function removeQueryParam(name: string): void {
	const url = new URL(window.location.href);
	if (!url.searchParams.has(name)) return;
	url.searchParams.delete(name);
	window.history.replaceState(window.history.state, "", `${url.pathname}${url.search}${url.hash}`);
}

/**
 * For pages opened from a one-time link (invite claim, password reset, email verification): reads
 * the token from the URL once, on first render, then immediately strips it from the address bar
 * with history.replaceState so it doesn't linger in the visible URL, browser history entries,
 * screenshots or copy-pasted links - including after a failed submit. The value lives on in the
 * returned string, which the page seeds its own form state with. Pair with the no-referrer policy
 * in index.html so the token can't leak through a Referer header in the meantime.
 */
export function useUrlToken(param = "token"): string {
	const [token] = useState(() => new URLSearchParams(window.location.search).get(param) ?? "");
	useEffect(() => {
		removeQueryParam(param);
	}, [param]);
	return token;
}
