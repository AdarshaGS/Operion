// Cross-tab session sync. localStorage is shared by every tab of the app, and the browser
// fires a `storage` event in each *other* tab when one of them writes it - so a logout (or a
// token removal / new login) in one tab can be mirrored by the rest instead of leaving them
// "signed in" with a token that no longer exists.

/**
 * Calls `onChange` whenever `key` is changed, removed, or the whole storage is cleared by
 * another tab. Returns an unsubscribe function. The event never fires for the tab that made
 * the change, so local logouts must still update local state directly.
 */
export function subscribeToStorageKey(key: string, onChange: () => void): () => void {
	function handle(event: StorageEvent) {
		// key === null means storage.clear() was called in the other tab.
		if (event.key !== null && event.key !== key) return;
		onChange();
	}
	window.addEventListener("storage", handle);
	return () => window.removeEventListener("storage", handle);
}
