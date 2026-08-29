import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { getOrganisationBranding, type OrganisationBrandingResponse } from "../../api/organisationBranding";
import { ApiError } from "../../api/client";
import {
	getDocumentTemplate,
	updateDocumentTemplate,
	type DocumentType,
	type DocumentTemplateResponse,
	type TemplateStyle,
} from "../../api/documentTemplates";
import { Can } from "../../auth/Can";
import { useAuth } from "../../auth/AuthContext";
import { colors } from "../../theme";

const DOCUMENT_TYPES: { value: DocumentType; label: string }[] = [
	{ value: "QUESTION_PAPER_HEADER", label: "Question paper header" },
	{ value: "REPORT_CARD", label: "Report card" },
];

const TEMPLATE_STYLES: TemplateStyle[] = ["CLASSIC", "MODERN", "MINIMAL", "ELEGANT"];
const PAGE_SIZES = ["A4", "Letter"];

/** Per-style preview treatment - a design-time approximation, not the real print CSS
 * (there's no PDF/rendering dependency in this project yet, see #31's scope note). */
const STYLE_PREVIEW: Record<TemplateStyle, { border: string; headerBg: string; align: "left" | "center" }> = {
	CLASSIC: { border: `2px solid ${colors.ink}`, headerBg: "transparent", align: "center" },
	MODERN: { border: "none", headerBg: colors.accent, align: "left" },
	MINIMAL: { border: "none", headerBg: "transparent", align: "left" },
	ELEGANT: { border: `1px solid ${colors.inkFaint}`, headerBg: "transparent", align: "center" },
};

/** Letter Formats (#31) - a template picker for the branded header/footer frame that
 * question papers and report cards render inside. Live A4 preview only for this pass;
 * see the issue's note that no PDF/document-rendering dependency exists yet. */
export function LetterFormatsPanel() {
	const { hasAnyPermission } = useAuth();
	const canEdit = hasAnyPermission(["ORGANISATION_MANAGE"]);

	const [documentType, setDocumentType] = useState<DocumentType>("QUESTION_PAPER_HEADER");
	const [branding, setBranding] = useState<OrganisationBrandingResponse | null>(null);
	const [template, setTemplate] = useState<DocumentTemplateResponse | null>(null);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);

	useEffect(() => {
		getOrganisationBranding()
			.then(setBranding)
			.catch(() => {
				/* preview still works without branding assets */
			});
	}, []);

	useEffect(() => {
		setLoading(true);
		setSaved(false);
		setError(null);
		getDocumentTemplate(documentType)
			.then(setTemplate)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load template"))
			.finally(() => setLoading(false));
	}, [documentType]);

	function updateField<K extends keyof DocumentTemplateResponse>(key: K, value: DocumentTemplateResponse[K]) {
		setTemplate((prev) => (prev ? { ...prev, [key]: value } : prev));
	}

	async function handleSave() {
		if (!template) return;
		setSubmitting(true);
		setSaved(false);
		setError(null);
		try {
			const updated = await updateDocumentTemplate(documentType, {
				templateStyle: template.templateStyle,
				pageSize: template.pageSize,
				fontStyle: template.fontStyle,
				fontSize: template.fontSize,
				headerSubtext: template.headerSubtext || null,
			});
			setTemplate(updated);
			setSaved(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save template");
		} finally {
			setSubmitting(false);
		}
	}

	const preview = template ? STYLE_PREVIEW[template.templateStyle] : STYLE_PREVIEW.CLASSIC;
	const schoolName = branding?.schoolNameOverride || "Your Organisation";

	return (
		<Stack spacing={2}>
			<Typography variant="body2" color="text.secondary">
				Choose the branded header/footer frame that question papers and report cards render inside.
			</Typography>

			<Tabs value={documentType} onChange={(_e, value) => setDocumentType(value)}>
				{DOCUMENT_TYPES.map((dt) => (
					<Tab key={dt.value} value={dt.value} label={dt.label} />
				))}
			</Tabs>

			{error && <Alert severity="error">{error}</Alert>}
			{saved && <Alert severity="success">Template saved</Alert>}

			{!loading && template && (
				<Box sx={{ display: "flex", gap: 3, flexWrap: "wrap", alignItems: "flex-start" }}>
					<Paper component="form" onSubmit={(e) => { e.preventDefault(); handleSave(); }} sx={{ p: 3, flex: "1 1 320px", minWidth: 280 }}>
						<Stack spacing={2}>
							<TextField
								select
								label="Template style"
								value={template.templateStyle}
								onChange={(e) => updateField("templateStyle", e.target.value as TemplateStyle)}
								disabled={!canEdit}
								fullWidth
							>
								{TEMPLATE_STYLES.map((style) => (
									<MenuItem key={style} value={style}>
										{style.charAt(0) + style.slice(1).toLowerCase()}
									</MenuItem>
								))}
							</TextField>
							<TextField
								select
								label="Page size"
								value={template.pageSize}
								onChange={(e) => updateField("pageSize", e.target.value)}
								disabled={!canEdit}
								fullWidth
							>
								{PAGE_SIZES.map((size) => (
									<MenuItem key={size} value={size}>
										{size}
									</MenuItem>
								))}
							</TextField>
							<TextField
								label="Font style"
								value={template.fontStyle}
								onChange={(e) => updateField("fontStyle", e.target.value)}
								disabled={!canEdit}
								fullWidth
							/>
							<TextField
								label="Font size"
								type="number"
								value={template.fontSize}
								onChange={(e) => updateField("fontSize", Number(e.target.value))}
								disabled={!canEdit}
								fullWidth
							/>
							<TextField
								label="Header subtext"
								placeholder="e.g. Half-Yearly Examination 2026"
								value={template.headerSubtext ?? ""}
								onChange={(e) => updateField("headerSubtext", e.target.value)}
								disabled={!canEdit}
								fullWidth
							/>

							<Can anyOf={["ORGANISATION_MANAGE"]}>
								<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
									<Button type="submit" variant="contained" disabled={submitting}>
										{submitting ? "Saving..." : "Save"}
									</Button>
								</Box>
							</Can>
						</Stack>
					</Paper>

					<Box
						sx={{
							position: "relative",
							width: 380,
							aspectRatio: "210 / 297",
							bgcolor: "background.paper",
							boxShadow: 3,
							border: preview.border,
							p: 2.5,
							fontFamily: template.fontStyle || "inherit",
							fontSize: `${Math.max(template.fontSize - 4, 8)}px`,
							overflow: "hidden",
						}}
					>
						<Box
							sx={{
								textAlign: preview.align,
								bgcolor: preview.headerBg,
								color: preview.headerBg === "transparent" ? "inherit" : colors.accentInk,
								p: preview.headerBg === "transparent" ? 0 : 1.5,
								mb: 2,
							}}
						>
							{branding?.logoUrl && (
								<Box
									component="img"
									src={branding.logoUrl}
									alt="Logo"
									sx={{ height: 32, mb: 1, display: "inline-block" }}
								/>
							)}
							<Typography sx={{ fontWeight: 700, fontSize: `${template.fontSize}px`, fontFamily: "inherit" }}>
								{schoolName}
							</Typography>
							{branding?.addressLine && <Typography sx={{ fontSize: "0.85em" }}>{branding.addressLine}</Typography>}
							{branding?.affiliationText && <Typography sx={{ fontSize: "0.8em" }}>{branding.affiliationText}</Typography>}
							{template.headerSubtext && (
								<Typography sx={{ fontSize: "0.9em", fontWeight: 600, mt: 0.5 }}>{template.headerSubtext}</Typography>
							)}
						</Box>
						<Stack spacing={1}>
							{Array.from({ length: 8 }).map((_, i) => (
								<Box key={i} sx={{ height: 6, bgcolor: colors.rule, borderRadius: 0.5, width: i % 3 === 2 ? "60%" : "100%" }} />
							))}
						</Stack>
						{branding?.footerText && (
							<Typography sx={{ position: "absolute", bottom: 12, fontSize: "0.75em", color: "text.secondary" }}>
								{branding.footerText}
							</Typography>
						)}
					</Box>
				</Box>
			)}
		</Stack>
	);
}
