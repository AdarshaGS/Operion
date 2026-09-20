import { useEffect, useState } from "react";
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
import { ApiError } from "../../../api/client";
import { fetchImportHistory, type ImportRunResponse } from "../../../api/imports";

export function ImportHistoryDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
	const [runs, setRuns] = useState<ImportRunResponse[] | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		if (!open) return;
		setRuns(null);
		setError(null);
		fetchImportHistory()
			.then(setRuns)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Could not load import history"));
	}, [open]);

	return (
		<Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
			<DialogTitle>Import history</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ pt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					{!runs && !error && (
						<Stack alignItems="center" sx={{ py: 4 }}>
							<CircularProgress />
						</Stack>
					)}
					{runs && runs.length === 0 && (
						<Typography variant="body2" color="text.secondary">
							No imports yet.
						</Typography>
					)}
					{runs && runs.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>When</TableCell>
										<TableCell>Entity</TableCell>
										<TableCell>File</TableCell>
										<TableCell>Total</TableCell>
										<TableCell>Imported</TableCell>
										<TableCell>Errors</TableCell>
										<TableCell>Duplicates</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{runs.map((run) => (
										<TableRow key={run.id}>
											<TableCell>{new Date(run.createdAt).toLocaleString()}</TableCell>
											<TableCell>{run.entityType}</TableCell>
											<TableCell>{run.fileName}</TableCell>
											<TableCell>{run.totalRows}</TableCell>
											<TableCell>
												<Chip label={run.importedCount} color="success" size="small" />
											</TableCell>
											<TableCell>{run.errorCount > 0 ? <Chip label={run.errorCount} color="error" size="small" /> : "-"}</TableCell>
											<TableCell>
												{run.duplicateCount > 0 ? <Chip label={run.duplicateCount} color="warning" size="small" /> : "-"}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	);
}
