import { useState, type ChangeEvent } from "react";
import Button from "@mui/material/Button";
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import AttachFileIcon from "@mui/icons-material/AttachFile";
import { resolveAssetUrl, uploadAsset } from "../../api/assets";
import { ApiError } from "../../api/client";

interface Props {
	label?: string;
	value: string | null;
	onChange: (reference: string | null) => void;
	onError: (message: string) => void;
}

/** Uploads through the shared /api/v1/assets endpoint (PNG/JPG/PDF, 5MB) and reports back the opaque reference. */
export function AttachmentField({ label = "Attach file", value, onChange, onError }: Props) {
	const [name, setName] = useState<string | null>(null);
	const [url, setUrl] = useState<string | null>(null);
	const [uploading, setUploading] = useState(false);

	async function handleFile(event: ChangeEvent<HTMLInputElement>) {
		const file = event.target.files?.[0];
		event.target.value = "";
		if (!file) {
			return;
		}
		setUploading(true);
		try {
			const uploaded = await uploadAsset(file);
			setName(file.name);
			setUrl(resolveAssetUrl(uploaded.url));
			onChange(uploaded.reference);
		} catch (err) {
			onError(err instanceof ApiError ? err.message : "Upload failed");
		} finally {
			setUploading(false);
		}
	}

	return (
		<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
			<Button component="label" size="small" variant="outlined" startIcon={<AttachFileIcon />} disabled={uploading}>
				{uploading ? "Uploading…" : label}
				<input hidden type="file" accept="image/png,image/jpeg,application/pdf" onChange={handleFile} />
			</Button>
			{value && (
				<>
					<Link href={url ?? undefined} target="_blank" rel="noreferrer" variant="body2">
						{name ?? "Attached"}
					</Link>
					<Button
						size="small"
						onClick={() => {
							setName(null);
							setUrl(null);
							onChange(null);
						}}
					>
						Remove
					</Button>
				</>
			)}
			{!value && (
				<Typography variant="caption" color="text.secondary">
					PNG, JPG or PDF, up to 5MB
				</Typography>
			)}
		</Stack>
	);
}
