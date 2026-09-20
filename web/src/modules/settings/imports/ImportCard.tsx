import { useState } from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import DownloadIcon from "@mui/icons-material/Download";
import UploadIcon from "@mui/icons-material/Upload";
import type { ImportEntityConfig } from "./ImportConfig";
import { downloadTemplate, ImportWorkflowDialog } from "./ImportWorkflowDialog";

export function ImportCard({ config, onImported }: { config: ImportEntityConfig; onImported?: () => void }) {
	const [open, setOpen] = useState(false);

	return (
		<>
			<Paper
				variant="outlined"
				sx={{ p: 2, display: "flex", alignItems: "flex-start", gap: 1.5, cursor: "pointer" }}
				onClick={() => setOpen(true)}
			>
				<Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", width: 40, height: 40, borderRadius: 1.5, flexShrink: 0 }}>
					{config.icon}
				</Box>
				<Box sx={{ flexGrow: 1, minWidth: 0 }}>
					<Typography variant="subtitle2">{config.label}</Typography>
					<Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 1 }}>
						{config.description}
					</Typography>
					<Stack direction="row" spacing={1} onClick={(event) => event.stopPropagation()}>
						<Button size="small" variant="text" startIcon={<DownloadIcon fontSize="small" />} onClick={() => downloadTemplate(config)}>
							Download template
						</Button>
						<Button size="small" variant="outlined" startIcon={<UploadIcon fontSize="small" />} onClick={() => setOpen(true)}>
							Import
						</Button>
					</Stack>
				</Box>
				<ChevronRightIcon fontSize="small" sx={{ color: "text.secondary", mt: 0.5 }} />
			</Paper>
			{open && <ImportWorkflowDialog open={open} onClose={() => setOpen(false)} config={config} onImported={onImported} />}
		</>
	);
}
