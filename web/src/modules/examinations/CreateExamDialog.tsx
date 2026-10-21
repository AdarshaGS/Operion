import { useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import { ApiError } from "../../api/client";
import { createExam } from "../../api/exams";

const EXAM_TYPES = ["UNIT_TEST", "MID_TERM", "FINAL", "OTHER"];

interface Props {
	open: boolean;
	academicYearId: number | null;
	onClose: () => void;
	onCreated: () => void;
}

export function CreateExamDialog({ open, academicYearId, onClose, onCreated }: Props) {
	const [error, setError] = useState<string | null>(null);
	const [name, setName] = useState("");
	const [examType, setExamType] = useState("UNIT_TEST");
	const [submitting, setSubmitting] = useState(false);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		if (!academicYearId) return;
		setSubmitting(true);
		setError(null);
		try {
			await createExam({ academicYearId, name, examType });
			setName("");
			setExamType("UNIT_TEST");
			onCreated();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create exam");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Dialog open={open} onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
			<DialogTitle>Create examination</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField label="Name" placeholder="Mid Term 2026" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
					<TextField select label="Type" value={examType} onChange={(e) => setExamType(e.target.value)} required fullWidth>
						{EXAM_TYPES.map((type) => (
							<MenuItem key={type} value={type}>
								{type}
							</MenuItem>
						))}
					</TextField>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" disabled={submitting || !academicYearId}>
					Create
				</Button>
			</DialogActions>
		</Dialog>
	);
}
