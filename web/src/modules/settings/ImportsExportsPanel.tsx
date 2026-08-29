import { useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Divider from "@mui/material/Divider";
import Paper from "@mui/material/Paper";
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
import FileDownloadIcon from "@mui/icons-material/FileDownload";
import {
	exportStudents,
	importStudents,
	STUDENT_IMPORT_TEMPLATE_HEADERS,
	type StudentImportRowResult,
} from "../../api/students";
import { ApiError } from "../../api/client";
import { downloadCsvFile, toCsv } from "../../utils/csv";

const TEMPLATE_EXAMPLE_ROW = [
	"Asha",
	"Rao",
	"2012-04-18",
	"FEMALE",
	"asha.rao@example.com",
	"9876500000",
	"ADM-2026-001",
	"2026-06-01",
	"WALK_IN",
	"",
	"",
	"",
	"O+",
	"General",
	"Indian",
	"",
];

function downloadTemplate() {
	const csv = [STUDENT_IMPORT_TEMPLATE_HEADERS.join(","), TEMPLATE_EXAMPLE_ROW.join(",")].join("\n");
	downloadCsvFile("students-import-template.csv", csv);
}

/** Imports & exports settings section (#147) - Students only for v1, per that issue's
 * own scope. Import is per-row (StudentImportRowResult), so a partial batch is visible
 * rather than silently all-or-nothing. */
export function ImportsExportsPanel() {
	const [importing, setImporting] = useState(false);
	const [results, setResults] = useState<StudentImportRowResult[] | null>(null);
	const [importError, setImportError] = useState<string | null>(null);
	const [exporting, setExporting] = useState(false);
	const [exportError, setExportError] = useState<string | null>(null);

	async function handleFileSelected(file: File) {
		setImporting(true);
		setImportError(null);
		setResults(null);
		try {
			const rowResults = await importStudents(file);
			setResults(rowResults);
		} catch (err) {
			setImportError(err instanceof ApiError ? err.message : "Import failed");
		} finally {
			setImporting(false);
		}
	}

	async function handleExport() {
		setExporting(true);
		setExportError(null);
		try {
			const rows = await exportStudents();
			downloadCsvFile(
				"students.csv",
				toCsv(
					["id", "firstName", "lastName", "email", "phone", "admissionNumber", "admissionDate", "bloodGroup", "category", "status"],
					rows,
				),
			);
		} catch (err) {
			setExportError(err instanceof ApiError ? err.message : "Export failed");
		} finally {
			setExporting(false);
		}
	}

	const successCount = results?.filter((r) => r.success).length ?? 0;
	const failureCount = results ? results.length - successCount : 0;

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={3}>
				<Box>
					<Typography variant="h6">Imports & exports</Typography>
					<Typography variant="body2" color="text.secondary">
						Bulk-import students from a CSV, or export existing student records.
					</Typography>
				</Box>

				<Stack spacing={2}>
					<Typography variant="subtitle1">Students</Typography>

					<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
						<Button variant="outlined" startIcon={<DownloadIcon />} onClick={downloadTemplate}>
							Download CSV template
						</Button>

						<Button component="label" variant="contained" startIcon={importing ? <CircularProgress size={16} color="inherit" /> : <CloudUploadIcon />} disabled={importing}>
							Import students
							<input
								type="file"
								hidden
								accept=".csv,text/csv"
								onChange={(event) => {
									const file = event.target.files?.[0];
									if (file) {
										handleFileSelected(file);
									}
									event.target.value = "";
								}}
							/>
						</Button>

						<Button
							variant="outlined"
							startIcon={exporting ? <CircularProgress size={16} /> : <FileDownloadIcon />}
							onClick={handleExport}
							disabled={exporting}
						>
							Export students
						</Button>
					</Stack>

					{importError && <Alert severity="error">{importError}</Alert>}
					{exportError && <Alert severity="error">{exportError}</Alert>}

					{results && (
						<>
							<Divider />
							<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
								<Typography variant="body2">Import result:</Typography>
								<Chip label={`${successCount} created`} color="success" size="small" />
								{failureCount > 0 && <Chip label={`${failureCount} failed`} color="error" size="small" />}
							</Stack>
							<TableContainer>
								<Table size="small">
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
													<Chip label={result.success ? "Created" : "Failed"} color={result.success ? "success" : "error"} size="small" />
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
			</Stack>
		</Paper>
	);
}
