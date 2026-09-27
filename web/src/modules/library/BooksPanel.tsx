import { useEffect, useMemo, useState, type FormEvent, type MouseEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Autocomplete from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
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
import EditIcon from "@mui/icons-material/Edit";
import { createBook, listBooks, updateBook, type BookResponse } from "../../api/books";
import { ApiError } from "../../api/client";

/** Suggestions only - Book.category is a free-text column (no backend catalog), so schools
 * can still type anything here. */
const CATEGORY_SUGGESTIONS = ["Textbook", "Reference", "Fiction", "Non-fiction", "Magazine", "Other"];

const EMPTY_FORM = { isbn: "", title: "", author: "", publisher: "", category: "", edition: "" };

export function BooksPanel() {
	const navigate = useNavigate();
	const [books, setBooks] = useState<BookResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [search, setSearch] = useState("");
	const [categoryFilter, setCategoryFilter] = useState("");

	const [dialogOpen, setDialogOpen] = useState(false);
	const [editingId, setEditingId] = useState<number | null>(null);
	const [form, setForm] = useState(EMPTY_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listBooks()
			.then(setBooks)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load books"));
	}

	useEffect(refresh, []);

	const categories = useMemo(() => Array.from(new Set(books.map((b) => b.category).filter((c): c is string => !!c))), [books]);

	const visibleBooks = useMemo(() => {
		const term = search.trim().toLowerCase();
		return books.filter((book) => {
			if (categoryFilter && book.category !== categoryFilter) return false;
			if (!term) return true;
			return (
				book.title.toLowerCase().includes(term) ||
				(book.author ?? "").toLowerCase().includes(term) ||
				(book.category ?? "").toLowerCase().includes(term)
			);
		});
	}, [books, search, categoryFilter]);

	function openAddDialog() {
		setEditingId(null);
		setForm(EMPTY_FORM);
		setDialogOpen(true);
	}

	function openEditDialog(book: BookResponse, event: MouseEvent) {
		event.stopPropagation();
		setEditingId(book.id);
		setForm({
			isbn: book.isbn ?? "",
			title: book.title,
			author: book.author ?? "",
			publisher: book.publisher ?? "",
			category: book.category ?? "",
			edition: book.edition ?? "",
		});
		setDialogOpen(true);
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			const payload = {
				isbn: form.isbn || null,
				title: form.title,
				author: form.author || null,
				publisher: form.publisher || null,
				category: form.category || null,
				edition: form.edition || null,
			};
			if (editingId != null) {
				await updateBook(editingId, payload);
			} else {
				await createBook(payload);
			}
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save book");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Books</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={openAddDialog}>
						Add book
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{books.length === 0 && (
					<Alert
						severity="info"
						action={
							<Button size="small" onClick={openAddDialog}>
								Add first book
							</Button>
						}
					>
						No books yet.
					</Alert>
				)}

				{books.length > 0 && (
					<Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
						<TextField
							label="Search title, author, or category"
							size="small"
							value={search}
							onChange={(e) => setSearch(e.target.value)}
							sx={{ maxWidth: 400, flexGrow: 1 }}
						/>
						<TextField
							select
							label="Category"
							size="small"
							value={categoryFilter}
							onChange={(e) => setCategoryFilter(e.target.value)}
							sx={{ minWidth: 160 }}
						>
							<MenuItem value="">All</MenuItem>
							{categories.map((category) => (
								<MenuItem key={category} value={category}>
									{category}
								</MenuItem>
							))}
						</TextField>
					</Stack>
				)}

				{books.length > 0 && visibleBooks.length === 0 && <Alert severity="info">No books match your filters.</Alert>}

				{visibleBooks.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Title</TableCell>
									<TableCell>Author</TableCell>
									<TableCell>Category</TableCell>
									<TableCell>Status</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{visibleBooks.map((book) => (
									<TableRow key={book.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/library/books/${book.id}`)}>
										<TableCell>{book.title}</TableCell>
										<TableCell>{book.author ?? "—"}</TableCell>
										<TableCell>{book.category ?? "—"}</TableCell>
										<TableCell>
											<Chip label={book.status} size="small" />
										</TableCell>
										<TableCell>
											<IconButton size="small" onClick={(e) => openEditDialog(book, e)} aria-label="Edit book">
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

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>{editingId != null ? "Edit book" : "Add book"}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Title"
							value={form.title}
							onChange={(e) => setForm({ ...form, title: e.target.value })}
							required
							autoFocus
							fullWidth
						/>
						<TextField label="Author" value={form.author} onChange={(e) => setForm({ ...form, author: e.target.value })} fullWidth />
						<TextField label="Publisher" value={form.publisher} onChange={(e) => setForm({ ...form, publisher: e.target.value })} fullWidth />
						<TextField label="Edition" value={form.edition} onChange={(e) => setForm({ ...form, edition: e.target.value })} fullWidth />
						<TextField label="ISBN" value={form.isbn} onChange={(e) => setForm({ ...form, isbn: e.target.value })} fullWidth />
						<Autocomplete
							freeSolo
							options={CATEGORY_SUGGESTIONS}
							inputValue={form.category}
							onInputChange={(_event, value) => setForm({ ...form, category: value })}
							renderInput={(params) => <TextField {...params} label="Category" fullWidth />}
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						{editingId != null ? "Save" : "Add"}
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
