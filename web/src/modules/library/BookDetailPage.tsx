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
import IconButton from "@mui/material/IconButton";
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
import EditIcon from "@mui/icons-material/Edit";
import {
	addBookCopy,
	listBookCopies,
	listBooks,
	updateBook,
	updateCopyShelfLocation,
	type BookCopyResponse,
	type BookResponse,
} from "../../api/books";
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
	const [shelfLocation, setShelfLocation] = useState("");
	const [submitting, setSubmitting] = useState(false);

	const [editBookOpen, setEditBookOpen] = useState(false);
	const [editForm, setEditForm] = useState({ isbn: "", title: "", author: "", publisher: "", category: "", edition: "" });

	const [shelfDialogCopy, setShelfDialogCopy] = useState<BookCopyResponse | null>(null);
	const [shelfDialogValue, setShelfDialogValue] = useState("");

	function refreshBook() {
		if (!bookId) return;
		listBooks()
			.then((books) => setBook(books.find((b) => b.id === Number(bookId)) ?? null))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load book"))
			.finally(() => setLoading(false));
	}

	useEffect(() => {
		refreshBook();
		refreshCopies();
		// eslint-disable-next-line react-hooks/exhaustive-deps
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
			await addBookCopy(Number(bookId), { campusId: Number(campusId), accessionNumber, shelfLocation: shelfLocation || null });
			setCampusId("");
			setAccessionNumber("");
			setShelfLocation("");
			setDialogOpen(false);
			refreshCopies();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add copy");
		} finally {
			setSubmitting(false);
		}
	}

	function openEditBook() {
		if (!book) return;
		setEditForm({
			isbn: book.isbn ?? "",
			title: book.title,
			author: book.author ?? "",
			publisher: book.publisher ?? "",
			category: book.category ?? "",
			edition: book.edition ?? "",
		});
		setEditBookOpen(true);
	}

	async function handleEditBook(event: FormEvent) {
		event.preventDefault();
		if (!bookId) return;
		setSubmitting(true);
		try {
			await updateBook(Number(bookId), {
				isbn: editForm.isbn || null,
				title: editForm.title,
				author: editForm.author || null,
				publisher: editForm.publisher || null,
				category: editForm.category || null,
				edition: editForm.edition || null,
			});
			setEditBookOpen(false);
			refreshBook();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update book");
		} finally {
			setSubmitting(false);
		}
	}

	function openShelfDialog(copy: BookCopyResponse) {
		setShelfDialogCopy(copy);
		setShelfDialogValue(copy.shelfLocation ?? "");
	}

	async function handleSaveShelfLocation(event: FormEvent) {
		event.preventDefault();
		if (!bookId || !shelfDialogCopy) return;
		setSubmitting(true);
		try {
			await updateCopyShelfLocation(Number(bookId), shelfDialogCopy.id, shelfDialogValue || null);
			setShelfDialogCopy(null);
			refreshCopies();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update shelf location");
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

			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
				<Box>
					<Typography variant="h4" component="h1">
						{book?.title ?? `Book #${bookId}`}
					</Typography>
					{book?.author && (
						<Typography variant="body2" color="text.secondary">
							by {book.author}
						</Typography>
					)}
				</Box>
				{book && (
					<Button size="small" startIcon={<EditIcon />} onClick={openEditBook}>
						Edit
					</Button>
				)}
			</Box>

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
										<TableCell>Shelf location</TableCell>
										<TableCell>Status</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{copies.map((copy) => (
										<TableRow key={copy.id}>
											<TableCell>{copy.accessionNumber}</TableCell>
											<TableCell>{campusName(copy.campusId)}</TableCell>
											<TableCell>{copy.shelfLocation ?? "—"}</TableCell>
											<TableCell>
												<Chip label={copy.status} size="small" />
											</TableCell>
											<TableCell>
												<IconButton size="small" onClick={() => openShelfDialog(copy)} aria-label="Edit shelf location">
													<EditIcon fontSize="small" />
												</IconButton>
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
						<TextField label="Shelf location" value={shelfLocation} onChange={(e) => setShelfLocation(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={editBookOpen} onClose={() => setEditBookOpen(false)} component="form" onSubmit={handleEditBook} fullWidth maxWidth="xs">
				<DialogTitle>Edit book</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Title"
							value={editForm.title}
							onChange={(e) => setEditForm({ ...editForm, title: e.target.value })}
							required
							fullWidth
						/>
						<TextField
							label="Author"
							value={editForm.author}
							onChange={(e) => setEditForm({ ...editForm, author: e.target.value })}
							fullWidth
						/>
						<TextField
							label="Publisher"
							value={editForm.publisher}
							onChange={(e) => setEditForm({ ...editForm, publisher: e.target.value })}
							fullWidth
						/>
						<TextField
							label="Edition"
							value={editForm.edition}
							onChange={(e) => setEditForm({ ...editForm, edition: e.target.value })}
							fullWidth
						/>
						<TextField label="ISBN" value={editForm.isbn} onChange={(e) => setEditForm({ ...editForm, isbn: e.target.value })} fullWidth />
						<TextField
							label="Category"
							value={editForm.category}
							onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setEditBookOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={!!shelfDialogCopy} onClose={() => setShelfDialogCopy(null)} component="form" onSubmit={handleSaveShelfLocation} fullWidth maxWidth="xs">
				<DialogTitle>Shelf location — {shelfDialogCopy?.accessionNumber}</DialogTitle>
				<DialogContent>
					<TextField
						label="Shelf location"
						value={shelfDialogValue}
						onChange={(e) => setShelfDialogValue(e.target.value)}
						autoFocus
						fullWidth
						sx={{ mt: 1 }}
					/>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setShelfDialogCopy(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
