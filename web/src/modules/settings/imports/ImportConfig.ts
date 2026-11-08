import type { ReactNode } from "react";
import type { ImportRowResult } from "../../../api/imports";

export type ImportGroupKey = "people" | "academics" | "finance" | "operations" | "library";

export interface ImportEntityConfig {
	key: string;
	label: string;
	description: string;
	group: ImportGroupKey;
	/** Backend module code (AppModule) that owns this entity's import/export endpoints; hidden when the module is switched off for the organisation. */
	module: string;
	icon: ReactNode;
	/** Column order the backend's RowImportService expects - keep in sync. */
	templateHeaders: string[];
	templateExampleRow: string[];
	templateFileName: string;
	/** false only for the original Students endpoint, which predates validateOnly and
	 * always imports immediately - see ImportWorkflowDialog. */
	supportsPreview: boolean;
	/** Backend permission codes (any-of) required by this entity's import endpoint - keep in sync with its controller's @RequirePermission. */
	importPermissions: string[];
	/** Backend permission codes (any-of) required by this entity's export endpoint (usually the module's *_VIEW code). */
	exportPermissions: string[];
	runImport: (file: File, validateOnly: boolean) => Promise<ImportRowResult[]>;
	/** Undefined until that entity's export endpoint is wired up. */
	exportData?: () => Promise<void>;
}

export const IMPORT_GROUP_LABELS: Record<ImportGroupKey, string> = {
	people: "People",
	academics: "Academics",
	finance: "Finance",
	operations: "Operations",
	library: "Library",
};

export const IMPORT_GROUP_DESCRIPTIONS: Record<ImportGroupKey, string> = {
	people: "Import staff, students and parents.",
	academics: "Import academic structure and related data.",
	finance: "Import fee structure and related data.",
	operations: "Import inventory, transport and other operational data.",
	library: "Import library books and members.",
};

export const IMPORT_GROUP_ORDER: ImportGroupKey[] = ["people", "academics", "finance", "operations", "library"];
