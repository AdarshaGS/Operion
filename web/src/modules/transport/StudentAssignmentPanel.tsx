import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import FormControlLabel from "@mui/material/FormControlLabel";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { listStudentEnrollments } from "../../api/enrollments";
import { type PersonResponse, listPersons } from "../../api/persons";
import { listRouteStops, listRoutes, type RouteResponse, type RouteStopResponse } from "../../api/transportRoutes";
import {
	assignStudentTransport,
	endStudentAssignment,
	listStudentAssignment,
	type StudentTransportAssignmentResponse,
} from "../../api/transportAssignments";
import { listStudents, type StudentResponse } from "../../api/students";

export function StudentAssignmentPanel() {
	const [students, setStudents] = useState<StudentResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [studentId, setStudentId] = useState("");
	const [enrollmentId, setEnrollmentId] = useState<number | null>(null);

	const [routes, setRoutes] = useState<RouteResponse[]>([]);
	const [stops, setStops] = useState<RouteStopResponse[]>([]);
	const [routeId, setRouteId] = useState("");
	const [routeStopId, setRouteStopId] = useState("");
	const [usesPickup, setUsesPickup] = useState(true);
	const [usesDrop, setUsesDrop] = useState(true);
	const [effectiveFrom, setEffectiveFrom] = useState(new Date().toISOString().slice(0, 10));

	const [assignment, setAssignment] = useState<StudentTransportAssignmentResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		listStudents().then(setStudents).catch(() => {});
		listPersons().then(setPersons).catch(() => {});
		listRoutes().then(setRoutes).catch(() => {});
	}, []);

	useEffect(() => {
		setEnrollmentId(null);
		setAssignment(null);
		if (!studentId) return;
		listStudentEnrollments(Number(studentId))
			.then((enrollments) => {
				const current = enrollments.find((e) => e.current);
				if (!current) {
					setError("This student has no current enrollment.");
					return;
				}
				setEnrollmentId(current.id);
				return listStudentAssignment(current.id);
			})
			.then((assignments) => {
				if (assignments && assignments.length > 0) setAssignment(assignments[0]);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load student"));
	}, [studentId]);

	useEffect(() => {
		setRouteStopId("");
		if (!routeId) {
			setStops([]);
			return;
		}
		listRouteStops(Number(routeId)).then(setStops).catch(() => {});
	}, [routeId]);

	function studentLabel(student: StudentResponse): string {
		const person = persons.find((p) => p.id === student.personId);
		return person ? `${person.firstName} ${person.lastName} (${student.admissionNumber})` : student.admissionNumber;
	}

	function routeName(id: number): string {
		return routes.find((r) => r.id === id)?.name ?? `Route #${id}`;
	}

	async function handleAssign(event: FormEvent) {
		event.preventDefault();
		if (!enrollmentId) return;
		setSubmitting(true);
		try {
			const created = await assignStudentTransport({
				studentEnrollmentId: enrollmentId,
				routeId: Number(routeId),
				routeStopId: Number(routeStopId),
				usesPickup,
				usesDrop,
				effectiveFrom,
			});
			setAssignment(created);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to assign transport");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleEnd() {
		if (!assignment) return;
		setSubmitting(true);
		try {
			const ended = await endStudentAssignment(assignment.id, new Date().toISOString().slice(0, 10));
			setAssignment(ended);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to end assignment");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Student transport</Typography>

				<TextField select label="Student" value={studentId} onChange={(e) => setStudentId(e.target.value)} sx={{ maxWidth: 400 }}>
					{students.map((student) => (
						<MenuItem key={student.id} value={student.id}>
							{studentLabel(student)}
						</MenuItem>
					))}
				</TextField>

				{error && <Alert severity="error">{error}</Alert>}

				{enrollmentId && assignment && assignment.status === "ACTIVE" && (
					<Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
						<Typography>
							{routeName(assignment.routeId)} — {assignment.usesPickup ? "Pickup" : ""}
							{assignment.usesPickup && assignment.usesDrop ? " & " : ""}
							{assignment.usesDrop ? "Drop" : ""}
						</Typography>
						<Chip label={assignment.status} size="small" />
						<Button size="small" color="error" onClick={handleEnd} disabled={submitting}>
							End assignment
						</Button>
					</Box>
				)}

				{enrollmentId && (!assignment || assignment.status !== "ACTIVE") && (
					<Stack spacing={2} component="form" onSubmit={handleAssign} sx={{ maxWidth: 400 }}>
						<TextField select label="Route" value={routeId} onChange={(e) => setRouteId(e.target.value)} required fullWidth>
							{routes.map((route) => (
								<MenuItem key={route.id} value={route.id}>
									{route.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Stop"
							value={routeStopId}
							onChange={(e) => setRouteStopId(e.target.value)}
							required
							disabled={!routeId}
							fullWidth
						>
							{stops.map((stop) => (
								<MenuItem key={stop.id} value={stop.id}>
									{stop.stopName}
								</MenuItem>
							))}
						</TextField>
						<Box sx={{ display: "flex", gap: 2 }}>
							<FormControlLabel control={<Checkbox checked={usesPickup} onChange={(e) => setUsesPickup(e.target.checked)} />} label="Pickup" />
							<FormControlLabel control={<Checkbox checked={usesDrop} onChange={(e) => setUsesDrop(e.target.checked)} />} label="Drop" />
						</Box>
						<TextField
							label="Effective from"
							type="date"
							value={effectiveFrom}
							onChange={(e) => setEffectiveFrom(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<Box>
							<Button type="submit" variant="contained" disabled={submitting || !routeStopId}>
								Assign
							</Button>
						</Box>
					</Stack>
				)}
			</Stack>
		</Paper>
	);
}
