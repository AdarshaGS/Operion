import { useMemo, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Typography from "@mui/material/Typography";
import { PanelTitle } from "./PanelTitle";
import HistoryIcon from "@mui/icons-material/History";
import { ApiError } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { IMPORT_GROUP_DESCRIPTIONS, IMPORT_GROUP_LABELS, IMPORT_GROUP_ORDER } from "./imports/ImportConfig";
import { IMPORT_ENTITIES } from "./imports/importEntities";
import { ExportButton } from "./imports/ExportButton";
import { ImportCard } from "./imports/ImportCard";
import { ImportHistoryDialog } from "./imports/ImportHistoryDialog";

const TABS = ["import", "export"] as const;
type TabKey = (typeof TABS)[number];

/** Imports & exports settings section - a migration/onboarding center covering every
 * bulk-importable entity (see imports/importEntities.tsx), not just Students (#147's
 * original v1 scope). One generic ImportWorkflowDialog (validate -> preview -> confirm)
 * drives every card. (The setup guide card lives on the dashboard only.) */
export function ImportsExportsPanel() {
	const { isModuleEnabled } = useAuth();
	// An entity whose module is switched off for this organisation is not offered at all (the server refuses its endpoints too).
	const entities = useMemo(() => IMPORT_ENTITIES.filter((entity) => isModuleEnabled(entity.module)), [isModuleEnabled]);
	const [tab, setTab] = useState<TabKey>("import");
	const [historyOpen, setHistoryOpen] = useState(false);
	const [exportError, setExportError] = useState<string | null>(null);

	/** An export is an API call that can be refused (no view permission, network) - show why instead of an uncaught rejection. */
	async function runExport(label: string, exportData?: () => Promise<void>) {
		setExportError(null);
		try {
			await exportData?.();
		} catch (err) {
			setExportError(err instanceof ApiError && err.status === 403 ? `You don't have permission to export ${label.toLowerCase()}.` : err instanceof ApiError ? err.message : `Could not export ${label.toLowerCase()}.`);
		}
	}

	return (
		<Stack spacing={3}>
			<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: 2 }}>
				<Box>
					<PanelTitle>Imports & exports</PanelTitle>
					<Typography variant="body2" color="text.secondary">
						Import existing school data or export your data. Use templates to bulk upload and save time.
					</Typography>
				</Box>
				<Button variant="outlined" startIcon={<HistoryIcon />} onClick={() => setHistoryOpen(true)}>
					View import history
				</Button>
			</Stack>

			<Tabs value={tab} onChange={(_, value) => setTab(value)}>
				<Tab label="Import data" value="import" />
				<Tab label="Export data" value="export" />
			</Tabs>

			{entities.length === 0 && (
				<Alert severity="info">No import or export is available - the modules they belong to are switched off for this organisation.</Alert>
			)}

			{tab === "import" && (
				<Stack spacing={4}>
					{IMPORT_GROUP_ORDER.map((group) => {
						const groupEntities = entities.filter((entity) => entity.group === group);
						if (groupEntities.length === 0) return null;
						return (
							<Box key={group}>
								<Typography variant="h6">{IMPORT_GROUP_LABELS[group]}</Typography>
								<Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
									{IMPORT_GROUP_DESCRIPTIONS[group]}
								</Typography>
								<Grid container spacing={2}>
									{groupEntities.map((entity) => (
										<Grid key={entity.key} size={{ xs: 12, sm: 6, md: 4 }}>
											<ImportCard config={entity} />
										</Grid>
									))}
								</Grid>
							</Box>
						);
					})}
				</Stack>
			)}

			{tab === "export" && exportError && (
				<Alert severity="error" onClose={() => setExportError(null)}>
					{exportError}
				</Alert>
			)}

			{tab === "export" && (
				<Grid container spacing={2}>
					{entities.filter((entity) => entity.exportData).map((entity) => (
						<Grid key={entity.key} size={{ xs: 12, sm: 6, md: 4 }}>
							<ExportButton entity={entity} onExport={() => void runExport(entity.label, entity.exportData)} />
						</Grid>
					))}
				</Grid>
			)}

			<ImportHistoryDialog open={historyOpen} onClose={() => setHistoryOpen(false)} />
		</Stack>
	);
}
