import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
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
import { createBook, listBooks, type BookResponse } from "../../api/books";
import { ApiError } from "../../api/client";

export function BooksPanel() {
	const navigate = useNavigate();
	const [books, setBooks] = useState<BookResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [isbn, setIsbn] = useState("");
	const [title, setTitle] = useState("");
	const [author, setAuthor] = useState("");
	const [category, setCategory] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listBooks()
			.then(setBooks)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load books"));
	}

	useEffect(refresh, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createBook({ isbn: isbn || null, title, author: author || null, category: category || null });
			setIsbn("");
			setTitle("");
			setAuthor("");
			setCategory("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create book");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Books</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add book
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{books.length === 0 && <Alert severity="info">No books yet.</Alert>}

				{books.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Title</TableCell>
									<TableCell>Author</TableCell>
									<TableCell>Category</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{books.map((book) => (
									<TableRow key={book.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/library/books/${book.id}`)}>
										<TableCell>{book.title}</TableCell>
										<TableCell>{book.author ?? "—"}</TableCell>
										<TableCell>{book.category ?? "—"}</TableCell>
										<TableCell>
											<Chip label={book.status} size="small" />
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add book</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus fullWidth />
						<TextField label="Author" value={author} onChange={(e) => setAuthor(e.target.value)} fullWidth />
						<TextField label="ISBN" value={isbn} onChange={(e) => setIsbn(e.target.value)} fullWidth />
						<TextField label="Category" value={category} onChange={(e) => setCategory(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
