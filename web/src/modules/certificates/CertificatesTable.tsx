import { useEffect, useState, type FormEvent } from "react";
import { Link as RouterLink } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import {
	CERTIFICATE_STATUS_LABELS,
	approveCertificate,
	cancelCertificate,
	downloadCertificate,
	getCertificateClearance,
	issueCertificate,
	rejectCertificate,
	updateCertificateDetails,
	type CertificateRequestResponse,
	type CertificateRequestStatus,
	type Clearance,
} from "../../api/certificates";

const STATUS_COLORS: Record<CertificateRequestStatus, "default" | "warning" | "info" | "success" | "error"> = {
	PENDING_APPROVAL: "warning",
	APPROVED: "info",
	REJECTED: "error",
	ISSUED: "success",
	CANCELLED: "default",
};

function message(err: unknown, fallback: string): string {
	return err instanceof ApiError ? err.message : fallback;
}

function formatDate(value: string | null): string {
	return value ? new Date(value).toLocaleDateString() : "—";
}

/** The certificate requests list with every workflow action - used on a student's page and on the register. */
export function CertificatesTable({
	requests,
	showStudent,
	onChanged,
}: {
	requests: CertificateRequestResponse[];
	showStudent?: boolean;
	onChanged: () => void;
}) {
	const [error, setError] = useState<string | null>(null);
	const [issuing, setIssuing] = useState<CertificateRequestResponse | null>(null);
	const [reasonFor, setReasonFor] = useState<{ request: CertificateRequestResponse; action: "reject" | "cancel" } | null>(null);

	async function run(work: () => Promise<unknown>, fallback: string) {
		setError(null);
		try {
			await work();
			onChanged();
		} catch (err) {
			setError(message(err, fallback));
		}
	}

	return (
		<>
			{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
			<TableContainer>
				<Table size="small">
					<TableHead>
						<TableRow>
							{showStudent && <TableCell>Student</TableCell>}
							<TableCell>Certificate</TableCell>
							<TableCell>Number</TableCell>
							<TableCell>Requested</TableCell>
							<TableCell>Status</TableCell>
							<TableCell align="right">Actions</TableCell>
						</TableRow>
					</TableHead>
					<TableBody>
						{requests.map((request) => (
							<TableRow key={request.id} data-testid={`certificate-row-${request.id}`}>
								{showStudent && (
									<TableCell>
										<Link component={RouterLink} to={`/students/${request.studentId}`}>
											{request.studentName}
										</Link>
										{request.admissionNumber && (
											<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
												{request.admissionNumber}
											</Typography>
										)}
									</TableCell>
								)}
								<TableCell>
									{request.certificateTypeName}
									{request.purpose && (
										<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
											{request.purpose}
										</Typography>
									)}
								</TableCell>
								<TableCell>{request.certificateNumber ?? "—"}</TableCell>
								<TableCell>
									{formatDate(request.requestedAt)}
									{request.requestedVia === "PORTAL" && <Chip label="Portal" size="small" sx={{ ml: 1 }} />}
								</TableCell>
								<TableCell>
									<Chip label={CERTIFICATE_STATUS_LABELS[request.status]} size="small" color={STATUS_COLORS[request.status]} />
									{request.rejectionReason && (
										<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
											{request.rejectionReason}
										</Typography>
									)}
									{request.cancelReason && (
										<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
											{request.cancelReason}
										</Typography>
									)}
									{request.clearanceOverrideReason && (
										<Typography variant="caption" color="warning.main" sx={{ display: "block" }}>
											Dues override: {request.clearanceOverrideReason}
										</Typography>
									)}
								</TableCell>
								<TableCell align="right">
									<Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
										{request.status === "PENDING_APPROVAL" && (
											<Can anyOf={["CERTIFICATE_APPROVE"]}>
												<Button size="small" onClick={() => run(() => approveCertificate(request.id), "Failed to approve")}>
													Approve
												</Button>
											</Can>
										)}
										{(request.status === "PENDING_APPROVAL" || request.status === "APPROVED") && (
											<Can anyOf={["CERTIFICATE_APPROVE"]}>
												<Button size="small" color="error" onClick={() => setReasonFor({ request, action: "reject" })}>
													Reject
												</Button>
											</Can>
										)}
										{request.status === "APPROVED" && (
											<Can anyOf={["CERTIFICATE_ISSUE"]}>
												<Button size="small" variant="contained" onClick={() => setIssuing(request)}>
													Issue
												</Button>
											</Can>
										)}
										{(request.status === "ISSUED" || request.status === "CANCELLED") && (
											<Can anyOf={["CERTIFICATE_ISSUE"]}>
												<Button
													size="small"
													onClick={() =>
														run(() => downloadCertificate(request.id, request.certificateNumber), "Failed to download certificate")
													}
												>
													{request.printCount > 0 ? "Reprint" : "Print"}
												</Button>
											</Can>
										)}
										{request.status === "ISSUED" && (
											<Can anyOf={["CERTIFICATE_CANCEL"]}>
												<Button size="small" color="error" onClick={() => setReasonFor({ request, action: "cancel" })}>
													Cancel
												</Button>
											</Can>
										)}
									</Stack>
								</TableCell>
							</TableRow>
						))}
					</TableBody>
				</Table>
			</TableContainer>

			{issuing && (
				<IssueCertificateDialog
					request={issuing}
					onClose={() => setIssuing(null)}
					onIssued={() => {
						setIssuing(null);
						onChanged();
					}}
				/>
			)}
			{reasonFor && (
				<ReasonDialog
					title={reasonFor.action === "reject" ? "Reject certificate request" : "Cancel issued certificate"}
					hint={
						reasonFor.action === "cancel"
							? "The certificate's verification page will show it as cancelled. A new one can then be requested."
							: undefined
					}
					confirmLabel={reasonFor.action === "reject" ? "Reject" : "Cancel certificate"}
					onClose={() => setReasonFor(null)}
					onConfirm={async (reason) => {
						if (reasonFor.action === "reject") {
							await rejectCertificate(reasonFor.request.id, reason);
						} else {
							await cancelCertificate(reasonFor.request.id, reason);
						}
						setReasonFor(null);
						onChanged();
					}}
				/>
			)}
		</>
	);
}

function ReasonDialog({
	title,
	hint,
	confirmLabel,
	onClose,
	onConfirm,
}: {
	title: string;
	hint?: string;
	confirmLabel: string;
	onClose: () => void;
	onConfirm: (reason: string) => Promise<void>;
}) {
	const [reason, setReason] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setError(null);
		try {
			await onConfirm(reason.trim());
		} catch (err) {
			setError(message(err, "Action failed"));
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Dialog open onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
			<DialogTitle>{title}</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					{hint && <Typography variant="body2">{hint}</Typography>}
					<TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required multiline rows={2} fullWidth />
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Back</Button>
				<Button type="submit" color="error" variant="contained" disabled={submitting || !reason.trim()}>
					{confirmLabel}
				</Button>
			</DialogActions>
		</Dialog>
	);
}

/** Shows dues clearance, lets staff fill in the per-request fields, and issues - with an override reason when dues remain. */
function IssueCertificateDialog({
	request,
	onClose,
	onIssued,
}: {
	request: CertificateRequestResponse;
	onClose: () => void;
	onIssued: () => void;
}) {
	const [clearance, setClearance] = useState<Clearance | null>(null);
	const [manualValues, setManualValues] = useState<Record<string, string>>(request.manualValues ?? {});
	const [overrideReason, setOverrideReason] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		getCertificateClearance(request.id)
			.then(setClearance)
			.catch((err) => setError(message(err, "Failed to check dues")));
	}, [request.id]);

	const needsOverride = request.requiresClearance && clearance !== null && !clearance.clear;

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setError(null);
		try {
			if (request.manualFields.length > 0) {
				await updateCertificateDetails(request.id, { purpose: request.purpose, manualValues });
			}
			const issued = await issueCertificate(request.id, needsOverride ? overrideReason.trim() : null);
			await downloadCertificate(issued.id, issued.certificateNumber);
			onIssued();
		} catch (err) {
			setError(message(err, "Failed to issue certificate"));
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Dialog open onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
			<DialogTitle>
				Issue {request.certificateTypeName} — {request.studentName}
			</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					{clearance && (
						<Stack spacing={1} data-testid="certificate-clearance">
							<Typography variant="subtitle2">Dues clearance</Typography>
							{clearance.items.map((item) => (
								<Stack key={item.code} direction="row" spacing={1} sx={{ alignItems: "center" }}>
									<Chip size="small" label={item.clear ? "Clear" : "Pending"} color={item.clear ? "success" : "warning"} />
									<Typography variant="body2">
										{item.label}: {item.detail}
									</Typography>
								</Stack>
							))}
							{!request.requiresClearance && !clearance.clear && (
								<Typography variant="caption" color="text.secondary">
									This certificate type does not require clearance, so dues do not block it.
								</Typography>
							)}
						</Stack>
					)}
					{request.manualFields.map((field) => (
						<TextField
							key={field.key}
							label={field.label}
							value={manualValues[field.key] ?? ""}
							onChange={(e) => setManualValues((prev) => ({ ...prev, [field.key]: e.target.value }))}
							fullWidth
						/>
					))}
					{needsOverride && (
						<TextField
							label="Reason to issue despite pending dues"
							helperText="Recorded against the certificate with your name."
							value={overrideReason}
							onChange={(e) => setOverrideReason(e.target.value)}
							required
							multiline
							rows={2}
							fullWidth
						/>
					)}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Back</Button>
				<Button type="submit" variant="contained" disabled={submitting || !clearance || (needsOverride && !overrideReason.trim())}>
					Issue and print
				</Button>
			</DialogActions>
		</Dialog>
	);
}
