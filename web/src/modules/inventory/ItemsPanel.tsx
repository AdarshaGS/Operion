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
import { ApiError } from "../../api/client";
import { listItemCategories, type ItemCategoryResponse } from "../../api/itemCategories";
import { createItem, listItems, type ItemResponse } from "../../api/items";

export function ItemsPanel() {
	const navigate = useNavigate();
	const [items, setItems] = useState<ItemResponse[]>([]);
	const [categories, setCategories] = useState<ItemCategoryResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [categoryId, setCategoryId] = useState("");
	const [code, setCode] = useState("");
	const [name, setName] = useState("");
	const [unit, setUnit] = useState("");
	const [description, setDescription] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listItems()
			.then(setItems)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load items"));
	}

	useEffect(refresh, []);
	useEffect(() => {
		listItemCategories().then(setCategories).catch(() => {});
	}, []);

	function categoryLabel(id: number): string {
		return categories.find((c) => c.id === id)?.name ?? `Category #${id}`;
	}

	function openDialog() {
		listItemCategories().then(setCategories).catch(() => {});
		setDialogOpen(true);
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createItem({ categoryId: Number(categoryId), code, name, unit, description: description || null });
			setCategoryId("");
			setCode("");
			setName("");
			setUnit("");
			setDescription("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create item");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Items</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={openDialog}>
						Add item
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Code</TableCell>
								<TableCell>Name</TableCell>
								<TableCell>Category</TableCell>
								<TableCell>Unit</TableCell>
								<TableCell>Status</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{items.map((item) => (
								<TableRow key={item.id} hover onClick={() => navigate(`/inventory/items/${item.id}`)} sx={{ cursor: "pointer" }}>
									<TableCell>{item.code}</TableCell>
									<TableCell>{item.name}</TableCell>
									<TableCell>{categoryLabel(item.categoryId)}</TableCell>
									<TableCell>{item.unit}</TableCell>
									<TableCell>
										<Chip label={item.status} size="small" />
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add item</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Category" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} required autoFocus fullWidth>
							{categories.map((category) => (
								<MenuItem key={category.id} value={category.id}>
									{category.name}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Code" placeholder="STA-001" value={code} onChange={(e) => setCode(e.target.value)} required fullWidth />
						<TextField label="Name" placeholder="A4 Paper Ream" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
						<TextField label="Unit" placeholder="PCS" value={unit} onChange={(e) => setUnit(e.target.value)} required fullWidth />
						<TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !categoryId}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
