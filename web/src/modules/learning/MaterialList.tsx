import Chip from "@mui/material/Chip";
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { resolveAssetUrl } from "../../api/assets";
import type { MaterialResponse } from "../../api/learning";

/** The open/download affordance for one material, shared by the teacher table and the guardian list. */
export function MaterialAction({ material }: { material: MaterialResponse }) {
	if (material.kind === "FILE" && material.fileUrl) {
		return (
			<Link href={resolveAssetUrl(material.fileUrl)} target="_blank" rel="noreferrer">
				Download
			</Link>
		);
	}
	if (material.kind === "LINK" && material.url) {
		return (
			<Link href={material.url} target="_blank" rel="noreferrer">
				Open link
			</Link>
		);
	}
	return null;
}

export function MaterialSummary({ material }: { material: MaterialResponse }) {
	return (
		<Stack spacing={0.5}>
			<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
				<Typography variant="subtitle2">{material.title}</Typography>
				<Chip size="small" label={material.kind} variant="outlined" />
				{material.subjectName && <Chip size="small" label={material.subjectName} />}
			</Stack>
			{material.description && (
				<Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "pre-wrap" }}>
					{material.description}
				</Typography>
			)}
		</Stack>
	);
}
