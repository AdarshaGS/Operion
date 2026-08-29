import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listStudents, type StudentResponse } from "../../api/students";
import { approveTransferRequest, listTransferRequests, rejectTransferRequest, type TransferRequestResponse } from "../../api/transferRequests";

/** Org-wide "pending approvals" inbox for campus transfers - same shape as
 * LeaveRequestsInboxPanel, backed by GET /api/v1/transfer-requests?status=PENDING. */
export function TransferRequestsInboxPanel() {
	const navigate = useNavigate();
	const [requests, setRequests] = useState<TransferRequestResponse[]>([]);
	const [studentsById, setStudentsById] = useState<Map<number, StudentResponse>>(new Map());
	const [personsById, setPersonsById] = useState<Map<number, PersonResponse>>(new Map());
	const [campusesById, setCampusesById] = useState<Map<number, CampusResponse>>(new Map());
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		listTransferRequests({ status: "PENDING" })
			.then(setRequests)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load pending transfer requests"));
	}

	useEffect(refresh, []);
	useEffect(() => {
		listStudents()
			.then((students) => setStudentsById(new Map(students.map((s) => [s.id, s]))))
			.catch(() => {});
		listPersons()
			.then((persons) => setPersonsById(new Map(persons.map((p) => [p.id, p]))))
			.catch(() => {});
		listCampuses()
			.then((campuses) => setCampusesById(new Map(campuses.map((c) => [c.id, c]))))
			.catch(() => {});
	}, []);

	function studentLabel(studentId: number): string {
		const student = studentsById.get(studentId);
		if (!student) return `Student #${studentId}`;
		const person = personsById.get(student.personId);
		return person ? `${person.firstName} ${person.lastName} (${student.admissionNumber})` : student.admissionNumber;
	}

	async function handleApprove(id: number) {
		try {
			await approveTransferRequest(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to approve transfer request");
		}
	}

	async function handleReject(id: number) {
		try {
			await rejectTransferRequest(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to reject transfer request");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Pending campus transfers</Typography>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{requests.length === 0 && <Alert severity="info">No pending transfer requests.</Alert>}

				{requests.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Student</TableCell>
									<TableCell>From</TableCell>
									<TableCell>To</TableCell>
									<TableCell>Reason</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{requests.map((request) => (
									<TableRow
										key={request.id}
										hover
										sx={{ cursor: "pointer" }}
										onClick={() => navigate(`/students/${request.studentId}`)}
									>
										<TableCell>{studentLabel(request.studentId)}</TableCell>
										<TableCell>{campusesById.get(request.fromCampusId)?.name ?? `#${request.fromCampusId}`}</TableCell>
										<TableCell>{campusesById.get(request.toCampusId)?.name ?? `#${request.toCampusId}`}</TableCell>
										<TableCell>{request.reason ?? "—"}</TableCell>
										<TableCell onClick={(e) => e.stopPropagation()}>
											<Button size="small" onClick={() => handleApprove(request.id)}>
												Approve
											</Button>
											<Button size="small" color="error" onClick={() => handleReject(request.id)}>
												Reject
											</Button>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>
		</Paper>
	);
}
