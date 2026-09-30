import type { MouseEvent } from "react";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import { resolveAssetUrl } from "../../api/assets";
import type { SheetAnnotation } from "../../api/learning";

interface Props {
	url: string;
	annotations: SheetAnnotation[];
	/** When set, clicking the page reports a fractional position; clicking a marker reports its index. */
	onPlace?: (x: number, y: number) => void;
	onMarkerClick?: (index: number) => void;
}

const GLYPH: Record<SheetAnnotation["kind"], { text: string; color: string }> = {
	TICK: { text: "✓", color: "success.main" },
	CROSS: { text: "✗", color: "error.main" },
	MARKS: { text: "", color: "primary.main" },
	COMMENT: { text: "💬", color: "warning.main" },
};

/** A scanned page with annotation markers overlaid at fractional positions, so they line up at any render size.
 * PDF pages can't be overlaid (no raster to position against) and fall back to a link. */
export function AnnotatedPage({ url, annotations, onPlace, onMarkerClick }: Props) {
	const resolved = resolveAssetUrl(url);
	if (resolved.toLowerCase().split("?")[0].endsWith(".pdf")) {
		return (
			<Typography variant="body2">
				PDF page - <Link href={resolved} target="_blank" rel="noreferrer">open</Link> (on-screen marks apply to image scans)
			</Typography>
		);
	}

	function handleClick(event: MouseEvent<HTMLDivElement>) {
		if (!onPlace) {
			return;
		}
		const rect = event.currentTarget.getBoundingClientRect();
		onPlace((event.clientX - rect.left) / rect.width, (event.clientY - rect.top) / rect.height);
	}

	return (
		<Box sx={{ position: "relative", display: "inline-block", maxWidth: "100%", cursor: onPlace ? "crosshair" : "default", lineHeight: 0 }} onClick={handleClick}>
			<Box component="img" src={resolved} alt="Answer sheet page" sx={{ maxWidth: "100%", display: "block" }} />
			{annotations.map((a, i) => {
				const glyph = GLYPH[a.kind];
				return (
					<Box
						key={i}
						title={a.label ?? undefined}
						onClick={(event) => {
							if (onMarkerClick) {
								event.stopPropagation();
								onMarkerClick(i);
							}
						}}
						sx={{
							position: "absolute",
							left: `${a.x * 100}%`,
							top: `${a.y * 100}%`,
							transform: "translate(-50%, -50%)",
							color: glyph.color,
							fontWeight: 700,
							fontSize: "1.4rem",
							lineHeight: 1,
							textShadow: "0 0 3px #fff",
							cursor: onMarkerClick ? "pointer" : "inherit",
						}}
					>
						{a.kind === "MARKS" ? `+${a.marks}` : glyph.text}
						{a.kind === "COMMENT" && a.label && (
							<Typography component="span" variant="caption" sx={{ ml: 0.5, bgcolor: "rgba(255,255,255,0.85)", color: "text.primary", px: 0.5, borderRadius: 0.5 }}>
								{a.label}
							</Typography>
						)}
					</Box>
				);
			})}
		</Box>
	);
}
