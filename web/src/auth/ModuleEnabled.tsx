import type { ReactNode } from "react";
import { useAuth } from "./AuthContext";

interface ModuleEnabledProps {
	/** Module code as in the backend AppModule enum, e.g. "LIBRARY", "FEES". */
	module: string;
	children: ReactNode;
}

/** Renders its children only while the organisation has the module switched on. Use it around a card or panel that one module embeds inside another module's page, so a disabled module leaves no empty card or error behind. UX sugar only - the backend still refuses the module's API calls. */
export function ModuleEnabled({ module, children }: ModuleEnabledProps) {
	const { isModuleEnabled } = useAuth();
	if (!isModuleEnabled(module)) {
		return null;
	}
	return <>{children}</>;
}
