import { useEffect, useMemo, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { Can } from "../../auth/Can";
import { useAuth } from "../../auth/AuthContext";
import { ApiError } from "../../api/client";
import {
	listCertificateTypes,
	listCertificates,
	raiseCertificate,
	type CertificateRequestResponse,
	type CertificateTypeResponse,
} from "../../api/certificates";
import { CertificatesTable } from "./CertificatesTable";

const VIEW_PERMISSIONS = ["CERTIFICATE_VIEW", "CERTIFICATE_REQUEST", "CERTIFICATE_APPROVE", "CERTIFICATE_ISSUE"];

/** A student's certificates (TC, bonafide, ...) with request, approve, issue and print in place. */
export function StudentCertificatesPanel({ studentId }: { studentId: number }) {
	const { hasAnyPermission } = useAuth();
	const canView = hasAnyPermission(VIEW_PERMISSIONS);
	const [requests, setRequests] = useState<CertificateRequestResponse[]>([]);
	const [types, setTypes] = useState<CertificateTypeResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [open, setOpen] = useState(false);
	const [typeId, setTypeId] = useState("");
	const [purpose, setPurpose] = useState("");
	const [manualValues, setManualValues] = useState<Record<string, string>>({});
	const [submitting, setSubmitting] = useState(false);
	const [formError, setFormError] = useState<string | null>(null);

	function refresh() {
		if (!canView) return;
		listCertificates({ studentId })
			.then(setRequests)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load certificates"));
	}

	useEffect(refresh, [studentId, canView]);
	useEffect(() => {
		if (canView) listCertificateTypes().then(setTypes).catch(() => {});
	}, [canView]);

	const selectedType = useMemo(() => types.find((type) => String(type.id) === typeId), [types, typeId]);

	if (!canView) return null;

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setFormError(null);
		setSubmitting(true);
		try {
			await raiseCertificate({ studentId, certificateTypeId: Number(typeId), purpose: purpose || null, manualValues });
			setOpen(false);
			setTypeId("");
			setPurpose("");
			setManualValues({});
			refresh();
		} catch (err) {
			setFormError(err instanceof ApiError ? err.message : "Failed to request certificate");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }} data-testid="student-certificates">
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Certificates</Typography>
					<Can anyOf={["CERTIFICATE_REQUEST"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
							Request certificate
						</Button>
					</Can>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}
				{requests.length === 0 && !error && <Alert severity="info">No certificates requested yet.</Alert>}
				{requests.length > 0 && <CertificatesTable requests={requests} onChanged={refresh} />}
			</Stack>

			<Dialog open={open} onClose={() => setOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Request certificate</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{formError && <Alert severity="error">{formError}</Alert>}
						<TextField select label="Certificate" value={typeId} onChange={(e) => setTypeId(e.target.value)} required fullWidth>
							{types
								.filter((type) => type.active)
								.map((type) => (
									<MenuItem key={type.id} value={type.id}>
										{type.name}
									</MenuItem>
								))}
						</TextField>
						{selectedType && (
							<Typography variant="caption" color="text.secondary">
								{[
									selectedType.requiresExit && "Needs the student's exit recorded",
									selectedType.approvalRequired && "Needs approval",
									selectedType.requiresClearance && "Checks dues before issue",
								]
									.filter(Boolean)
									.join(" · ") || "Can be issued straight away"}
							</Typography>
						)}
						<TextField label="Purpose" value={purpose} onChange={(e) => setPurpose(e.target.value)} fullWidth />
						{selectedType?.manualFieldList.map((field) => (
							<TextField
								key={field.key}
								label={field.label}
								value={manualValues[field.key] ?? ""}
								onChange={(e) => setManualValues((prev) => ({ ...prev, [field.key]: e.target.value }))}
								fullWidth
							/>
						))}
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !typeId}>
						Request
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
