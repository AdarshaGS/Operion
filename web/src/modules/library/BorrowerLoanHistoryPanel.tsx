import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { listBorrowHistory, type BorrowRecordResponse } from "../../api/borrowRecords";

const STATUS_COLOR: Record<string, "default" | "warning" | "success" | "error"> = {
	BORROWED: "warning",
	RETURNED: "success",
	LOST: "error",
};

/** This borrower's active + past loans (#248) - linkable from a student/staff profile
 * page so finding what someone currently has out doesn't mean text-searching the whole
 * active-loans table by name. */
export function BorrowerLoanHistoryPanel({ personId }: { personId: number }) {
	const [records, setRecords] = useState<BorrowRecordResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		listBorrowHistory(personId)
			.then(setRecords)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load loan history"));
	}, [personId]);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Library loans</Typography>

				{error && <Alert severity="error">{error}</Alert>}

				{records.length === 0 && !error && <Alert severity="info">No library loans on record.</Alert>}

				{records.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Book</TableCell>
									<TableCell>Copy</TableCell>
									<TableCell>Borrowed</TableCell>
									<TableCell>Due</TableCell>
									<TableCell>Returned</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{records.map((record) => (
									<TableRow key={record.id}>
										<TableCell>{record.bookTitle}</TableCell>
										<TableCell>{record.accessionNumber}</TableCell>
										<TableCell>{record.borrowedDate}</TableCell>
										<TableCell>{record.dueDate}</TableCell>
										<TableCell>{record.returnedDate ?? "—"}</TableCell>
										<TableCell>
											<Chip label={record.status} size="small" color={STATUS_COLOR[record.status] ?? "default"} />
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>
		</Paper>
	);
}
