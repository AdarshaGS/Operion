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
import { ApiError } from "../../api/client";
import { approveJobApplication, listJobApplications, rejectJobApplication, type JobApplicationResponse } from "../../api/jobApplications";

/** Org-wide "pending applications" inbox - same shape as LeaveRequestsInboxPanel,
 * backed by GET /api/v1/job-applications?status=PENDING. */
export function JobApplicationsPanel() {
	const [applications, setApplications] = useState<JobApplicationResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		listJobApplications("PENDING")
			.then(setApplications)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load pending applications"));
	}

	useEffect(refresh, []);

	async function handleApprove(id: number) {
		try {
			await approveJobApplication(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to approve application");
		}
	}

	async function handleReject(id: number) {
		try {
			await rejectJobApplication(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to reject application");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Pending job applications</Typography>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{applications.length === 0 && <Alert severity="info">No pending job applications.</Alert>}

				{applications.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Applicant</TableCell>
									<TableCell>Email</TableCell>
									<TableCell>Specialization</TableCell>
									<TableCell>Experience</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{applications.map((application) => (
									<TableRow key={application.id}>
										<TableCell>{application.applicantName}</TableCell>
										<TableCell>{application.email}</TableCell>
										<TableCell>{application.specialization ?? "—"}</TableCell>
										<TableCell>{application.yearsExperience ?? "—"}</TableCell>
										<TableCell>
											<Button size="small" onClick={() => handleApprove(application.id)}>
												Approve
											</Button>
											<Button size="small" color="error" onClick={() => handleReject(application.id)}>
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
