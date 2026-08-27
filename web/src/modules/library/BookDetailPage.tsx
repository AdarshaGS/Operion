import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
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
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { addBookCopy, listBookCopies, listBooks, type BookCopyResponse, type BookResponse } from "../../api/books";
import { type CampusResponse, listCampuses } from "../../api/campuses";
import { ApiError } from "../../api/client";

/** No GET-by-id exists for Book - resolving via list+find, same tradeoff documented
 * for SchoolClassSectionsPage/RouteDetailPage at this data scale. */
export function BookDetailPage() {
	const { bookId } = useParams<{ bookId: string }>();
	const navigate = useNavigate();

	const [book, setBook] = useState<BookResponse | null>(null);
	const [copies, setCopies] = useState<BookCopyResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [campusId, setCampusId] = useState("");
	const [accessionNumber, setAccessionNumber] = useState("");
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		if (!bookId) return;
		listBooks()
			.then((books) => setBook(books.find((b) => b.id === Number(bookId)) ?? null))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load book"))
			.finally(() => setLoading(false));
		refreshCopies();
	}, [bookId]);

	useEffect(() => {
		listCampuses().then(setCampuses).catch(() => {});
	}, []);

	function refreshCopies() {
		if (!bookId) return;
		listBookCopies(Number(bookId))
			.then(setCopies)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load copies"));
	}

	function campusName(id: number): string {
		return campuses.find((c) => c.id === id)?.name ?? `Campus #${id}`;
	}

	async function handleAddCopy(event: FormEvent) {
		event.preventDefault();
		if (!bookId) return;
		setSubmitting(true);
		try {
			await addBookCopy(Number(bookId), { campusId: Number(campusId), accessionNumber });
			setCampusId("");
			setAccessionNumber("");
			setDialogOpen(false);
			refreshCopies();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add copy");
		} finally {
			setSubmitting(false);
		}
	}

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/library")}>
					Back to library
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				{book?.title ?? `Book #${bookId}`}
			</Typography>
			{book?.author && (
				<Typography variant="body2" color="text.secondary">
					by {book.author}
				</Typography>
			)}

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Copies</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
							Add copy
						</Button>
					</Box>

					{copies.length === 0 && <Alert severity="info">No copies yet.</Alert>}

					{copies.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Accession #</TableCell>
										<TableCell>Campus</TableCell>
										<TableCell>Status</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{copies.map((copy) => (
										<TableRow key={copy.id}>
											<TableCell>{copy.accessionNumber}</TableCell>
											<TableCell>{campusName(copy.campusId)}</TableCell>
											<TableCell>
												<Chip label={copy.status} size="small" />
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleAddCopy} fullWidth maxWidth="xs">
				<DialogTitle>Add copy</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value)} required fullWidth>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Accession number"
							value={accessionNumber}
							onChange={(e) => setAccessionNumber(e.target.value)}
							required
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
