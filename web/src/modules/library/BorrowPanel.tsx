import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
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
import { type BookCopyResponse, type BookResponse, listBookCopies, listBooks } from "../../api/books";
import {
	issueBook,
	listActiveBorrowsByBorrower,
	markBookLost,
	returnBook,
	type BorrowRecordResponse,
} from "../../api/borrowRecords";
import { ApiError } from "../../api/client";
import { listFinesForRecord, payFine, raiseFine, waiveFine, type FineResponse } from "../../api/fines";
import { type PersonResponse, listPersons } from "../../api/persons";

const FINE_REASONS = ["OVERDUE", "LOST", "DAMAGED"];

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

export function BorrowPanel() {
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [borrowerId, setBorrowerId] = useState("");
	const [activeBorrows, setActiveBorrows] = useState<BorrowRecordResponse[]>([]);
	const [books, setBooks] = useState<BookResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [issueDialogOpen, setIssueDialogOpen] = useState(false);
	const [bookId, setBookId] = useState("");
	const [availableCopies, setAvailableCopies] = useState<BookCopyResponse[]>([]);
	const [bookCopyId, setBookCopyId] = useState("");
	const [dueDate, setDueDate] = useState("");

	const [lastRecord, setLastRecord] = useState<BorrowRecordResponse | null>(null);
	const [fines, setFines] = useState<FineResponse[]>([]);
	const [fineAmount, setFineAmount] = useState("");
	const [fineReason, setFineReason] = useState("OVERDUE");

	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		listPersons().then(setPersons).catch(() => {});
		listBooks().then(setBooks).catch(() => {});
	}, []);

	function refreshBorrows() {
		if (!borrowerId) return;
		listActiveBorrowsByBorrower(Number(borrowerId))
			.then(setActiveBorrows)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load borrows"));
	}

	useEffect(() => {
		setLastRecord(null);
		setFines([]);
		refreshBorrows();
	}, [borrowerId]);

	// BorrowRecordResponse only carries bookCopyId, and there's no "get copy by id"
	// endpoint - resolving to an accession number/title would mean fetching every book's
	// copies just to build a lookup map. Left as a plain id for this first cut.
	function copyLabel(copyId: number): string {
		return `Copy #${copyId}`;
	}

	async function openIssueDialog() {
		setBookId("");
		setBookCopyId("");
		setAvailableCopies([]);
		setDueDate("");
		listBooks().then(setBooks).catch(() => {});
		setIssueDialogOpen(true);
	}

	async function handleBookChange(newBookId: string) {
		setBookId(newBookId);
		setBookCopyId("");
		if (!newBookId) {
			setAvailableCopies([]);
			return;
		}
		const copies = await listBookCopies(Number(newBookId));
		setAvailableCopies(copies.filter((c) => c.status === "AVAILABLE"));
	}

	async function handleIssue(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await issueBook({
				bookCopyId: Number(bookCopyId),
				borrowerPersonId: Number(borrowerId),
				borrowedDate: todayIso(),
				dueDate,
			});
			setIssueDialogOpen(false);
			refreshBorrows();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to issue book");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleReturn(id: number) {
		try {
			const record = await returnBook(id, todayIso());
			setLastRecord(record);
			setFines(await listFinesForRecord(id));
			refreshBorrows();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to return book");
		}
	}

	async function handleMarkLost(id: number) {
		try {
			const record = await markBookLost(id);
			setLastRecord(record);
			setFines(await listFinesForRecord(id));
			refreshBorrows();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to mark book lost");
		}
	}

	async function handleRaiseFine() {
		if (!lastRecord) return;
		try {
			await raiseFine({ borrowRecordId: lastRecord.id, amount: Number(fineAmount), reason: fineReason });
			setFineAmount("");
			setFines(await listFinesForRecord(lastRecord.id));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to raise fine");
		}
	}

	async function handlePayFine(id: number) {
		if (!lastRecord) return;
		try {
			await payFine(id, todayIso());
			setFines(await listFinesForRecord(lastRecord.id));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to pay fine");
		}
	}

	async function handleWaiveFine(id: number) {
		if (!lastRecord) return;
		try {
			await waiveFine(id, 1, "Waived by admin");
			setFines(await listFinesForRecord(lastRecord.id));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to waive fine");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Borrow / return</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={openIssueDialog} disabled={!borrowerId}>
						Issue book
					</Button>
				</Box>

				<TextField select label="Borrower" value={borrowerId} onChange={(e) => setBorrowerId(e.target.value)} sx={{ maxWidth: 400 }}>
					{persons.map((person) => (
						<MenuItem key={person.id} value={person.id}>
							{person.firstName} {person.lastName}
						</MenuItem>
					))}
				</TextField>

				{error && <Alert severity="error">{error}</Alert>}

				{borrowerId && activeBorrows.length === 0 && <Alert severity="info">No active borrows.</Alert>}

				{activeBorrows.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Copy</TableCell>
									<TableCell>Borrowed</TableCell>
									<TableCell>Due</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{activeBorrows.map((record) => (
									<TableRow key={record.id}>
										<TableCell>{copyLabel(record.bookCopyId)}</TableCell>
										<TableCell>{record.borrowedDate}</TableCell>
										<TableCell>{record.dueDate}</TableCell>
										<TableCell>
											<Button size="small" onClick={() => handleReturn(record.id)}>
												Return
											</Button>
											<Button size="small" color="error" onClick={() => handleMarkLost(record.id)}>
												Mark lost
											</Button>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}

				{lastRecord && (
					<>
						<Divider />
						<Typography variant="subtitle1">
							Borrow record #{lastRecord.id} — <Chip label={lastRecord.status} size="small" />
						</Typography>

						{fines.length > 0 && (
							<TableContainer>
								<Table size="small">
									<TableHead>
										<TableRow>
											<TableCell>Amount</TableCell>
											<TableCell>Reason</TableCell>
											<TableCell>Status</TableCell>
											<TableCell />
										</TableRow>
									</TableHead>
									<TableBody>
										{fines.map((fine) => (
											<TableRow key={fine.id}>
												<TableCell>{fine.amount}</TableCell>
												<TableCell>{fine.reason}</TableCell>
												<TableCell>
													<Chip label={fine.status} size="small" />
												</TableCell>
												<TableCell>
													{fine.status === "PENDING" && (
														<>
															<Button size="small" onClick={() => handlePayFine(fine.id)}>
																Pay
															</Button>
															<Button size="small" onClick={() => handleWaiveFine(fine.id)}>
																Waive
															</Button>
														</>
													)}
												</TableCell>
											</TableRow>
										))}
									</TableBody>
								</Table>
							</TableContainer>
						)}

						<Box sx={{ display: "flex", gap: 2, alignItems: "flex-end", maxWidth: 500 }}>
							<TextField label="Fine amount" type="number" size="small" value={fineAmount} onChange={(e) => setFineAmount(e.target.value)} />
							<TextField select label="Reason" size="small" value={fineReason} onChange={(e) => setFineReason(e.target.value)} sx={{ minWidth: 140 }}>
								{FINE_REASONS.map((reason) => (
									<MenuItem key={reason} value={reason}>
										{reason}
									</MenuItem>
								))}
							</TextField>
							<Button size="small" variant="contained" onClick={handleRaiseFine} disabled={!fineAmount}>
								Raise fine
							</Button>
						</Box>
					</>
				)}
			</Stack>

			<Dialog open={issueDialogOpen} onClose={() => setIssueDialogOpen(false)} component="form" onSubmit={handleIssue} fullWidth maxWidth="xs">
				<DialogTitle>Issue book</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Book" value={bookId} onChange={(e) => handleBookChange(e.target.value)} required fullWidth>
							{books.map((book) => (
								<MenuItem key={book.id} value={book.id}>
									{book.title}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Available copy"
							value={bookCopyId}
							onChange={(e) => setBookCopyId(e.target.value)}
							required
							disabled={!bookId}
							fullWidth
						>
							{availableCopies.map((copy) => (
								<MenuItem key={copy.id} value={copy.id}>
									{copy.accessionNumber}
								</MenuItem>
							))}
						</TextField>
						{bookId && availableCopies.length === 0 && <Alert severity="warning">No available copies for this book.</Alert>}
						<TextField
							label="Due date"
							type="date"
							value={dueDate}
							onChange={(e) => setDueDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setIssueDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !bookCopyId}>
						Issue
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
