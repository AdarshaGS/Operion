import Button from "@mui/material/Button";
import Tooltip from "@mui/material/Tooltip";
import FileDownloadIcon from "@mui/icons-material/FileDownload";
import { useCan } from "../../../auth/useCan";
import type { ImportEntityConfig } from "./ImportConfig";

/** Export button for one entity. Exports are gated by the module's view permission, so without it the button is disabled with an explanation instead of failing with a 403. */
export function ExportButton({ entity, onExport }: { entity: ImportEntityConfig; onExport: () => void }) {
	const canExport = useCan(...entity.exportPermissions);
	const button = (
		<Button
			fullWidth
			variant="outlined"
			startIcon={<FileDownloadIcon />}
			sx={{ justifyContent: "flex-start", py: 1.5 }}
			disabled={!canExport}
			onClick={onExport}
		>
			{entity.label}
		</Button>
	);
	if (canExport) return button;
	return (
		<Tooltip title="You don't have permission">
			<span style={{ display: "block" }}>{button}</span>
		</Tooltip>
	);
}
