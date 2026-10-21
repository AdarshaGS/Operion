import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../api/client";
import { CERTIFICATE_STATUS_LABELS } from "../api/certificates";
import {
	downloadChildCertificate,
	getChildCertificates,
	getRequestableCertificateTypes,
	requestChildCertificate,
	type GuardianCertificate,
} from "../api/portal";

const GUARDIAN_STATUS_LABELS = { ...CERTIFICATE_STATUS_LABELS, APPROVED: "Being prepared" };

/** A child's certificates in the guardian portal: request the types the school allows, download issued ones. */
export function GuardianCertificatesSection({ studentId }: { studentId: number }) {
	const [certificates, setCertificates] = useState<GuardianCertificate[]>([]);
	const [types, setTypes] = useState<{ id: number; name: string }[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [open, setOpen] = useState(false);
	const [typeId, setTypeId] = useState("");
	const [purpose, setPurpose] = useState("");
	const [formError, setFormError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		getChildCertificates(studentId)
			.then(setCertificates)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load certificates"));
	}

	useEffect(refresh, [studentId]);
	useEffect(() => {
		getRequestableCertificateTypes().then(setTypes).catch(() => {});
	}, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setFormError(null);
		try {
			await requestChildCertificate(studentId, Number(typeId), purpose || null);
			setOpen(false);
			setTypeId("");
			setPurpose("");
			refresh();
		} catch (err) {
			setFormError(err instanceof ApiError ? err.message : "Failed to send the request");
		} finally {
			setSubmitting(false);
		}
	}

	if (types.length === 0 && certificates.length === 0) return null;

	return (
		<Paper sx={{ p: 2 }} data-testid="guardian-certificates">
			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 1 }}>
				<Typography variant="subtitle1">Certificates</Typography>
				{types.length > 0 && (
					<Button size="small" onClick={() => setOpen(true)}>
						Request a certificate
					</Button>
				)}
			</Box>
			{error && <Alert severity="error">{error}</Alert>}
			{certificates.length === 0 ? (
				<Typography variant="body2" color="text.secondary">
					No certificates yet.
				</Typography>
			) : (
				<Table size="small">
					<TableHead>
						<TableRow>
							<TableCell>Certificate</TableCell>
							<TableCell>Number</TableCell>
							<TableCell>Status</TableCell>
							<TableCell />
						</TableRow>
					</TableHead>
					<TableBody>
						{certificates.map((certificate) => (
							<TableRow key={certificate.id}>
								<TableCell>
									{certificate.certificateName}
									{certificate.purpose && (
										<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
											{certificate.purpose}
										</Typography>
									)}
								</TableCell>
								<TableCell>{certificate.certificateNumber ?? "—"}</TableCell>
								<TableCell>
									<Chip size="small" label={GUARDIAN_STATUS_LABELS[certificate.status]} />
									{certificate.rejectionReason && (
										<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
											{certificate.rejectionReason}
										</Typography>
									)}
								</TableCell>
								<TableCell>
									{certificate.status === "ISSUED" && (
										<Button
											size="small"
											onClick={() =>
												downloadChildCertificate(studentId, certificate.id, certificate.certificateNumber).catch((err) =>
													setError(err instanceof ApiError ? err.message : "Failed to download"),
												)
											}
										>
											Download
										</Button>
									)}
								</TableCell>
							</TableRow>
						))}
					</TableBody>
				</Table>
			)}

			<Dialog open={open} onClose={() => setOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Request a certificate</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{formError && <Alert severity="error">{formError}</Alert>}
						<TextField select label="Certificate" value={typeId} onChange={(e) => setTypeId(e.target.value)} required fullWidth>
							{types.map((type) => (
								<MenuItem key={type.id} value={type.id}>
									{type.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Purpose"
							helperText="e.g. passport application, bank account"
							value={purpose}
							onChange={(e) => setPurpose(e.target.value)}
							required
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !typeId || !purpose.trim()}>
						Send request
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
