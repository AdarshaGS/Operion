import { useEffect, useState, type FormEvent } from "react";
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
import { ApiError } from "../../api/client";
import {
	changeGradeLevelStatus,
	createGradeLevel,
	listGradeLevels,
	updateGradeLevel,
	type GradeLevelResponse,
} from "../../api/gradeLevels";

interface GradeLevelFormState {
	name: string;
	sequenceOrder: string;
	stage: string;
}

const EMPTY_FORM: GradeLevelFormState = { name: "", sequenceOrder: "", stage: "" };

function GradeLevelFormFields({ value, onChange }: { value: GradeLevelFormState; onChange: (next: GradeLevelFormState) => void }) {
	return (
		<>
			<TextField
				label="Name"
				placeholder="Grade 5"
				value={value.name}
				onChange={(e) => onChange({ ...value, name: e.target.value })}
				required
				autoFocus
				fullWidth
			/>
			<TextField
				label="Sequence order"
				type="number"
				value={value.sequenceOrder}
				onChange={(e) => onChange({ ...value, sequenceOrder: e.target.value })}
				required
				fullWidth
			/>
			<TextField
				label="Stage"
				placeholder="PRIMARY"
				value={value.stage}
				onChange={(e) => onChange({ ...value, stage: e.target.value })}
				fullWidth
			/>
		</>
	);
}

export function GradeLevelsPanel() {
	const [gradeLevels, setGradeLevels] = useState<GradeLevelResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState<GradeLevelFormState>(EMPTY_FORM);
	const [editTarget, setEditTarget] = useState<GradeLevelResponse | null>(null);
	const [editForm, setEditForm] = useState<GradeLevelFormState>(EMPTY_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listGradeLevels()
			.then((levels) => setGradeLevels(levels.sort((a, b) => a.sequenceOrder - b.sequenceOrder)))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load grade levels"));
	}

	useEffect(refresh, []);

	async function handleToggleStatus(level: GradeLevelResponse) {
		try {
			await changeGradeLevelStatus(level.id, level.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update grade level status");
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createGradeLevel({ name: form.name, sequenceOrder: Number(form.sequenceOrder), stage: form.stage || null });
			setForm(EMPTY_FORM);
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create grade level");
		} finally {
			setSubmitting(false);
		}
	}

	function openEdit(level: GradeLevelResponse) {
		setEditTarget(level);
		setEditForm({ name: level.name, sequenceOrder: String(level.sequenceOrder), stage: level.stage ?? "" });
	}

	async function handleEditSave(event: FormEvent) {
		event.preventDefault();
		if (!editTarget) return;
		setSubmitting(true);
		try {
			await updateGradeLevel(editTarget.id, {
				name: editForm.name,
				sequenceOrder: Number(editForm.sequenceOrder),
				stage: editForm.stage || null,
			});
			setEditTarget(null);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update grade level");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Grade levels</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add grade level
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Name</TableCell>
								<TableCell>Sequence</TableCell>
								<TableCell>Stage</TableCell>
								<TableCell>Status</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{gradeLevels.map((level) => (
								<TableRow key={level.id}>
									<TableCell>{level.name}</TableCell>
									<TableCell>{level.sequenceOrder}</TableCell>
									<TableCell>{level.stage ?? "—"}</TableCell>
									<TableCell>
										<Chip label={level.status} size="small" />
									</TableCell>
									<TableCell>
										<Button size="small" onClick={() => openEdit(level)}>
											Edit
										</Button>
										<Button size="small" onClick={() => handleToggleStatus(level)}>
											{level.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
										</Button>
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add grade level</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<GradeLevelFormFields value={form} onChange={setForm} />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={editTarget !== null} onClose={() => setEditTarget(null)} component="form" onSubmit={handleEditSave} fullWidth maxWidth="xs">
				<DialogTitle>Edit grade level</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<GradeLevelFormFields value={editForm} onChange={setEditForm} />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setEditTarget(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
