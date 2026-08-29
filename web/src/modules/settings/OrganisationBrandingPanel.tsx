import { useEffect, useState, type FormEvent } from "react";
import CloudUploadIcon from "@mui/icons-material/CloudUpload";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { resolveAssetUrl, uploadAsset } from "../../api/assets";
import { ApiError } from "../../api/client";
import { getOrganisationBranding, updateOrganisationBranding } from "../../api/organisationBranding";
import { Can } from "../../auth/Can";
import { useAuth } from "../../auth/AuthContext";
import { colors } from "../../theme";

type AssetSlot = "logo" | "stamp" | "signature";

interface UploadSlotProps {
	label: string;
	url: string | null;
	uploading: boolean;
	disabled: boolean;
	onSelect: (file: File) => void;
}

/** One of the three identical upload boxes (logo/stamp/signature) - kept local since
 * nothing outside this panel needs it. */
function UploadSlot({ label, url, uploading, disabled, onSelect }: UploadSlotProps) {
	return (
		<Box sx={{ flex: 1 }}>
			<Typography variant="caption" sx={{ display: "block", mb: 0.5, fontWeight: 700, letterSpacing: "0.06em", color: colors.inkFaint }}>
				{label.toUpperCase()}
			</Typography>
			<Button
				component="label"
				disabled={disabled || uploading}
				sx={{
					width: "100%",
					height: 120,
					border: "1px dashed",
					borderColor: "divider",
					borderRadius: 1,
					display: "flex",
					flexDirection: "column",
					gap: 1,
					textTransform: "none",
				}}
			>
				{uploading ? (
					<CircularProgress size={24} />
				) : url ? (
					<Box component="img" src={resolveAssetUrl(url)} alt={label} sx={{ maxHeight: 80, maxWidth: "100%", objectFit: "contain" }} />
				) : (
					<>
						<CloudUploadIcon color="disabled" />
						<Typography variant="caption" color="text.secondary">
							Click to upload
						</Typography>
					</>
				)}
				<input
					type="file"
					hidden
					accept="image/png,image/jpeg"
					onChange={(event) => {
						const file = event.target.files?.[0];
						if (file) {
							onSelect(file);
						}
						event.target.value = "";
					}}
				/>
			</Button>
			<Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.5 }}>
				PNG or JPG, up to 5MB
			</Typography>
		</Box>
	);
}

/** School Branding (GitHub #27, Phase A) - logo/stamp/signature plus the text that
 * appears on every printed/branded surface (receipts, letterheads, ID cards once those
 * land in Phase C). Distinct from Organisation Profile's plain-URL logo field - these
 * assets go through real upload (#25) and are meant for print-quality output. */
export function OrganisationBrandingPanel() {
	const { hasAnyPermission } = useAuth();
	const canEdit = hasAnyPermission(["ORGANISATION_MANAGE"]);

	const [logoRef, setLogoRef] = useState<string | null>(null);
	const [logoUrl, setLogoUrl] = useState<string | null>(null);
	const [stampRef, setStampRef] = useState<string | null>(null);
	const [stampUrl, setStampUrl] = useState<string | null>(null);
	const [signatureRef, setSignatureRef] = useState<string | null>(null);
	const [signatureUrl, setSignatureUrl] = useState<string | null>(null);
	const [schoolNameOverride, setSchoolNameOverride] = useState("");
	const [addressLine, setAddressLine] = useState("");
	const [affiliationText, setAffiliationText] = useState("");
	const [footerText, setFooterText] = useState("");
	const [admissionNumberFormat, setAdmissionNumberFormat] = useState("");
	const [invoiceNumberFormat, setInvoiceNumberFormat] = useState("");
	const [receiptNumberFormat, setReceiptNumberFormat] = useState("");

	const [uploadingSlot, setUploadingSlot] = useState<AssetSlot | null>(null);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);

	useEffect(() => {
		getOrganisationBranding()
			.then((branding) => {
				setLogoRef(branding.logoRef);
				setLogoUrl(branding.logoUrl);
				setStampRef(branding.stampRef);
				setStampUrl(branding.stampUrl);
				setSignatureRef(branding.signatureRef);
				setSignatureUrl(branding.signatureUrl);
				setSchoolNameOverride(branding.schoolNameOverride ?? "");
				setAddressLine(branding.addressLine ?? "");
				setAffiliationText(branding.affiliationText ?? "");
				setFooterText(branding.footerText ?? "");
				setAdmissionNumberFormat(branding.admissionNumberFormat);
				setInvoiceNumberFormat(branding.invoiceNumberFormat);
				setReceiptNumberFormat(branding.receiptNumberFormat);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load branding"))
			.finally(() => setLoading(false));
	}, []);

	async function handleUpload(slot: AssetSlot, file: File) {
		setUploadingSlot(slot);
		setError(null);
		try {
			const { reference, url } = await uploadAsset(file);
			if (slot === "logo") {
				setLogoRef(reference);
				setLogoUrl(url);
			} else if (slot === "stamp") {
				setStampRef(reference);
				setStampUrl(url);
			} else {
				setSignatureRef(reference);
				setSignatureUrl(url);
			}
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to upload image");
		} finally {
			setUploadingSlot(null);
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setSaved(false);
		setError(null);
		try {
			const branding = await updateOrganisationBranding({
				logoRef,
				stampRef,
				signatureRef,
				schoolNameOverride: schoolNameOverride || null,
				addressLine: addressLine || null,
				affiliationText: affiliationText || null,
				footerText: footerText || null,
				admissionNumberFormat,
				invoiceNumberFormat,
				receiptNumberFormat,
			});
			setLogoRef(branding.logoRef);
			setLogoUrl(branding.logoUrl);
			setStampRef(branding.stampRef);
			setStampUrl(branding.stampUrl);
			setSignatureRef(branding.signatureRef);
			setSignatureUrl(branding.signatureUrl);
			setAdmissionNumberFormat(branding.admissionNumberFormat);
			setInvoiceNumberFormat(branding.invoiceNumberFormat);
			setReceiptNumberFormat(branding.receiptNumberFormat);
			setSaved(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update branding");
		} finally {
			setSubmitting(false);
		}
	}

	if (loading) {
		return null;
	}

	return (
		<Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Branding &amp; documents</Typography>
				<Typography variant="body2" color="text.secondary">
					These assets and formats appear on fee receipts, certificates, and exports.
				</Typography>

				{error && <Alert severity="error">{error}</Alert>}
				{saved && <Alert severity="success">Branding updated</Alert>}

				<Box sx={{ display: "flex", gap: 2 }}>
					<UploadSlot
						label="School logo"
						url={logoUrl}
						uploading={uploadingSlot === "logo"}
						disabled={!canEdit}
						onSelect={(file) => handleUpload("logo", file)}
					/>
					<UploadSlot
						label="Official stamp"
						url={stampUrl}
						uploading={uploadingSlot === "stamp"}
						disabled={!canEdit}
						onSelect={(file) => handleUpload("stamp", file)}
					/>
					<UploadSlot
						label="Authorized signature"
						url={signatureUrl}
						uploading={uploadingSlot === "signature"}
						disabled={!canEdit}
						onSelect={(file) => handleUpload("signature", file)}
					/>
				</Box>

				<TextField
					label="School name override"
					helperText="Shown on printed documents instead of the organisation name, if set"
					value={schoolNameOverride}
					onChange={(e) => setSchoolNameOverride(e.target.value)}
					disabled={!canEdit}
					fullWidth
				/>
				<TextField label="Address line" value={addressLine} onChange={(e) => setAddressLine(e.target.value)} disabled={!canEdit} fullWidth />
				<TextField
					label="Affiliation text"
					placeholder="Affiliated to XYZ Board"
					value={affiliationText}
					onChange={(e) => setAffiliationText(e.target.value)}
					disabled={!canEdit}
					fullWidth
				/>
				<TextField
					label="Document footer"
					helperText="Printed at the bottom of receipts, invoices, and report cards"
					value={footerText}
					onChange={(e) => setFooterText(e.target.value)}
					disabled={!canEdit}
					fullWidth
					multiline
					minRows={2}
				/>

				<Typography variant="subtitle2" sx={{ pt: 1 }}>
					Numbering formats
				</Typography>
				<Typography variant="body2" color="text.secondary">
					Tokens: <code>{"{SEQ}"}</code> or <code>{"{SEQ:4}"}</code> for a zero-padded sequence, <code>{"{AY}"}</code> for the
					academic year, <code>{"{YYYY}"}</code> for the calendar year.
				</Typography>
				<TextField
					label="Admission number format"
					value={admissionNumberFormat}
					onChange={(e) => setAdmissionNumberFormat(e.target.value)}
					disabled={!canEdit}
					required
					fullWidth
				/>
				<TextField
					label="Invoice number format"
					value={invoiceNumberFormat}
					onChange={(e) => setInvoiceNumberFormat(e.target.value)}
					disabled={!canEdit}
					required
					fullWidth
				/>
				<TextField
					label="Receipt number format"
					value={receiptNumberFormat}
					onChange={(e) => setReceiptNumberFormat(e.target.value)}
					disabled={!canEdit}
					required
					fullWidth
				/>

				<Can anyOf={["ORGANISATION_MANAGE"]}>
					<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
						<Button type="submit" variant="contained" disabled={submitting || uploadingSlot !== null}>
							{submitting ? "Saving..." : "Save"}
						</Button>
					</Box>
				</Can>
			</Stack>
		</Paper>
	);
}
