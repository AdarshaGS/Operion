import { useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import CloudUploadIcon from "@mui/icons-material/CloudUpload";
import DownloadIcon from "@mui/icons-material/Download";
import { ApiError } from "../../../api/client";
import type { ImportRowResult, ImportRowStatus } from "../../../api/imports";
import { downloadCsvFile, toCsv } from "../../../utils/csv";
import type { ImportEntityConfig } from "./ImportConfig";

type Step = "start" | "busy" | "preview" | "summary";

const STATUS_COLOR: Record<ImportRowStatus, "success" | "error" | "warning" | "info"> = {
	VALID: "info",
	IMPORTED: "success",
	ERROR: "error",
	DUPLICATE: "warning",
};

function countBy(results: ImportRowResult[], status: ImportRowStatus) {
	return results.filter((r) => r.status === status).length;
}

export function downloadTemplate(config: ImportEntityConfig) {
	const csv = [config.templateHeaders.join(","), config.templateExampleRow.join(",")].join("\n");
	downloadCsvFile(config.templateFileName, csv);
}

function downloadErrorReport(config: ImportEntityConfig, results: ImportRowResult[]) {
	const badRows = results.filter((r) => r.status === "ERROR" || r.status === "DUPLICATE");
	const csv = toCsv(
		["row", "status", "message"],
		badRows.map((r) => ({ row: r.row, status: r.status, message: r.message })),
	);
	downloadCsvFile(`${config.key}-import-errors.csv`, csv);
}

/** One dialog, driven entirely by ImportEntityConfig, reused for every entity - see
 * ImportConfig.ts. Entities with supportsPreview=true get a validate-then-confirm flow
 * (upload -> preview counts/table -> confirm -> summary); Students (supportsPreview=false,
 * its backend predates validateOnly) goes straight from upload to summary. */
export function ImportWorkflowDialog({
	open,
	onClose,
	config,
	onImported,
}: {
	open: boolean;
	onClose: () => void;
	config: ImportEntityConfig;
	onImported?: () => void;
}) {
	const [step, setStep] = useState<Step>("start");
	const [file, setFile] = useState<File | null>(null);
	const [results, setResults] = useState<ImportRowResult[]>([]);
	const [error, setError] = useState<string | null>(null);

	function reset() {
		setStep("start");
		setFile(null);
		setResults([]);
		setError(null);
	}

	function handleClose() {
		reset();
		onClose();
	}

	async function handleFileSelected(selected: File) {
		setFile(selected);
		setError(null);
		setStep("busy");
		try {
			const rowResults = await config.runImport(selected, config.supportsPreview);
			setResults(rowResults);
			if (config.supportsPreview) {
				setStep("preview");
			} else {
				setStep("summary");
				onImported?.();
			}
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Import failed");
			setStep("start");
		}
	}

	async function handleConfirm() {
		if (!file) return;
		setStep("busy");
		setError(null);
		try {
			const rowResults = await config.runImport(file, false);
			setResults(rowResults);
			setStep("summary");
			onImported?.();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Import failed");
			setStep("preview");
		}
	}

	const validCount = countBy(results, "VALID");
	const importedCount = countBy(results, "IMPORTED");
	const errorCount = countBy(results, "ERROR");
	const duplicateCount = countBy(results, "DUPLICATE");

	return (
		<Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
			<DialogTitle>Import {config.label}</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ pt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}

					{step === "start" && (
						<>
							<Typography variant="body2" color="text.secondary">
								{config.description}
							</Typography>
							<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
								<Button variant="outlined" startIcon={<DownloadIcon />} onClick={() => downloadTemplate(config)}>
									Download template
								</Button>
								<Button component="label" variant="contained" startIcon={<CloudUploadIcon />}>
									Upload file
									<input
										type="file"
										hidden
										accept=".csv,text/csv,.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
										onChange={(event) => {
											const selected = event.target.files?.[0];
											if (selected) {
												handleFileSelected(selected);
											}
											event.target.value = "";
										}}
									/>
								</Button>
							</Stack>
						</>
					)}

					{step === "busy" && (
						<Stack alignItems="center" spacing={2} sx={{ py: 4 }}>
							<CircularProgress />
							<Typography variant="body2" color="text.secondary">
								{config.supportsPreview && results.length === 0 ? "Validating..." : "Importing..."}
							</Typography>
						</Stack>
					)}

					{(step === "preview" || step === "summary") && (
						<>
							<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
								<Chip label={`Total rows: ${results.length}`} size="small" />
								{step === "preview" && <Chip label={`Valid: ${validCount}`} color="info" size="small" />}
								{step === "summary" && <Chip label={`Imported: ${importedCount}`} color="success" size="small" />}
								{errorCount > 0 && <Chip label={`Errors: ${errorCount}`} color="error" size="small" />}
								{duplicateCount > 0 && <Chip label={`Duplicates: ${duplicateCount}`} color="warning" size="small" />}
							</Stack>

							{(errorCount > 0 || duplicateCount > 0) && (
								<Button size="small" startIcon={<DownloadIcon />} onClick={() => downloadErrorReport(config, results)}>
									Download error report
								</Button>
							)}

							<TableContainer sx={{ maxHeight: 360 }}>
								<Table size="small" stickyHeader>
									<TableHead>
										<TableRow>
											<TableCell>Row</TableCell>
											<TableCell>Status</TableCell>
											<TableCell>Message</TableCell>
										</TableRow>
									</TableHead>
									<TableBody>
										{results.map((result) => (
											<TableRow key={result.row}>
												<TableCell>{result.row}</TableCell>
												<TableCell>
													<Chip label={result.status} color={STATUS_COLOR[result.status]} size="small" />
												</TableCell>
												<TableCell>{result.message}</TableCell>
											</TableRow>
										))}
									</TableBody>
								</Table>
							</TableContainer>
						</>
					)}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={handleClose}>{step === "summary" ? "Close" : "Cancel"}</Button>
				{step === "preview" && (
					<Button variant="contained" disabled={validCount === 0} onClick={handleConfirm}>
						Confirm import ({validCount})
					</Button>
				)}
			</DialogActions>
		</Dialog>
	);
}
