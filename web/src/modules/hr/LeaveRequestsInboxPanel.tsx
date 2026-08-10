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
import { approveLeaveRequest, listLeaveRequests, rejectLeaveRequest, type LeaveRequestResponse } from "../../api/leaveRequests";
import { listLeaveTypes, type LeaveTypeResponse } from "../../api/leaveTypes";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listStaffProfiles, type StaffProfileResponse } from "../../api/staffProfiles";

/** Org-wide "pending approvals" inbox - the natural core-flow entry point for HR, backed by
 * the new staffProfileId-less GET /api/v1/hr/leave-requests?status=PENDING added for this. */
export function LeaveRequestsInboxPanel() {
	const navigate = useNavigate();
	const [requests, setRequests] = useState<LeaveRequestResponse[]>([]);
	const [staffById, setStaffById] = useState<Map<number, StaffProfileResponse>>(new Map());
	const [personsById, setPersonsById] = useState<Map<number, PersonResponse>>(new Map());
	const [leaveTypesById, setLeaveTypesById] = useState<Map<number, LeaveTypeResponse>>(new Map());
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		listLeaveRequests({ status: "PENDING" })
			.then(setRequests)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load pending leave requests"));
	}

	useEffect(refresh, []);
	useEffect(() => {
		listStaffProfiles()
			.then((staff) => setStaffById(new Map(staff.map((s) => [s.id, s]))))
			.catch(() => {});
		listPersons()
			.then((persons) => setPersonsById(new Map(persons.map((p) => [p.id, p]))))
			.catch(() => {});
		listLeaveTypes()
			.then((types) => setLeaveTypesById(new Map(types.map((t) => [t.id, t]))))
			.catch(() => {});
	}, []);

	function staffLabel(staffProfileId: number): string {
		const profile = staffById.get(staffProfileId);
		if (!profile) return `Staff #${staffProfileId}`;
		const person = personsById.get(profile.personId);
		return person ? `${person.firstName} ${person.lastName} (${profile.employeeCode})` : profile.employeeCode;
	}

	async function handleApprove(id: number) {
		try {
			await approveLeaveRequest(id, { decidedBy: 1 });
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to approve leave request");
		}
	}

	async function handleReject(id: number) {
		try {
			await rejectLeaveRequest(id, { decidedBy: 1 });
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to reject leave request");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Pending leave requests</Typography>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{requests.length === 0 && <Alert severity="info">No pending leave requests.</Alert>}

				{requests.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Staff</TableCell>
									<TableCell>Leave type</TableCell>
									<TableCell>Start</TableCell>
									<TableCell>End</TableCell>
									<TableCell>Days</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{requests.map((request) => (
									<TableRow
										key={request.id}
										hover
										sx={{ cursor: "pointer" }}
										onClick={() => navigate(`/hr/staff/${request.staffProfileId}`)}
									>
										<TableCell>{staffLabel(request.staffProfileId)}</TableCell>
										<TableCell>{leaveTypesById.get(request.leaveTypeId)?.name ?? `#${request.leaveTypeId}`}</TableCell>
										<TableCell>{request.startDate}</TableCell>
										<TableCell>{request.endDate}</TableCell>
										<TableCell>{request.numberOfDays}</TableCell>
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
