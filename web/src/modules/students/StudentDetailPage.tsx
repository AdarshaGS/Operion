import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { ApiError } from "../../api/client";
import { getPerson, type PersonResponse } from "../../api/persons";
import { getStudent, type StudentResponse } from "../../api/students";
import { StudentGuardiansPanel } from "./StudentGuardiansPanel";

function Field({ label, value }: { label: string; value: string | number | null | undefined }) {
	return (
		<Box>
			<Typography variant="caption" color="text.secondary">
				{label}
			</Typography>
			<Typography variant="body1">{value ?? "—"}</Typography>
		</Box>
	);
}

export function StudentDetailPage() {
	const { studentId } = useParams<{ studentId: string }>();
	const navigate = useNavigate();
	const [student, setStudent] = useState<StudentResponse | null>(null);
	const [person, setPerson] = useState<PersonResponse | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		if (!studentId) return;
		let cancelled = false;
		getStudent(Number(studentId))
			.then((studentResponse) => {
				if (cancelled) return;
				setStudent(studentResponse);
				return getPerson(studentResponse.personId);
			})
			.then((personResponse) => {
				if (cancelled || !personResponse) return;
				setPerson(personResponse);
			})
			.catch((err) => {
				if (cancelled) return;
				setError(err instanceof ApiError ? err.message : "Failed to load student");
			});
		return () => {
			cancelled = true;
		};
	}, [studentId]);

	if (error) {
		return <Alert severity="error">{error}</Alert>;
	}

	if (!student) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/students")}>
					Back to students
				</Button>
			</Box>

			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
				<Typography variant="h4" component="h1">
					{person ? `${person.firstName} ${person.lastName}` : `Student #${student.id}`}
				</Typography>
				<Chip label={student.status} />
			</Box>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="subtitle1">Person details</Typography>
					<Grid container spacing={2}>
						<Grid size={4}>
							<Field label="Date of birth" value={person?.dateOfBirth} />
						</Grid>
						<Grid size={4}>
							<Field label="Gender" value={person?.gender} />
						</Grid>
						<Grid size={4}>
							<Field label="Phone" value={person?.phone} />
						</Grid>
						<Grid size={4}>
							<Field label="Email" value={person?.email} />
						</Grid>
					</Grid>

					<Divider />
					<Typography variant="subtitle1">Admission details</Typography>
					<Grid container spacing={2}>
						<Grid size={4}>
							<Field label="Admission number" value={student.admissionNumber} />
						</Grid>
						<Grid size={4}>
							<Field label="Admission date" value={student.admissionDate} />
						</Grid>
						<Grid size={4}>
							<Field label="Previous school" value={student.previousSchool} />
						</Grid>
						<Grid size={4}>
							<Field label="Blood group" value={student.bloodGroup} />
						</Grid>
						<Grid size={4}>
							<Field label="Category" value={student.category} />
						</Grid>
						<Grid size={4}>
							<Field label="Nationality" value={student.nationality} />
						</Grid>
						<Grid size={12}>
							<Field label="Remarks" value={student.remarks} />
						</Grid>
					</Grid>
				</Stack>
			</Paper>

			<StudentGuardiansPanel studentId={student.id} />
		</Stack>
	);
}
