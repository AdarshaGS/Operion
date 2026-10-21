import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link as RouterLink } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import MenuItem from "@mui/material/MenuItem";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { Can } from "../../auth/Can";
import { convertApplicant, inquireApplicant } from "../../api/applicants";
import { createPerson, GENDER_OPTIONS } from "../../api/persons";
import {
	admissionApplicantName,
	getAdmissionStatusCounts,
	getApplicantDocuments,
	listAdmissionApplicants,
	rejectApplicantWithReason,
	saveApplicantDocuments,
	startApplicantReview,
	type AdmissionApplicantResponse,
	type AdmissionDocument,
	type AdmissionStatusCounts,
} from "../../api/admissions";
import { ApiError } from "../../api/client";
import { DOC_TYPES, STATUS_FILTERS, allowedActions, docLabel, publicFormUrl, statusColor, statusLabel, type StatusFilter } from "./admissionStatus";

const SLUG_KEY = "operion.admissions.orgSlug";
const SEARCH_FETCH_SIZE = 200;
const NEW_APPLICANT = { firstName: "", lastName: "", dateOfBirth: "", gender: "", phone: "", email: "", source: "", notes: "" };
const CONVERT_FORM = { admissionNumber: "", admissionDate: "", admissionSource: "", previousSchool: "", tcNumber: "", entranceScore: "" };

function errMsg(err: unknown, fallback: string) {
	return err instanceof ApiError ? err.message : fallback;
}

/** Admissions review queue (#219): public inquiries -> start review -> approve (admit as a
 * student) or reject, with a per-applicant document checklist. */
export function AdmissionsPage() {
	const [filter, setFilter] = useState<StatusFilter>("ALL");
	const [counts, setCounts] = useState<AdmissionStatusCounts | null>(null);
	const [rows, setRows] = useState<AdmissionApplicantResponse[]>([]);
	const [total, setTotal] = useState(0);
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(20);
	const [error, setError] = useState<string | null>(null);
	const [busyId, setBusyId] = useState<number | null>(null);

	const [approveTarget, setApproveTarget] = useState<AdmissionApplicantResponse | null>(null);
	const [convertForm, setConvertForm] = useState(CONVERT_FORM);
	const [createdStudentId, setCreatedStudentId] = useState<number | null>(null);
	const [approveError, setApproveError] = useState<string | null>(null);
	const [rejectTarget, setRejectTarget] = useState<AdmissionApplicantResponse | null>(null);
	const [reason, setReason] = useState("");
	const [docsTarget, setDocsTarget] = useState<AdmissionApplicantResponse | null>(null);
	const [docs, setDocs] = useState<AdmissionDocument[]>([]);
	const [docsLoading, setDocsLoading] = useState(false);
	const [linkOpen, setLinkOpen] = useState(false);
	const [slug, setSlug] = useState(() => {
		try {
			return localStorage.getItem(SLUG_KEY) ?? "";
		} catch {
			return "";
		}
	});
	const [copied, setCopied] = useState(false);
	const [submitting, setSubmitting] = useState(false);
	const [query, setQuery] = useState("");
	const [addOpen, setAddOpen] = useState(false);
	const [addForm, setAddForm] = useState(NEW_APPLICANT);
	const [addError, setAddError] = useState<string | null>(null);

	// The applicants endpoint has no text search, so while a query is typed the first SEARCH_FETCH_SIZE rows of the
	// selected status are fetched and filtered here (pagination is hidden meanwhile).
	const searching = query.trim() !== "";

	const refresh = useCallback(() => {
		getAdmissionStatusCounts().then(setCounts).catch(() => {});
		listAdmissionApplicants(filter, searching ? 0 : page, searching ? SEARCH_FETCH_SIZE : size)
			.then((result) => {
				setRows(result.content);
				setTotal(result.totalElements);
			})
			.catch((err) => setError(errMsg(err, "Failed to load applicants")));
	}, [filter, page, size, searching]);

	useEffect(refresh, [refresh]);

	async function handleAddApplicant(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setAddError(null);
		try {
			const person = await createPerson({
				firstName: addForm.firstName,
				lastName: addForm.lastName,
				dateOfBirth: addForm.dateOfBirth || null,
				gender: addForm.gender,
				phone: addForm.phone || null,
				email: addForm.email || null,
			});
			await inquireApplicant({
				personId: person.id,
				inquiryDate: new Date().toISOString().slice(0, 10),
				source: addForm.source || null,
				notes: addForm.notes || null,
			});
			setAddForm(NEW_APPLICANT);
			setAddOpen(false);
			refresh();
		} catch (err) {
			setAddError(errMsg(err, "Failed to add applicant"));
		} finally {
			setSubmitting(false);
		}
	}

	function setAddField<K extends keyof typeof NEW_APPLICANT>(key: K) {
		return (event: React.ChangeEvent<HTMLInputElement>) => setAddForm((prev) => ({ ...prev, [key]: event.target.value }));
	}

	const needle = query.trim().toLowerCase();
	const visibleRows = needle
		? rows.filter((a) =>
				[admissionApplicantName(a), a.guardianName, a.guardianPhone, a.guardianEmail].some((v) => v?.toLowerCase().includes(needle)),
			)
		: rows;

	async function handleStartReview(a: AdmissionApplicantResponse) {
		setBusyId(a.id);
		setError(null);
		try {
			await startApplicantReview(a.id);
			refresh();
		} catch (err) {
			setError(errMsg(err, "Failed to start review"));
		} finally {
			setBusyId(null);
		}
	}

	async function handleApprove(event: FormEvent) {
		event.preventDefault();
		if (!approveTarget) return;
		setSubmitting(true);
		setApproveError(null);
		try {
			const student = await convertApplicant(approveTarget.id, {
				admissionNumber: convertForm.admissionNumber || null,
				admissionDate: convertForm.admissionDate,
				admissionSource: convertForm.admissionSource || null,
				previousSchool: convertForm.previousSchool || null,
				tcNumber: convertForm.tcNumber || null,
				entranceScore: convertForm.entranceScore ? Number(convertForm.entranceScore) : null,
			});
			setCreatedStudentId(student.id);
			setConvertForm(CONVERT_FORM);
			refresh();
		} catch (err) {
			setApproveError(errMsg(err, "Failed to approve applicant"));
		} finally {
			setSubmitting(false);
		}
	}

	function closeApprove() {
		setApproveTarget(null);
		setCreatedStudentId(null);
		setApproveError(null);
	}

	async function handleReject(event: FormEvent) {
		event.preventDefault();
		if (!rejectTarget || !reason.trim()) return;
		setSubmitting(true);
		try {
			await rejectApplicantWithReason(rejectTarget.id, reason.trim());
			setRejectTarget(null);
			setReason("");
			refresh();
		} catch (err) {
			setError(errMsg(err, "Failed to reject applicant"));
			setRejectTarget(null);
		} finally {
			setSubmitting(false);
		}
	}

	function openDocs(a: AdmissionApplicantResponse) {
		setDocsTarget(a);
		setDocsLoading(true);
		setDocs(DOC_TYPES.map((docType) => ({ docType, received: false })));
		getApplicantDocuments(a.id)
			.then((saved) => setDocs(DOC_TYPES.map((docType) => ({ docType, received: saved.find((d) => d.docType === docType)?.received ?? false }))))
			.catch((err) => setError(errMsg(err, "Failed to load documents")))
			.finally(() => setDocsLoading(false));
	}

	async function saveDocs() {
		if (!docsTarget) return;
		setSubmitting(true);
		try {
			await saveApplicantDocuments(docsTarget.id, docs);
			setDocsTarget(null);
		} catch (err) {
			setError(errMsg(err, "Failed to save documents"));
		} finally {
			setSubmitting(false);
		}
	}

	function updateSlug(value: string) {
		setSlug(value);
		setCopied(false);
		try {
			localStorage.setItem(SLUG_KEY, value);
		} catch {
			/* storage unavailable - the field still works for this session */
		}
	}

	const link = slug.trim() ? publicFormUrl(window.location.origin, slug) : "";

	async function copyLink() {
		try {
			await navigator.clipboard.writeText(link);
			setCopied(true);
		} catch {
			setError("Couldn't copy automatically - select the link and copy it manually");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
					<Typography variant="h6">Admissions</Typography>
					<Stack direction="row" spacing={1}>
						<Button size="small" variant="outlined" onClick={() => setLinkOpen(true)}>
							Public form link
						</Button>
						<Can anyOf={["STUDENT_MANAGE"]}>
							<Button size="small" variant="contained" startIcon={<AddIcon />} onClick={() => setAddOpen(true)}>
								Add applicant
							</Button>
						</Can>
					</Stack>
				</Box>

				<TextField
					size="small"
					label="Search applicants"
					placeholder="Applicant or guardian name, phone, email"
					value={query}
					onChange={(e) => setQuery(e.target.value)}
					sx={{ maxWidth: 420 }}
				/>

				<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", rowGap: 1 }}>
					{STATUS_FILTERS.map((s) => (
						<Chip
							key={s}
							label={`${statusLabel(s)}${counts ? `: ${counts[s] ?? 0}` : ""}`}
							color={filter === s ? "primary" : "default"}
							variant={filter === s ? "filled" : "outlined"}
							size="small"
							clickable
							onClick={() => {
								setFilter(s);
								setPage(0);
							}}
						/>
					))}
				</Stack>

				{error && (
					<Alert severity="error" onClose={() => setError(null)}>
						{error}
					</Alert>
				)}

				{visibleRows.length === 0 ? (
					<Alert severity="info">
						{searching
							? `No applicants${filter === "ALL" ? "" : ` marked ${statusLabel(filter)}`} match "${query.trim()}".`
							: `No applicants${filter === "ALL" ? " yet - use Add applicant, or share the public form link, to start receiving enquiries" : ` marked ${statusLabel(filter)}`}.`}
					</Alert>
				) : (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Applicant</TableCell>
									<TableCell>Date of birth</TableCell>
									<TableCell>Class</TableCell>
									<TableCell>Guardian</TableCell>
									<TableCell>Inquiry date</TableCell>
									<TableCell>Status</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{visibleRows.map((a) => {
									const can = allowedActions(a.status);
									return (
										<TableRow key={a.id}>
											<TableCell>{admissionApplicantName(a)}</TableCell>
											<TableCell>{a.dateOfBirth ?? "—"}</TableCell>
											<TableCell>{a.desiredGrade?.name ?? "—"}</TableCell>
											<TableCell>
												{a.guardianName ?? "—"}
												{a.guardianPhone && (
													<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
														{a.guardianPhone}
														{a.guardianEmail ? ` · ${a.guardianEmail}` : ""}
													</Typography>
												)}
											</TableCell>
											<TableCell>{a.inquiryDate}</TableCell>
											<TableCell>
												<Chip label={statusLabel(a.status)} color={statusColor(a.status)} size="small" />
												{a.status === "REJECTED" && a.rejectionReason && (
													<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
														{a.rejectionReason}
													</Typography>
												)}
											</TableCell>
											<TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
												<Button size="small" onClick={() => openDocs(a)}>
													Documents
												</Button>
												<Can anyOf={["STUDENT_MANAGE"]}>
													{can.startReview && (
														<Button size="small" disabled={busyId === a.id} onClick={() => handleStartReview(a)}>
															Start review
														</Button>
													)}
													{can.approve && (
														<Button size="small" color="success" onClick={() => setApproveTarget(a)}>
															Approve
														</Button>
													)}
													{can.reject && (
														<Button size="small" color="error" onClick={() => setRejectTarget(a)}>
															Reject
														</Button>
													)}
												</Can>
											</TableCell>
										</TableRow>
									);
								})}
							</TableBody>
						</Table>
					</TableContainer>
				)}

				{searching && total > SEARCH_FETCH_SIZE && (
					<Typography variant="caption" color="text.secondary">
						Searching the first {SEARCH_FETCH_SIZE} of {total} applicants - narrow by status for older records.
					</Typography>
				)}

				{!searching && (
				<TablePagination
					component="div"
					count={total}
					page={page}
					rowsPerPage={size}
					rowsPerPageOptions={[10, 20, 50]}
					onPageChange={(_, p) => setPage(p)}
					onRowsPerPageChange={(e) => {
						setSize(Number(e.target.value));
						setPage(0);
					}}
				/>
				)}
			</Stack>

			<Dialog open={addOpen} onClose={() => setAddOpen(false)} component="form" onSubmit={handleAddApplicant} fullWidth maxWidth="sm">
				<DialogTitle>Add applicant</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{addError && <Alert severity="error">{addError}</Alert>}
						<Stack direction="row" spacing={2}>
							<TextField label="First name" value={addForm.firstName} onChange={setAddField("firstName")} required autoFocus fullWidth />
							<TextField label="Last name" value={addForm.lastName} onChange={setAddField("lastName")} required fullWidth />
						</Stack>
						<Stack direction="row" spacing={2}>
							<TextField label="Date of birth" type="date" value={addForm.dateOfBirth} onChange={setAddField("dateOfBirth")} slotProps={{ inputLabel: { shrink: true } }} fullWidth />
							<TextField select label="Gender" value={addForm.gender} onChange={setAddField("gender")} required fullWidth>
								{GENDER_OPTIONS.map((g) => (
									<MenuItem key={g} value={g}>
										{g}
									</MenuItem>
								))}
							</TextField>
						</Stack>
						<Stack direction="row" spacing={2}>
							<TextField label="Phone" value={addForm.phone} onChange={setAddField("phone")} fullWidth />
							<TextField label="Email" type="email" value={addForm.email} onChange={setAddField("email")} fullWidth />
						</Stack>
						<TextField label="Source (walk-in, referral...)" value={addForm.source} onChange={setAddField("source")} fullWidth />
						<TextField label="Notes" value={addForm.notes} onChange={setAddField("notes")} multiline minRows={2} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAddOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={approveTarget !== null} onClose={closeApprove} component="form" onSubmit={handleApprove} fullWidth maxWidth="sm">
				<DialogTitle>Approve {approveTarget ? admissionApplicantName(approveTarget) : ""}</DialogTitle>
				<DialogContent>
					{createdStudentId !== null ? (
						<Alert severity="success" sx={{ mt: 1 }}>
							Admitted as a student.{" "}
							<RouterLink to={`/students/${createdStudentId}`}>Open student record</RouterLink>
						</Alert>
					) : (
						<Stack spacing={2} sx={{ mt: 1 }}>
							{approveError && <Alert severity="error">{approveError}</Alert>}
							<Typography variant="body2" color="text.secondary">
								Approving admits the applicant as a student. Other student details can be completed on their record afterwards.
							</Typography>
							<Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
								<TextField
									label="Admission number"
									helperText="Leave blank to auto-generate"
									value={convertForm.admissionNumber}
									onChange={(e) => setConvertForm((p) => ({ ...p, admissionNumber: e.target.value }))}
									fullWidth
								/>
								<TextField
									label="Admission date"
									type="date"
									value={convertForm.admissionDate}
									onChange={(e) => setConvertForm((p) => ({ ...p, admissionDate: e.target.value }))}
									required
									fullWidth
									slotProps={{ inputLabel: { shrink: true } }}
								/>
							</Stack>
							<Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
								<TextField label="Admission source" value={convertForm.admissionSource} onChange={(e) => setConvertForm((p) => ({ ...p, admissionSource: e.target.value }))} fullWidth />
								<TextField label="Previous school" value={convertForm.previousSchool} onChange={(e) => setConvertForm((p) => ({ ...p, previousSchool: e.target.value }))} fullWidth />
							</Stack>
							<Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
								<TextField label="TC number" value={convertForm.tcNumber} onChange={(e) => setConvertForm((p) => ({ ...p, tcNumber: e.target.value }))} fullWidth />
								<TextField label="Entrance score" type="number" value={convertForm.entranceScore} onChange={(e) => setConvertForm((p) => ({ ...p, entranceScore: e.target.value }))} fullWidth />
							</Stack>
						</Stack>
					)}
				</DialogContent>
				<DialogActions>
					<Button onClick={closeApprove}>{createdStudentId !== null ? "Close" : "Cancel"}</Button>
					{createdStudentId === null && (
						<Button type="submit" variant="contained" disabled={submitting || !convertForm.admissionDate}>
							Approve and admit
						</Button>
					)}
				</DialogActions>
			</Dialog>

			<Dialog open={rejectTarget !== null} onClose={() => setRejectTarget(null)} component="form" onSubmit={handleReject} fullWidth maxWidth="xs">
				<DialogTitle>Reject {rejectTarget ? admissionApplicantName(rejectTarget) : ""}</DialogTitle>
				<DialogContent>
					<TextField
						label="Reason"
						value={reason}
						onChange={(e) => setReason(e.target.value)}
						required
						autoFocus
						multiline
						rows={3}
						fullWidth
						sx={{ mt: 1 }}
					/>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setRejectTarget(null)}>Cancel</Button>
					<Button type="submit" color="error" variant="contained" disabled={submitting || !reason.trim()}>
						Reject
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={docsTarget !== null} onClose={() => setDocsTarget(null)} fullWidth maxWidth="xs">
				<DialogTitle>Documents - {docsTarget ? admissionApplicantName(docsTarget) : ""}</DialogTitle>
				<DialogContent>
					<Stack>
						{docs.map((d) => (
							<FormControlLabel
								key={d.docType}
								label={docLabel(d.docType)}
								control={
									<Checkbox
										checked={d.received}
										disabled={docsLoading}
										onChange={(e) => setDocs((prev) => prev.map((x) => (x.docType === d.docType ? { ...x, received: e.target.checked } : x)))}
									/>
								}
							/>
						))}
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDocsTarget(null)}>Close</Button>
					<Can anyOf={["STUDENT_MANAGE"]}>
						<Button variant="contained" onClick={saveDocs} disabled={submitting || docsLoading}>
							Save
						</Button>
					</Can>
				</DialogActions>
			</Dialog>

			<Dialog open={linkOpen} onClose={() => setLinkOpen(false)} fullWidth maxWidth="sm">
				<DialogTitle>Public admission form link</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<Typography variant="body2" color="text.secondary">
							Share this link with parents - no login needed. Enter your organisation's login identifier (the slug you sign in with).
						</Typography>
						<TextField label="Organisation slug" value={slug} onChange={(e) => updateSlug(e.target.value)} autoComplete="off" autoCapitalize="off" spellCheck={false} fullWidth />
						{link && <TextField label="Link" value={link} fullWidth slotProps={{ input: { readOnly: true } }} onFocus={(e) => e.target.select()} />}
						{copied && <Alert severity="success">Link copied.</Alert>}
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setLinkOpen(false)}>Close</Button>
					<Button variant="contained" onClick={copyLink} disabled={!link}>
						Copy link
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
