import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
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
import {
	convertApplicant,
	inquireApplicant,
	listApplicants,
	rejectApplicant,
	type ApplicantResponse,
} from "../../api/applicants";
import { ApiError } from "../../api/client";
import { createPerson, listPersons, type PersonResponse } from "../../api/persons";

const INQUIRY_FORM = { firstName: "", lastName: "", dateOfBirth: "", gender: "", phone: "", email: "", inquiryDate: "", source: "", notes: "" };
const CONVERT_FORM = {
	admissionNumber: "",
	admissionDate: "",
	previousSchool: "",
	bloodGroup: "",
	category: "",
	nationality: "",
	remarks: "",
	medicalAlerts: "",
	emergencyContactName: "",
	emergencyContactPhone: "",
};

/** Pre-admission inquiries (#114) - converting here admits the applicant's own Person
 * as a Student (no name/DOB/gender re-entry); rejecting just closes the inquiry. */
export function ApplicantsPanel() {
	const navigate = useNavigate();
	const [applicants, setApplicants] = useState<ApplicantResponse[]>([]);
	const [personsById, setPersonsById] = useState<Map<number, PersonResponse>>(new Map());
	const [error, setError] = useState<string | null>(null);
	const [inquiryOpen, setInquiryOpen] = useState(false);
	const [inquiryForm, setInquiryForm] = useState(INQUIRY_FORM);
	const [convertTarget, setConvertTarget] = useState<ApplicantResponse | null>(null);
	const [convertForm, setConvertForm] = useState(CONVERT_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listApplicants()
			.then((all) => setApplicants(all.filter((a) => a.status === "INQUIRY")))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load applicants"));
		listPersons()
			.then((persons) => setPersonsById(new Map(persons.map((p) => [p.id, p]))))
			.catch(() => {});
	}

	useEffect(refresh, []);

	function personLabel(personId: number): string {
		const person = personsById.get(personId);
		return person ? `${person.firstName} ${person.lastName}` : `Person #${personId}`;
	}

	async function handleReject(id: number) {
		try {
			await rejectApplicant(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to reject applicant");
		}
	}

	async function handleInquirySubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			const person = await createPerson({
				firstName: inquiryForm.firstName,
				lastName: inquiryForm.lastName,
				dateOfBirth: inquiryForm.dateOfBirth || null,
				gender: inquiryForm.gender || null,
				phone: inquiryForm.phone || null,
				email: inquiryForm.email || null,
			});
			await inquireApplicant({
				personId: person.id,
				inquiryDate: inquiryForm.inquiryDate,
				source: inquiryForm.source || null,
				notes: inquiryForm.notes || null,
			});
			setInquiryForm(INQUIRY_FORM);
			setInquiryOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record inquiry");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleConvertSubmit(event: FormEvent) {
		event.preventDefault();
		if (!convertTarget) return;
		setSubmitting(true);
		try {
			const student = await convertApplicant(convertTarget.id, {
				admissionNumber: convertForm.admissionNumber || null,
				admissionDate: convertForm.admissionDate,
				previousSchool: convertForm.previousSchool || null,
				bloodGroup: convertForm.bloodGroup || null,
				category: convertForm.category || null,
				nationality: convertForm.nationality || null,
				remarks: convertForm.remarks || null,
				medicalAlerts: convertForm.medicalAlerts || null,
				emergencyContactName: convertForm.emergencyContactName || null,
				emergencyContactPhone: convertForm.emergencyContactPhone || null,
			});
			setConvertTarget(null);
			setConvertForm(CONVERT_FORM);
			navigate(`/students/${student.id}`);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to convert applicant");
		} finally {
			setSubmitting(false);
		}
	}

	function setInquiryField<K extends keyof typeof INQUIRY_FORM>(key: K) {
		return (event: React.ChangeEvent<HTMLInputElement>) => setInquiryForm((prev) => ({ ...prev, [key]: event.target.value }));
	}

	function setConvertField<K extends keyof typeof CONVERT_FORM>(key: K) {
		return (event: React.ChangeEvent<HTMLInputElement>) => setConvertForm((prev) => ({ ...prev, [key]: event.target.value }));
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Applicants</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setInquiryOpen(true)}>
						Record inquiry
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{applicants.length === 0 && <Alert severity="info">No applicants under inquiry.</Alert>}

				{applicants.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Applicant</TableCell>
									<TableCell>Inquiry date</TableCell>
									<TableCell>Source</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{applicants.map((applicant) => (
									<TableRow key={applicant.id}>
										<TableCell>{personLabel(applicant.personId)}</TableCell>
										<TableCell>{applicant.inquiryDate}</TableCell>
										<TableCell>{applicant.source ?? "—"}</TableCell>
										<TableCell>
											<Button size="small" onClick={() => setConvertTarget(applicant)}>
												Convert to student
											</Button>
											<Button size="small" color="error" onClick={() => handleReject(applicant.id)}>
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

			<Dialog open={inquiryOpen} onClose={() => setInquiryOpen(false)} component="form" onSubmit={handleInquirySubmit} fullWidth maxWidth="sm">
				<DialogTitle>Record inquiry</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField label="First name" value={inquiryForm.firstName} onChange={setInquiryField("firstName")} required autoFocus fullWidth />
							<TextField label="Last name" value={inquiryForm.lastName} onChange={setInquiryField("lastName")} required fullWidth />
						</Box>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField
								label="Date of birth"
								type="date"
								value={inquiryForm.dateOfBirth}
								onChange={setInquiryField("dateOfBirth")}
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
							<TextField label="Gender" value={inquiryForm.gender} onChange={setInquiryField("gender")} fullWidth />
						</Box>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField label="Phone" value={inquiryForm.phone} onChange={setInquiryField("phone")} fullWidth />
							<TextField label="Email" type="email" value={inquiryForm.email} onChange={setInquiryField("email")} fullWidth />
						</Box>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField
								label="Inquiry date"
								type="date"
								value={inquiryForm.inquiryDate}
								onChange={setInquiryField("inquiryDate")}
								required
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
							<TextField label="Source" placeholder="Walk-in, referral, website..." value={inquiryForm.source} onChange={setInquiryField("source")} fullWidth />
						</Box>
						<TextField label="Notes" value={inquiryForm.notes} onChange={setInquiryField("notes")} multiline rows={2} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setInquiryOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={convertTarget !== null} onClose={() => setConvertTarget(null)} component="form" onSubmit={handleConvertSubmit} fullWidth maxWidth="sm">
				<DialogTitle>Convert {convertTarget ? personLabel(convertTarget.personId) : ""} to a student</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField
								label="Admission number"
								helperText="Leave blank to auto-generate"
								value={convertForm.admissionNumber}
								onChange={setConvertField("admissionNumber")}
								fullWidth
							/>
							<TextField
								label="Admission date"
								type="date"
								value={convertForm.admissionDate}
								onChange={setConvertField("admissionDate")}
								required
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
						</Box>
						<TextField label="Previous school" value={convertForm.previousSchool} onChange={setConvertField("previousSchool")} fullWidth />
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField label="Blood group" value={convertForm.bloodGroup} onChange={setConvertField("bloodGroup")} fullWidth />
							<TextField label="Category" value={convertForm.category} onChange={setConvertField("category")} fullWidth />
							<TextField label="Nationality" value={convertForm.nationality} onChange={setConvertField("nationality")} fullWidth />
						</Box>
						<TextField label="Medical alerts / allergies" value={convertForm.medicalAlerts} onChange={setConvertField("medicalAlerts")} multiline rows={2} fullWidth />
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField label="Emergency contact name" value={convertForm.emergencyContactName} onChange={setConvertField("emergencyContactName")} fullWidth />
							<TextField label="Emergency contact phone" value={convertForm.emergencyContactPhone} onChange={setConvertField("emergencyContactPhone")} fullWidth />
						</Box>
						<TextField label="Remarks" value={convertForm.remarks} onChange={setConvertField("remarks")} multiline rows={2} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setConvertTarget(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Admit student
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
