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
import { changeGradeLevelStatus, createGradeLevel, listGradeLevels, type GradeLevelResponse } from "../../api/gradeLevels";

export function GradeLevelsPanel() {
	const [gradeLevels, setGradeLevels] = useState<GradeLevelResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [name, setName] = useState("");
	const [sequenceOrder, setSequenceOrder] = useState("");
	const [stage, setStage] = useState("");
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
			await createGradeLevel({ name, sequenceOrder: Number(sequenceOrder), stage: stage || null });
			setName("");
			setSequenceOrder("");
			setStage("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create grade level");
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
						<TextField label="Name" placeholder="Grade 5" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
						<TextField
							label="Sequence order"
							type="number"
							value={sequenceOrder}
							onChange={(e) => setSequenceOrder(e.target.value)}
							required
							fullWidth
						/>
						<TextField label="Stage" placeholder="PRIMARY" value={stage} onChange={(e) => setStage(e.target.value)} fullWidth />
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
