import { useEffect, useRef, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
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
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { resolveAssetUrl, uploadAsset } from "../../api/assets";
import { ApiError } from "../../api/client";
import { getSession } from "../../api/tokenStore";
import {
	listStudentDocuments,
	uploadStudentDocument,
	verifyStudentDocument,
	type StudentDocumentResponse,
} from "../../api/studentDocuments";
import { Can } from "../../auth/Can";

const DOCUMENT_TYPES = ["BIRTH_CERTIFICATE", "TRANSFER_CERTIFICATE", "ID_PROOF", "OTHER"] as const;

const VERIFICATION_COLOR: Record<string, "success" | "error" | "default"> = {
	VERIFIED: "success",
	REJECTED: "error",
	PENDING: "default",
};

/** Insert-only history (#115) - re-uploading a document type supersedes the prior ACTIVE
 * row server-side (StudentService.addDocument), so this only ever shows ACTIVE rows. */
export function StudentDocumentsPanel({ studentId }: { studentId: number }) {
	const [documents, setDocuments] = useState<StudentDocumentResponse[] | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [uploadOpen, setUploadOpen] = useState(false);
	const [documentType, setDocumentType] = useState<string>(DOCUMENT_TYPES[0]);
	const [file, setFile] = useState<File | null>(null);
	const [submitting, setSubmitting] = useState(false);
	const fileInputRef = useRef<HTMLInputElement>(null);

	function refresh() {
		listStudentDocuments(studentId)
			.then((docs) => setDocuments(docs.filter((d) => d.status === "ACTIVE")))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load documents"));
	}

	useEffect(refresh, [studentId]);

	async function handleUpload() {
		if (!file) return;
		setError(null);
		setSubmitting(true);
		try {
			const asset = await uploadAsset(file);
			await uploadStudentDocument(studentId, {
				documentType,
				fileReference: asset.reference,
				fileName: file.name,
				mimeType: file.type,
			});
			setUploadOpen(false);
			setFile(null);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to upload document");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleVerify(documentId: number, verificationStatus: "VERIFIED" | "REJECTED") {
		const session = getSession();
		if (!session) return;
		try {
			await verifyStudentDocument(studentId, documentId, verificationStatus, session.userId);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update verification status");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Documents</Typography>
					<Can anyOf={["STUDENT_DOCUMENT_MANAGE"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setUploadOpen(true)}>
							Upload document
						</Button>
					</Can>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{!documents && !error && (
					<Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
						<CircularProgress size={24} />
					</Box>
				)}

				{documents && documents.length === 0 && <Alert severity="info">No documents uploaded yet.</Alert>}

				{documents && documents.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Type</TableCell>
									<TableCell>File</TableCell>
									<TableCell>Status</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{documents.map((doc) => (
									<TableRow key={doc.id}>
										<TableCell>{doc.documentType.replaceAll("_", " ")}</TableCell>
										<TableCell>
											<a href={resolveAssetUrl(doc.fileUrl)} target="_blank" rel="noreferrer">
												{doc.fileName}
											</a>
										</TableCell>
										<TableCell>
											<Chip label={doc.verificationStatus} size="small" color={VERIFICATION_COLOR[doc.verificationStatus]} />
										</TableCell>
										<TableCell>
											{doc.verificationStatus === "PENDING" && (
												<Can anyOf={["STUDENT_DOCUMENT_MANAGE"]}>
													<Button size="small" onClick={() => handleVerify(doc.id, "VERIFIED")}>
														Verify
													</Button>
													<Button size="small" color="error" onClick={() => handleVerify(doc.id, "REJECTED")}>
														Reject
													</Button>
												</Can>
											)}
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={uploadOpen} onClose={() => setUploadOpen(false)} fullWidth maxWidth="xs">
				<DialogTitle>Upload document</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Document type" value={documentType} onChange={(e) => setDocumentType(e.target.value)} fullWidth>
							{DOCUMENT_TYPES.map((type) => (
								<MenuItem key={type} value={type}>
									{type.replaceAll("_", " ")}
								</MenuItem>
							))}
						</TextField>
						<Button variant="outlined" onClick={() => fileInputRef.current?.click()}>
							{file ? file.name : "Choose file (PNG, JPG, or PDF)"}
						</Button>
						<input
							ref={fileInputRef}
							type="file"
							accept="image/png,image/jpeg,application/pdf"
							hidden
							onChange={(e) => setFile(e.target.files?.[0] ?? null)}
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setUploadOpen(false)}>Cancel</Button>
					<Button variant="contained" disabled={!file || submitting} onClick={handleUpload}>
						Upload
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
