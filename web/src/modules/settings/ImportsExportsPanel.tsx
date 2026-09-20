import { useEffect, useState } from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Typography from "@mui/material/Typography";
import HistoryIcon from "@mui/icons-material/History";
import FileDownloadIcon from "@mui/icons-material/FileDownload";
import { dismissSetupProgress, getDashboardSummary, type SetupChecklist } from "../../api/dashboard";
import { SetupProgress } from "../dashboard/SetupProgress";
import { IMPORT_GROUP_DESCRIPTIONS, IMPORT_GROUP_LABELS, IMPORT_GROUP_ORDER } from "./imports/ImportConfig";
import { IMPORT_ENTITIES } from "./imports/importEntities";
import { ImportCard } from "./imports/ImportCard";
import { ImportHistoryDialog } from "./imports/ImportHistoryDialog";

const TABS = ["import", "export"] as const;
type TabKey = (typeof TABS)[number];

/** Imports & exports settings section - a migration/onboarding center covering every
 * bulk-importable entity (see imports/importEntities.tsx), not just Students (#147's
 * original v1 scope). One generic ImportWorkflowDialog (validate -> preview -> confirm)
 * drives every card; the onboarding card reuses the dashboard's own real setup-progress
 * signal rather than fabricating a separate one. */
export function ImportsExportsPanel() {
	const [tab, setTab] = useState<TabKey>("import");
	const [historyOpen, setHistoryOpen] = useState(false);
	const [checklist, setChecklist] = useState<SetupChecklist | null>(null);

	useEffect(() => {
		getDashboardSummary()
			.then((summary) => setChecklist(summary.setupChecklist))
			.catch(() => undefined);
	}, []);

	function handleDismissSetupProgress() {
		setChecklist(null);
		dismissSetupProgress().catch(() => undefined);
	}

	return (
		<Stack spacing={3}>
			<Stack direction="row" justifyContent="space-between" alignItems="flex-start" sx={{ flexWrap: "wrap", gap: 2 }}>
				<Box>
					<Typography variant="h5">Imports & exports</Typography>
					<Typography variant="body2" color="text.secondary">
						Import existing school data or export your data. Use templates to bulk upload and save time.
					</Typography>
				</Box>
				<Button variant="outlined" startIcon={<HistoryIcon />} onClick={() => setHistoryOpen(true)}>
					View import history
				</Button>
			</Stack>

			{checklist && <SetupProgress checklist={checklist} onDismiss={handleDismissSetupProgress} />}

			<Tabs value={tab} onChange={(_, value) => setTab(value)}>
				<Tab label="Import data" value="import" />
				<Tab label="Export data" value="export" />
			</Tabs>

			{tab === "import" && (
				<Stack spacing={4}>
					{IMPORT_GROUP_ORDER.map((group) => {
						const entities = IMPORT_ENTITIES.filter((entity) => entity.group === group);
						if (entities.length === 0) return null;
						return (
							<Box key={group}>
								<Typography variant="h6">{IMPORT_GROUP_LABELS[group]}</Typography>
								<Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
									{IMPORT_GROUP_DESCRIPTIONS[group]}
								</Typography>
								<Grid container spacing={2}>
									{entities.map((entity) => (
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

			{tab === "export" && (
				<Grid container spacing={2}>
					{IMPORT_ENTITIES.filter((entity) => entity.exportData).map((entity) => (
						<Grid key={entity.key} size={{ xs: 12, sm: 6, md: 4 }}>
							<Button
								fullWidth
								variant="outlined"
								startIcon={<FileDownloadIcon />}
								sx={{ justifyContent: "flex-start", py: 1.5 }}
								onClick={() => entity.exportData?.()}
							>
								{entity.label}
							</Button>
						</Grid>
					))}
				</Grid>
			)}

			<ImportHistoryDialog open={historyOpen} onClose={() => setHistoryOpen(false)} />
		</Stack>
	);
}
