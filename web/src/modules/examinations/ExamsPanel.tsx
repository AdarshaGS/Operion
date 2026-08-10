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
import { type AcademicYearResponse, listAcademicYears } from "../../api/academicYears";
import { ApiError } from "../../api/client";
import { createExam, listExams, type ExamResponse } from "../../api/exams";

const EXAM_TYPES = ["UNIT_TEST", "MID_TERM", "FINAL", "OTHER"];

export function ExamsPanel() {
	const navigate = useNavigate();
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [academicYearId, setAcademicYearId] = useState("");
	const [exams, setExams] = useState<ExamResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [name, setName] = useState("");
	const [examType, setExamType] = useState("UNIT_TEST");
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		listAcademicYears().then(setAcademicYears).catch(() => {});
	}, []);

	function refresh() {
		if (!academicYearId) {
			setExams([]);
			return;
		}
		listExams(Number(academicYearId))
			.then(setExams)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load exams"));
	}

	useEffect(refresh, [academicYearId]);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createExam({ academicYearId: Number(academicYearId), name, examType });
			setName("");
			setExamType("UNIT_TEST");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create exam");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Exams</Typography>

				<Box sx={{ display: "flex", gap: 2, alignItems: "flex-end" }}>
					<TextField
						select
						label="Academic year"
						value={academicYearId}
						onChange={(e) => setAcademicYearId(e.target.value)}
						sx={{ minWidth: 240 }}
					>
						{academicYears.map((year) => (
							<MenuItem key={year.id} value={year.id}>
								{year.name}
							</MenuItem>
						))}
					</TextField>
					<Button startIcon={<AddIcon />} onClick={() => setDialogOpen(true)} disabled={!academicYearId}>
						Add exam
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{academicYearId && exams.length === 0 && <Alert severity="info">No exams yet for this academic year.</Alert>}

				{exams.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Name</TableCell>
									<TableCell>Type</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{exams.map((exam) => (
									<TableRow key={exam.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/examinations/exams/${exam.id}`)}>
										<TableCell>{exam.name}</TableCell>
										<TableCell>{exam.examType}</TableCell>
										<TableCell>
											<Chip label={exam.status} size="small" />
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add exam</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
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
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
