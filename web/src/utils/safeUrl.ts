/** Schemes a user-supplied link may use; everything else (javascript:, data:, vbscript:, file:, blob:...) is refused. */
const WEB_SCHEMES = new Set(["http:", "https:"]);

export interface SafeHrefOptions {
	/** Also allow mailto: links - for fields that hold a contact address, not a web resource. */
	allowMailto?: boolean;
}

/**
 * Returns a URL that is safe to put in an `<a href>` / `window.open`, or null if `url` isn't one.
 * Only http(s) (and mailto: when asked) pass; the check runs on the URL as the browser will parse it,
 * so leading whitespace, mixed case and embedded tab/newline tricks ("  JaVa\tScript:...") can't
 * smuggle a script scheme through. Scheme-less / relative values resolve against the current origin
 * and so come back as http(s) links to this site.
 *
 * Callers should render plain text when this returns null rather than an unsafe or dead link.
 */
export function safeHref(url: string | null | undefined, options: SafeHrefOptions = {}): string | null {
	if (!url) return null;
	const trimmed = url.trim();
	if (!trimmed) return null;
	let parsed: URL;
	try {
		parsed = new URL(trimmed, window.location.origin);
	} catch {
		return null;
	}
	if (WEB_SCHEMES.has(parsed.protocol) || (options.allowMailto && parsed.protocol === "mailto:")) {
		return parsed.href;
	}
	return null;
}
