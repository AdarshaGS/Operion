import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
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
import { listOverdueBorrows, sendOverdueReminder, type OverdueBorrowResponse } from "../../api/borrowRecords";

/** Overdue list: borrower contact, fine due, and a reminder action (#171). Sending a
 * reminder routes through the same real EMAIL/SMS pipeline Fees' demand view already
 * uses - a borrower with no usable contact channel on file simply gets 0 notified,
 * surfaced back inline rather than as an error. */
export function OverduePanel() {
	const [records, setRecords] = useState<OverdueBorrowResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [reminderResult, setReminderResult] = useState<Record<number, string>>({});
	const [sending, setSending] = useState<number | null>(null);

	function refresh() {
		listOverdueBorrows()
			.then(setRecords)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load overdue books"));
	}

	useEffect(refresh, []);

	async function handleSendReminder(id: number) {
		setSending(id);
		try {
			const notified = await sendOverdueReminder(id);
			setReminderResult((prev) => ({ ...prev, [id]: notified > 0 ? "Reminder sent" : "No contact on file" }));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to send reminder");
		} finally {
			setSending(null);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Overdue</Typography>
					<Button size="small" onClick={refresh}>
						Refresh
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{records.length === 0 && <Alert severity="info">Nothing overdue.</Alert>}

				{records.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Borrower</TableCell>
									<TableCell>Contact</TableCell>
									<TableCell>Book</TableCell>
									<TableCell>Due</TableCell>
									<TableCell>Days overdue</TableCell>
									<TableCell>Fine due</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{records.map((record) => (
									<TableRow key={record.id}>
										<TableCell>{record.borrowerName}</TableCell>
										<TableCell>{record.borrowerPhone ?? record.borrowerEmail ?? "—"}</TableCell>
										<TableCell>
											{record.bookTitle} <Typography variant="caption" color="text.secondary">({record.accessionNumber})</Typography>
										</TableCell>
										<TableCell>{record.dueDate}</TableCell>
										<TableCell>
											<Chip label={record.daysOverdue} size="small" color="error" />
										</TableCell>
										<TableCell>{record.fineDue > 0 ? record.fineDue : "—"}</TableCell>
										<TableCell>
											{reminderResult[record.id] ? (
												<Typography variant="caption" color="text.secondary">
													{reminderResult[record.id]}
												</Typography>
											) : (
												<Button size="small" disabled={sending === record.id} onClick={() => handleSendReminder(record.id)}>
													Send reminder
												</Button>
											)}
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
