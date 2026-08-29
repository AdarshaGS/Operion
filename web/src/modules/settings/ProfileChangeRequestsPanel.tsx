import { useEffect, useState } from "react";
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
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import {
	approveProfileChangeRequest,
	listProfileChangeRequests,
	rejectProfileChangeRequest,
	type ProfileChangeRequestResponse,
} from "../../api/profileChangeRequests";

/** Org-wide "pending approvals" inbox for self-service profile edits - same shape as
 * LeaveRequestsInboxPanel, backed by GET /api/v1/profile-change-requests?status=PENDING. */
export function ProfileChangeRequestsPanel() {
	const [requests, setRequests] = useState<ProfileChangeRequestResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		listProfileChangeRequests("PENDING")
			.then(setRequests)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load pending change requests"));
	}

	useEffect(refresh, []);

	async function handleApprove(id: number) {
		try {
			await approveProfileChangeRequest(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to approve change request");
		}
	}

	async function handleReject(id: number) {
		try {
			await rejectProfileChangeRequest(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to reject change request");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Pending profile change requests</Typography>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{requests.length === 0 && <Alert severity="info">No pending profile change requests.</Alert>}

				{requests.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Person</TableCell>
									<TableCell>Phone</TableCell>
									<TableCell>Email</TableCell>
									<TableCell>Photo URL</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{requests.map((request) => (
									<TableRow key={request.id}>
										<TableCell>Person #{request.personId}</TableCell>
										<TableCell>{request.phone ?? "—"}</TableCell>
										<TableCell>{request.email ?? "—"}</TableCell>
										<TableCell>{request.photoUrl ?? "—"}</TableCell>
										<TableCell>
											<Can anyOf={["PROFILE_CHANGE_MANAGE"]}>
												<Button size="small" onClick={() => handleApprove(request.id)}>
													Approve
												</Button>
												<Button size="small" color="error" onClick={() => handleReject(request.id)}>
													Reject
												</Button>
											</Can>
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
