import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { DialogErrorAlert } from "../../components/DialogErrorAlert";
import { ApiError } from "../../api/client";
import { loadErrorMessage } from "./loadError";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { listDepartments, type DepartmentResponse } from "../../api/departments";
import { listDesignations, type DesignationResponse } from "../../api/designations";
import { approveJobApplication, listJobApplications, rejectJobApplication, type JobApplicationResponse } from "../../api/jobApplications";

/** Org-wide "pending applications" inbox - same shape as LeaveRequestsInboxPanel,
 * backed by GET /api/v1/job-applications?status=PENDING. */
export function JobApplicationsPanel({ onHired }: { onHired?: () => void }) {
	const [applications, setApplications] = useState<JobApplicationResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [hiring, setHiring] = useState<JobApplicationResponse | null>(null);
	const [dialogError, setDialogError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);
	const [designations, setDesignations] = useState<DesignationResponse[]>([]);
	const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [designationId, setDesignationId] = useState<number | "">("");
	const [employmentType, setEmploymentType] = useState("PERMANENT");
	const [dateOfJoining, setDateOfJoining] = useState(new Date().toISOString().slice(0, 10));
	const [employeeCode, setEmployeeCode] = useState("");
	const [campusId, setCampusId] = useState<number | "">("");
	const [departmentId, setDepartmentId] = useState<number | "">("");

	function refresh() {
		listJobApplications("PENDING")
			.then(setApplications)
			.catch((err) => setError(loadErrorMessage(err, "Failed to load pending applications")));
	}

	useEffect(refresh, []);
	useEffect(() => {
		listDesignations().then(setDesignations).catch(() => {});
		listDepartments().then(setDepartments).catch(() => {});
		listCampuses().then(setCampuses).catch(() => {});
	}, []);

	function openHireDialog(application: JobApplicationResponse) {
		setHiring(application);
		setDialogError(null);
	}

	/** Approving hires the applicant, so the details a staff profile needs are collected here. */
	async function handleHire(event: FormEvent) {
		event.preventDefault();
		if (!hiring || designationId === "") return;
		setSubmitting(true);
		setDialogError(null);
		try {
			await approveJobApplication(hiring.id, {
				designationId,
				employmentType,
				dateOfJoining,
				employeeCode,
				campusId: campusId === "" ? null : campusId,
				departmentId: departmentId === "" ? null : departmentId,
			});
			setHiring(null);
			setEmployeeCode("");
			setDesignationId("");
			refresh();
			onHired?.();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : "Failed to approve application");
		} finally {
			setSubmitting(false);
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

				{applications.length === 0 && !error && <Alert severity="info">No pending job applications.</Alert>}

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
											<Button size="small" onClick={() => openHireDialog(application)}>
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

			<Dialog open={hiring !== null} onClose={() => setHiring(null)} component="form" onSubmit={handleHire} fullWidth maxWidth="xs">
				<DialogTitle>Approve and hire {hiring?.applicantName}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{dialogError && <DialogErrorAlert>{dialogError}</DialogErrorAlert>}
						<TextField select label="Designation" value={designationId} onChange={(e) => setDesignationId(Number(e.target.value))} required fullWidth>
							{designations.map((d) => (
								<MenuItem key={d.id} value={d.id}>
									{d.name}
								</MenuItem>
							))}
						</TextField>
						<TextField select label="Employment type" value={employmentType} onChange={(e) => setEmploymentType(e.target.value)} required fullWidth>
							{["PERMANENT", "CONTRACT", "PART_TIME"].map((t) => (
								<MenuItem key={t} value={t}>
									{t}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Date of joining" type="date" value={dateOfJoining} onChange={(e) => setDateOfJoining(e.target.value)} required fullWidth slotProps={{ inputLabel: { shrink: true } }} />
						<TextField label="Employee code" value={employeeCode} onChange={(e) => setEmployeeCode(e.target.value)} required fullWidth />
						<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value === "" ? "" : Number(e.target.value))} fullWidth>
							<MenuItem value="">Org-wide</MenuItem>
							{campuses.map((c) => (
								<MenuItem key={c.id} value={c.id}>
									{c.name}
								</MenuItem>
							))}
						</TextField>
						<TextField select label="Department" value={departmentId} onChange={(e) => setDepartmentId(e.target.value === "" ? "" : Number(e.target.value))} fullWidth>
							<MenuItem value="">None</MenuItem>
							{departments.map((d) => (
								<MenuItem key={d.id} value={d.id}>
									{d.name}
								</MenuItem>
							))}
						</TextField>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setHiring(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || designationId === ""}>
						Approve and hire
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
