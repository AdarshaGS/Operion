import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import AddIcon from "@mui/icons-material/Add";
import { ApiError } from "../../api/client";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listStudents, type StudentResponse } from "../../api/students";
import { useAuth } from "../../auth/AuthContext";
import { StudentApplicationsPanel } from "./StudentApplicationsPanel";
import { TransferRequestsInboxPanel } from "./TransferRequestsInboxPanel";

export function StudentListPage() {
	const navigate = useNavigate();
	const { hasPermission } = useAuth();
	const [students, setStudents] = useState<StudentResponse[] | null>(null);
	const [personsById, setPersonsById] = useState<Map<number, PersonResponse>>(new Map());
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		let cancelled = false;
		Promise.all([listStudents(), listPersons()])
			.then(([studentList, personList]) => {
				if (cancelled) return;
				setStudents(studentList);
				setPersonsById(new Map(personList.map((person) => [person.id, person])));
			})
			.catch((err) => {
				if (cancelled) return;
				setError(err instanceof ApiError ? err.message : "Failed to load students");
			});
		return () => {
			cancelled = true;
		};
	}, []);

	return (
		<Stack spacing={2}>
			<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
				<Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate("/students/new")}>
					Admit student
				</Button>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			{!students && !error && (
				<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
					<CircularProgress />
				</Box>
			)}

			{students && students.length === 0 && <Alert severity="info">No students admitted yet.</Alert>}

			{students && students.length > 0 && (
				<TableContainer component={Paper}>
					<Table>
						<TableHead>
							<TableRow>
								<TableCell>Admission #</TableCell>
								<TableCell>Name</TableCell>
								<TableCell>Admission date</TableCell>
								<TableCell>Status</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{students.map((student) => {
								const person = personsById.get(student.personId);
								return (
									<TableRow
										key={student.id}
										hover
										sx={{ cursor: "pointer" }}
										onClick={() => navigate(`/students/${student.id}`)}
									>
										<TableCell>{student.admissionNumber}</TableCell>
										<TableCell>{person ? `${person.firstName} ${person.lastName}` : "—"}</TableCell>
										<TableCell>{student.admissionDate}</TableCell>
										<TableCell>
											<Chip label={student.status} size="small" />
										</TableCell>
									</TableRow>
								);
							})}
						</TableBody>
					</Table>
				</TableContainer>
			)}

			<TransferRequestsInboxPanel />
			{hasPermission("STUDENT_APPLICATION_MANAGE") && <StudentApplicationsPanel />}
		</Stack>
	);
}
