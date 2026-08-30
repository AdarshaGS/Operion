import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
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
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { Can } from "../../auth/Can";
import { useAuth } from "../../auth/AuthContext";
import { MemberStatusChip } from "../../components/MemberStatusChip";
import { StaffInviteDialog } from "../../components/StaffInviteDialog";
import { listAcademicYears, type AcademicYearResponse } from "../../api/academicYears";
import { ApiError } from "../../api/client";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { listDepartments, type DepartmentResponse } from "../../api/departments";
import { listDesignations, type DesignationResponse } from "../../api/designations";
import { allocateLeaveBalance, getLeaveBalance, type LeaveBalanceResponse } from "../../api/leaveBalances";
import {
	approveLeaveRequest,
	cancelLeaveRequest,
	listLeaveRequests,
	raiseLeaveRequest,
	rejectLeaveRequest,
	type LeaveRequestResponse,
} from "../../api/leaveRequests";
import { listLeaveTypes, type LeaveTypeResponse } from "../../api/leaveTypes";
import { grantMembership, listMemberships, type MembershipResponse } from "../../api/memberships";
import { getPerson, type PersonResponse } from "../../api/persons";
import { listRoles, type RoleResponse } from "../../api/roles";
import {
	addStaffDocument,
	changeStaffStatus,
	getStaffBankDetails,
	getStaffProfile,
	listStaffAssignments,
	listStaffDocuments,
	listStaffExits,
	listStaffProfiles,
	recordStaffExit,
	transferStaff,
	upsertStaffBankDetails,
	verifyStaffDocument,
	type StaffAssignmentResponse,
	type StaffBankDetailResponse,
	type StaffDocumentResponse,
	type StaffExitResponse,
	type StaffProfileResponse,
} from "../../api/staffProfiles";
import { inviteUser, type StaffInviteResponse } from "../../api/users";

/** ON_LEAVE is deliberately excluded - it's derived automatically by StaffLeaveStatusScheduler
 * from approved leave requests, never a manually-set target (see StaffProfileStatus's own doc). */
const STAFF_STATUSES = ["ACTIVE", "RESIGNED", "TERMINATED"];
const EXIT_TYPES = ["RESIGNATION", "TERMINATION", "RETIREMENT", "CONTRACT_END"];

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

function documentExpiryChip(expiryDate: string | null) {
	if (!expiryDate) return null;
	const daysUntil = (new Date(expiryDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24);
	if (daysUntil < 0) return <Chip label={`Expired ${expiryDate}`} size="small" color="error" />;
	if (daysUntil <= 30) return <Chip label={`Expires ${expiryDate}`} size="small" color="warning" />;
	return <Chip label={`Expires ${expiryDate}`} size="small" variant="outlined" />;
}

/** No GET-by-id exists for StaffProfile - resolving via list+find, same tradeoff documented
 * for BookDetailPage/RouteDetailPage/ItemDetailPage at this data scale. */
export function StaffDetailPage() {
	const { staffProfileId } = useParams<{ staffProfileId: string }>();
	const navigate = useNavigate();

	const [profile, setProfile] = useState<StaffProfileResponse | null>(null);
	const [person, setPerson] = useState<PersonResponse | null>(null);
	const [leaveTypes, setLeaveTypes] = useState<LeaveTypeResponse[]>([]);
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [documents, setDocuments] = useState<StaffDocumentResponse[]>([]);
	const [leaveRequests, setLeaveRequests] = useState<LeaveRequestResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);

	const [statusValue, setStatusValue] = useState("ACTIVE");

	const { hasAnyPermission } = useAuth();
	const canViewPayroll = hasAnyPermission(["HR_PAYROLL_VIEW"]);

	const [memberStatus, setMemberStatus] = useState<string | null>(null);
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
	const [designations, setDesignations] = useState<DesignationResponse[]>([]);
	const [assignments, setAssignments] = useState<StaffAssignmentResponse[]>([]);
	const [exits, setExits] = useState<StaffExitResponse[]>([]);
	const [bankDetails, setBankDetails] = useState<StaffBankDetailResponse | null>(null);
	const [allStaffProfiles, setAllStaffProfiles] = useState<StaffProfileResponse[]>([]);
	const [grantDialogOpen, setGrantDialogOpen] = useState(false);
	const [grantRoleId, setGrantRoleId] = useState<number | "">("");
	const [grantCampusId, setGrantCampusId] = useState<number | "">("");
	const [grantDepartmentId, setGrantDepartmentId] = useState<number | "">("");
	const [grantSubmitting, setGrantSubmitting] = useState(false);
	const [issuedInvite, setIssuedInvite] = useState<StaffInviteResponse | null>(null);

	const [documentDialogOpen, setDocumentDialogOpen] = useState(false);
	const [documentType, setDocumentType] = useState("");
	const [fileReference, setFileReference] = useState("");
	const [fileName, setFileName] = useState("");
	const [documentExpiryDate, setDocumentExpiryDate] = useState("");

	const [transferDialogOpen, setTransferDialogOpen] = useState(false);
	const [transferCampusId, setTransferCampusId] = useState<number | "">("");
	const [transferDepartmentId, setTransferDepartmentId] = useState<number | "">("");
	const [transferDesignationId, setTransferDesignationId] = useState<number | "">("");
	const [transferEffectiveDate, setTransferEffectiveDate] = useState(todayIso());

	const [exitDialogOpen, setExitDialogOpen] = useState(false);
	const [exitType, setExitType] = useState("RESIGNATION");
	const [exitDate, setExitDate] = useState(todayIso());
	const [exitReason, setExitReason] = useState("");

	const [bankAccountHolderName, setBankAccountHolderName] = useState("");
	const [bankAccountNumber, setBankAccountNumber] = useState("");
	const [bankName, setBankName] = useState("");
	const [bankBranchCode, setBankBranchCode] = useState("");
	const [taxIdentifier, setTaxIdentifier] = useState("");
	const [bankSubmitting, setBankSubmitting] = useState(false);

	const [balanceLeaveTypeId, setBalanceLeaveTypeId] = useState("");
	const [balanceAcademicYearId, setBalanceAcademicYearId] = useState("");
	const [allocatedDays, setAllocatedDays] = useState("");
	const [balance, setBalance] = useState<LeaveBalanceResponse | null>(null);

	const [requestDialogOpen, setRequestDialogOpen] = useState(false);
	const [requestLeaveTypeId, setRequestLeaveTypeId] = useState("");
	const [requestAcademicYearId, setRequestAcademicYearId] = useState("");
	const [startDate, setStartDate] = useState(todayIso());
	const [endDate, setEndDate] = useState(todayIso());
	const [numberOfDays, setNumberOfDays] = useState("");
	const [reason, setReason] = useState("");

	useEffect(() => {
		if (!staffProfileId) return;
		getStaffProfile(Number(staffProfileId))
			.then(async (found) => {
				setProfile(found);
				setStatusValue(found.status);
				const fetchedPerson = await getPerson(found.personId);
				setPerson(fetchedPerson);
				refreshLoginAccess(fetchedPerson.id);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load staff profile"))
			.finally(() => setLoading(false));
		// For resolving the reporting-manager's display label only - reporting managers are
		// expected to be active staff, so the roster list (not getStaffProfile) is fine here.
		listStaffProfiles().then(setAllStaffProfiles).catch(() => {});
		listLeaveTypes().then(setLeaveTypes).catch(() => {});
		listAcademicYears().then(setAcademicYears).catch(() => {});
		listRoles().then(setRoles).catch(() => {});
		listCampuses().then(setCampuses).catch(() => {});
		listDepartments().then(setDepartments).catch(() => {});
		listDesignations().then(setDesignations).catch(() => {});
		refreshDocuments();
		refreshLeaveRequests();
		refreshAssignments();
		refreshExits();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [staffProfileId]);

	// Separate from the effect above - useAuth()'s permission set can still be loading on
	// first render, so gating this fetch on staffProfileId alone would capture a stale
	// canViewPayroll=false and never retry once permissions actually resolve to true.
	useEffect(() => {
		if (!staffProfileId || !canViewPayroll) return;
		refreshBankDetails();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [staffProfileId, canViewPayroll]);

	function refreshLoginAccess(personId: number) {
		listMemberships()
			.then((memberships: MembershipResponse[]) => setMemberStatus(memberships.find((m) => m.personId === personId)?.memberStatus ?? null))
			.catch(() => {});
	}

	async function handleGrantLoginAccess(event: FormEvent) {
		event.preventDefault();
		if (!person || grantRoleId === "") return;
		setGrantSubmitting(true);
		try {
			const invite = await inviteUser({ email: person.email ?? "", phone: person.phone });
			await grantMembership({
				userId: invite.userId,
				personId: person.id,
				roleId: grantRoleId,
				campusId: grantCampusId === "" ? null : grantCampusId,
				departmentId: grantDepartmentId === "" ? null : grantDepartmentId,
			});
			setGrantRoleId("");
			setGrantCampusId("");
			setGrantDepartmentId("");
			setGrantDialogOpen(false);
			setIssuedInvite(invite);
			refreshLoginAccess(person.id);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to grant login access");
		} finally {
			setGrantSubmitting(false);
		}
	}

	function refreshDocuments() {
		if (!staffProfileId) return;
		listStaffDocuments(Number(staffProfileId))
			.then(setDocuments)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load documents"));
	}

	function refreshLeaveRequests() {
		if (!staffProfileId) return;
		listLeaveRequests({ staffProfileId: Number(staffProfileId) })
			.then(setLeaveRequests)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load leave requests"));
	}

	function refreshAssignments() {
		if (!staffProfileId) return;
		listStaffAssignments(Number(staffProfileId)).then(setAssignments).catch(() => {});
	}

	function refreshExits() {
		if (!staffProfileId) return;
		listStaffExits(Number(staffProfileId)).then(setExits).catch(() => {});
	}

	function refreshBankDetails() {
		if (!staffProfileId) return;
		getStaffBankDetails(Number(staffProfileId))
			.then((details) => {
				setBankDetails(details);
				setBankAccountHolderName(details?.bankAccountHolderName ?? "");
				setBankAccountNumber(details?.bankAccountNumber ?? "");
				setBankName(details?.bankName ?? "");
				setBankBranchCode(details?.bankBranchCode ?? "");
				setTaxIdentifier(details?.taxIdentifier ?? "");
			})
			.catch(() => {});
	}

	function leaveTypeLabel(id: number): string {
		return leaveTypes.find((t) => t.id === id)?.name ?? `Leave type #${id}`;
	}

	async function handleStatusChange() {
		if (!staffProfileId) return;
		try {
			const updated = await changeStaffStatus(Number(staffProfileId), { status: statusValue });
			setProfile(updated);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update status");
		}
	}

	async function handleAddDocument(event: FormEvent) {
		event.preventDefault();
		if (!staffProfileId) return;
		setSubmitting(true);
		try {
			await addStaffDocument(Number(staffProfileId), { documentType, fileReference, fileName, expiryDate: documentExpiryDate || null });
			setDocumentType("");
			setFileReference("");
			setFileName("");
			setDocumentExpiryDate("");
			setDocumentDialogOpen(false);
			refreshDocuments();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add document");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleVerifyDocument(id: number, verificationStatus: string) {
		try {
			await verifyStaffDocument(id, { verificationStatus, verifiedBy: 1 });
			refreshDocuments();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to verify document");
		}
	}

	async function handleCheckBalance() {
		if (!staffProfileId || !balanceLeaveTypeId || !balanceAcademicYearId) return;
		try {
			setBalance(await getLeaveBalance(Number(staffProfileId), Number(balanceLeaveTypeId), Number(balanceAcademicYearId)));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to load leave balance");
		}
	}

	async function handleAllocate() {
		if (!staffProfileId || !balanceLeaveTypeId || !balanceAcademicYearId || !allocatedDays) return;
		try {
			const updated = await allocateLeaveBalance({
				staffProfileId: Number(staffProfileId),
				leaveTypeId: Number(balanceLeaveTypeId),
				academicYearId: Number(balanceAcademicYearId),
				allocatedDays: Number(allocatedDays),
			});
			setBalance(updated);
			setAllocatedDays("");
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to allocate leave balance");
		}
	}

	async function handleRaiseRequest(event: FormEvent) {
		event.preventDefault();
		if (!staffProfileId) return;
		setSubmitting(true);
		try {
			await raiseLeaveRequest({
				staffProfileId: Number(staffProfileId),
				leaveTypeId: Number(requestLeaveTypeId),
				academicYearId: Number(requestAcademicYearId),
				startDate,
				endDate,
				numberOfDays: Number(numberOfDays),
				reason: reason || null,
			});
			setNumberOfDays("");
			setReason("");
			setRequestDialogOpen(false);
			refreshLeaveRequests();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to raise leave request");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleApprove(id: number) {
		try {
			await approveLeaveRequest(id, { decidedBy: 1 });
			refreshLeaveRequests();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to approve leave request");
		}
	}

	async function handleReject(id: number) {
		try {
			await rejectLeaveRequest(id, { decidedBy: 1 });
			refreshLeaveRequests();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to reject leave request");
		}
	}

	async function handleCancel(id: number) {
		try {
			await cancelLeaveRequest(id);
			refreshLeaveRequests();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to cancel leave request");
		}
	}

	async function handleTransfer(event: FormEvent) {
		event.preventDefault();
		if (!staffProfileId || transferDesignationId === "") return;
		setSubmitting(true);
		try {
			const updated = await transferStaff(Number(staffProfileId), {
				campusId: transferCampusId === "" ? null : transferCampusId,
				departmentId: transferDepartmentId === "" ? null : transferDepartmentId,
				designationId: transferDesignationId,
				effectiveDate: transferEffectiveDate,
			});
			setProfile(updated);
			setTransferDialogOpen(false);
			refreshAssignments();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to transfer staff member");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleRecordExit(event: FormEvent) {
		event.preventDefault();
		if (!staffProfileId) return;
		setSubmitting(true);
		try {
			await recordStaffExit(Number(staffProfileId), { exitType, exitDate, reason: exitReason || null });
			const updated = await getStaffProfile(Number(staffProfileId));
			setProfile(updated);
			setStatusValue(updated.status);
			setExitDialogOpen(false);
			refreshExits();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record exit");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleSaveBankDetails(event: FormEvent) {
		event.preventDefault();
		if (!staffProfileId) return;
		setBankSubmitting(true);
		try {
			const saved = await upsertStaffBankDetails(Number(staffProfileId), {
				bankAccountHolderName: bankAccountHolderName || null,
				bankAccountNumber: bankAccountNumber || null,
				bankName: bankName || null,
				bankBranchCode: bankBranchCode || null,
				taxIdentifier: taxIdentifier || null,
			});
			setBankDetails(saved);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save bank details");
		} finally {
			setBankSubmitting(false);
		}
	}

	const reportingManager = allStaffProfiles.find((p) => p.id === profile?.reportingManagerId);
	const reportingManagerLabel = reportingManager ? `${reportingManager.employeeCode} — ${reportingManager.designationName}` : "";

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/hr")}>
					Back to HR
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				{person ? `${person.firstName} ${person.lastName}` : `Staff #${staffProfileId}`}
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="body1">
							{profile?.employeeCode} — {profile?.designationName} {profile?.departmentName ? `(${profile.departmentName})` : ""}
						</Typography>
						<Chip label={profile?.status} size="small" />
					</Box>
					{person?.address && <Typography variant="body2" color="text.secondary">{person.address}</Typography>}
					{profile?.reportingManagerId && (
						<Typography variant="body2" color="text.secondary">
							Reports to: {reportingManagerLabel}
						</Typography>
					)}
					<Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
						<TextField select label="Status" size="small" value={statusValue} onChange={(e) => setStatusValue(e.target.value)} sx={{ minWidth: 160 }}>
							{/* ON_LEAVE isn't a selectable target (scheduler-derived), but it must still
							 * appear here - disabled - so the select renders the current value instead of
							 * going blank when a staff member happens to be on leave right now. */}
							{profile?.status === "ON_LEAVE" && (
								<MenuItem value="ON_LEAVE" disabled>
									ON_LEAVE
								</MenuItem>
							)}
							{STAFF_STATUSES.map((status) => (
								<MenuItem key={status} value={status}>
									{status}
								</MenuItem>
							))}
						</TextField>
						<Button size="small" variant="outlined" onClick={handleStatusChange} disabled={statusValue === profile?.status}>
							Update status
						</Button>
						<Can anyOf={["HR_STAFF_MANAGE"]}>
							<Button size="small" variant="outlined" onClick={() => setTransferDialogOpen(true)}>
								Transfer
							</Button>
							<Button
								size="small"
								variant="outlined"
								color="error"
								onClick={() => setExitDialogOpen(true)}
								disabled={profile?.status === "RESIGNED" || profile?.status === "TERMINATED"}
							>
								Record exit
							</Button>
						</Can>
					</Box>
					{assignments.length > 1 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Designation</TableCell>
										<TableCell>Start</TableCell>
										<TableCell>End</TableCell>
										<TableCell>Status</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{assignments.map((assignment) => (
										<TableRow key={assignment.id}>
											<TableCell>{assignment.designationName}</TableCell>
											<TableCell>{assignment.startDate}</TableCell>
											<TableCell>{assignment.endDate ?? "—"}</TableCell>
											<TableCell>
												<Chip label={assignment.status} size="small" />
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
					{exits.length > 0 && (
						<Stack spacing={1}>
							<Typography variant="subtitle2">Exit history</Typography>
							{exits.map((exit) => (
								<Typography variant="body2" color="text.secondary" key={exit.id}>
									{exit.exitType} on {exit.exitDate}
									{exit.reason ? ` — ${exit.reason}` : ""}
								</Typography>
							))}
						</Stack>
					)}
				</Stack>
			</Paper>

			<Can anyOf={["HR_PAYROLL_VIEW"]}>
				<Paper sx={{ p: 3 }} component="form" onSubmit={handleSaveBankDetails}>
					<Stack spacing={2}>
						<Typography variant="h6">Bank &amp; tax details</Typography>
						<Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
							<TextField
								label="Account holder name"
								value={bankAccountHolderName}
								onChange={(e) => setBankAccountHolderName(e.target.value)}
								sx={{ minWidth: 220 }}
							/>
							<TextField
								label="Account number"
								value={bankAccountNumber}
								onChange={(e) => setBankAccountNumber(e.target.value)}
								sx={{ minWidth: 220 }}
							/>
							<TextField label="Bank name" value={bankName} onChange={(e) => setBankName(e.target.value)} sx={{ minWidth: 220 }} />
							<TextField
								label="Branch code"
								value={bankBranchCode}
								onChange={(e) => setBankBranchCode(e.target.value)}
								sx={{ minWidth: 180 }}
							/>
							<TextField label="Tax identifier" value={taxIdentifier} onChange={(e) => setTaxIdentifier(e.target.value)} sx={{ minWidth: 180 }} />
						</Box>
						<Box>
							<Button type="submit" size="small" variant="contained" disabled={bankSubmitting}>
								{bankDetails ? "Update" : "Save"}
							</Button>
						</Box>
					</Stack>
				</Paper>
			</Can>

			<Paper sx={{ p: 3 }}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="body1">Login access</Typography>
					{memberStatus ? (
						<MemberStatusChip status={memberStatus} />
					) : (
						<Can anyOf={["MEMBERSHIP_MANAGE"]}>
							<Button size="small" variant="outlined" onClick={() => setGrantDialogOpen(true)}>
								Grant login access
							</Button>
						</Can>
					)}
				</Box>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Documents</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setDocumentDialogOpen(true)}>
							Add document
						</Button>
					</Box>

					{documents.length === 0 && <Alert severity="info">No documents on file.</Alert>}

					{documents.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Type</TableCell>
										<TableCell>File name</TableCell>
										<TableCell>Expiry</TableCell>
										<TableCell>Verification</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{documents.map((doc) => (
										<TableRow key={doc.id}>
											<TableCell>{doc.documentType}</TableCell>
											<TableCell>{doc.fileName}</TableCell>
											<TableCell>{documentExpiryChip(doc.expiryDate) ?? "—"}</TableCell>
											<TableCell>
												<Chip label={doc.verificationStatus} size="small" />
											</TableCell>
											<TableCell>
												{doc.verificationStatus === "PENDING" && (
													<>
														<Button size="small" onClick={() => handleVerifyDocument(doc.id, "VERIFIED")}>
															Verify
														</Button>
														<Button size="small" color="error" onClick={() => handleVerifyDocument(doc.id, "REJECTED")}>
															Reject
														</Button>
													</>
												)}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Leave balance</Typography>
					<Box sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}>
						<TextField select label="Leave type" size="small" value={balanceLeaveTypeId} onChange={(e) => setBalanceLeaveTypeId(e.target.value)} sx={{ minWidth: 180 }}>
							{leaveTypes.map((type) => (
								<MenuItem key={type.id} value={type.id}>
									{type.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Academic year"
							size="small"
							value={balanceAcademicYearId}
							onChange={(e) => setBalanceAcademicYearId(e.target.value)}
							sx={{ minWidth: 180 }}
						>
							{academicYears.map((year) => (
								<MenuItem key={year.id} value={year.id}>
									{year.name}
								</MenuItem>
							))}
						</TextField>
						<Button size="small" onClick={handleCheckBalance} disabled={!balanceLeaveTypeId || !balanceAcademicYearId}>
							Check
						</Button>
						<TextField label="Allocate days" type="number" size="small" value={allocatedDays} onChange={(e) => setAllocatedDays(e.target.value)} sx={{ width: 130 }} />
						<Button
							size="small"
							variant="contained"
							onClick={handleAllocate}
							disabled={!balanceLeaveTypeId || !balanceAcademicYearId || !allocatedDays}
						>
							Allocate
						</Button>
					</Box>

					{balance && (
						<Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
							<Typography variant="body1" component="span">
								Allocated:
							</Typography>
							<Chip label={balance.allocatedDays} size="small" />
							<Typography variant="body1" component="span">
								— Remaining:
							</Typography>
							<Chip label={balance.remainingDays} size="small" color="success" />
						</Box>
					)}
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Leave requests</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setRequestDialogOpen(true)}>
							Raise request
						</Button>
					</Box>

					{leaveRequests.length === 0 && <Alert severity="info">No leave requests.</Alert>}

					{leaveRequests.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Leave type</TableCell>
										<TableCell>Start</TableCell>
										<TableCell>End</TableCell>
										<TableCell>Days</TableCell>
										<TableCell>Status</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{leaveRequests.map((request) => (
										<TableRow key={request.id}>
											<TableCell>{leaveTypeLabel(request.leaveTypeId)}</TableCell>
											<TableCell>{request.startDate}</TableCell>
											<TableCell>{request.endDate}</TableCell>
											<TableCell>{request.numberOfDays}</TableCell>
											<TableCell>
												<Chip label={request.status} size="small" />
											</TableCell>
											<TableCell>
												{request.status === "PENDING" && (
													<>
														<Button size="small" onClick={() => handleApprove(request.id)}>
															Approve
														</Button>
														<Button size="small" color="error" onClick={() => handleReject(request.id)}>
															Reject
														</Button>
														<Button size="small" onClick={() => handleCancel(request.id)}>
															Cancel
														</Button>
													</>
												)}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Dialog
				open={documentDialogOpen}
				onClose={() => setDocumentDialogOpen(false)}
				component="form"
				onSubmit={handleAddDocument}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Add document</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Document type"
							placeholder="ID_PROOF"
							value={documentType}
							onChange={(e) => setDocumentType(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<TextField label="File name" value={fileName} onChange={(e) => setFileName(e.target.value)} required fullWidth />
						<TextField
							label="File reference"
							placeholder="Storage key / URL"
							value={fileReference}
							onChange={(e) => setFileReference(e.target.value)}
							required
							fullWidth
						/>
						<TextField
							label="Expiry date (optional)"
							type="date"
							value={documentExpiryDate}
							onChange={(e) => setDocumentExpiryDate(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDocumentDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog
				open={requestDialogOpen}
				onClose={() => setRequestDialogOpen(false)}
				component="form"
				onSubmit={handleRaiseRequest}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Raise leave request</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							select
							label="Leave type"
							value={requestLeaveTypeId}
							onChange={(e) => setRequestLeaveTypeId(e.target.value)}
							required
							autoFocus
							fullWidth
						>
							{leaveTypes.map((type) => (
								<MenuItem key={type.id} value={type.id}>
									{type.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Academic year"
							value={requestAcademicYearId}
							onChange={(e) => setRequestAcademicYearId(e.target.value)}
							required
							fullWidth
						>
							{academicYears.map((year) => (
								<MenuItem key={year.id} value={year.id}>
									{year.name}
								</MenuItem>
							))}
						</TextField>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField
								label="Start date"
								type="date"
								value={startDate}
								onChange={(e) => setStartDate(e.target.value)}
								required
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
							<TextField
								label="End date"
								type="date"
								value={endDate}
								onChange={(e) => setEndDate(e.target.value)}
								required
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
						</Box>
						<TextField
							label="Number of days"
							type="number"
							value={numberOfDays}
							onChange={(e) => setNumberOfDays(e.target.value)}
							required
							fullWidth
						/>
						<TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} multiline rows={2} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setRequestDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Raise
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={grantDialogOpen} onClose={() => setGrantDialogOpen(false)} component="form" onSubmit={handleGrantLoginAccess} fullWidth maxWidth="xs">
				<DialogTitle>Grant login access</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Email" value={person?.email ?? ""} fullWidth disabled />
						<TextField
							select
							label="Role"
							value={grantRoleId}
							onChange={(e) => setGrantRoleId(e.target.value === "" ? "" : Number(e.target.value))}
							required
							fullWidth
						>
							{roles
								.filter((role) => role.status === "ACTIVE")
								.map((role) => (
									<MenuItem key={role.id} value={role.id}>
										{role.name}
									</MenuItem>
								))}
						</TextField>
						<TextField
							select
							label="Campus (optional — org-wide if left blank)"
							value={grantCampusId}
							onChange={(e) => setGrantCampusId(e.target.value === "" ? "" : Number(e.target.value))}
							fullWidth
						>
							<MenuItem value="">Org-wide</MenuItem>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Department (optional)"
							value={grantDepartmentId}
							onChange={(e) => setGrantDepartmentId(e.target.value === "" ? "" : Number(e.target.value))}
							fullWidth
						>
							<MenuItem value="">No department</MenuItem>
							{departments.map((department) => (
								<MenuItem key={department.id} value={department.id}>
									{department.name}
								</MenuItem>
							))}
						</TextField>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setGrantDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={grantSubmitting}>
						Grant access
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={transferDialogOpen} onClose={() => setTransferDialogOpen(false)} component="form" onSubmit={handleTransfer} fullWidth maxWidth="xs">
				<DialogTitle>Transfer staff member</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							select
							label="Campus (optional — org-wide if left blank)"
							value={transferCampusId}
							onChange={(e) => setTransferCampusId(e.target.value === "" ? "" : Number(e.target.value))}
							fullWidth
						>
							<MenuItem value="">Org-wide</MenuItem>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Department (optional)"
							value={transferDepartmentId}
							onChange={(e) => setTransferDepartmentId(e.target.value === "" ? "" : Number(e.target.value))}
							fullWidth
						>
							<MenuItem value="">No department</MenuItem>
							{departments.map((department) => (
								<MenuItem key={department.id} value={department.id}>
									{department.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Designation"
							value={transferDesignationId}
							onChange={(e) => setTransferDesignationId(e.target.value === "" ? "" : Number(e.target.value))}
							required
							fullWidth
						>
							{designations.map((designation) => (
								<MenuItem key={designation.id} value={designation.id}>
									{designation.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Effective date"
							type="date"
							value={transferEffectiveDate}
							onChange={(e) => setTransferEffectiveDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setTransferDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || transferDesignationId === ""}>
						Transfer
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={exitDialogOpen} onClose={() => setExitDialogOpen(false)} component="form" onSubmit={handleRecordExit} fullWidth maxWidth="xs">
				<DialogTitle>Record exit</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Exit type" value={exitType} onChange={(e) => setExitType(e.target.value)} required fullWidth>
							{EXIT_TYPES.map((type) => (
								<MenuItem key={type} value={type}>
									{type}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Exit date"
							type="date"
							value={exitDate}
							onChange={(e) => setExitDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField label="Reason" value={exitReason} onChange={(e) => setExitReason(e.target.value)} multiline rows={2} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setExitDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" color="error" disabled={submitting}>
						Record exit
					</Button>
				</DialogActions>
			</Dialog>

			<StaffInviteDialog invite={issuedInvite} onClose={() => setIssuedInvite(null)} />
		</Stack>
	);
}
