import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../api/client";
import { getAdmissionFormOptions, submitAdmissionInquiry, type AdmissionFormOptions } from "../api/publicAdmissions";
import { Wordmark } from "../branding/Wordmark";
import { colors } from "../theme";

const EMPTY = { firstName: "", lastName: "", dateOfBirth: "", gender: "", desiredGradeId: "", guardianName: "", guardianPhone: "", guardianEmail: "", notes: "" };
type FormState = typeof EMPTY;

function validateInquiry(form: FormState, today: string): Partial<Record<keyof FormState, string>> {
	const errors: Partial<Record<keyof FormState, string>> = {};
	if (!form.firstName.trim()) errors.firstName = "Required";
	if (!form.lastName.trim()) errors.lastName = "Required";
	if (!form.dateOfBirth) errors.dateOfBirth = "Required";
	else if (form.dateOfBirth >= today) errors.dateOfBirth = "Must be in the past";
	if (!form.guardianName.trim()) errors.guardianName = "Required";
	if (!form.guardianPhone.trim()) errors.guardianPhone = "Required";
	else if (form.guardianPhone.replace(/\D/g, "").length < 7) errors.guardianPhone = "Enter a valid phone number";
	if (form.guardianEmail.trim() && !/^\S+@\S+\.\S+$/.test(form.guardianEmail.trim())) errors.guardianEmail = "Enter a valid email";
	return errors;
}

/** Public, unauthenticated admission inquiry form (#218) - same trust tier as the careers page. */
export function PublicAdmissionPage() {
	const { orgSlug = "" } = useParams();
	const [options, setOptions] = useState<AdmissionFormOptions | null>(null);
	const [loadError, setLoadError] = useState<string | null>(null);
	const [form, setForm] = useState<FormState>(EMPTY);
	const [website, setWebsite] = useState("");
	const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({});
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);
	const [submitted, setSubmitted] = useState(false);

	useEffect(() => {
		getAdmissionFormOptions(orgSlug)
			.then(setOptions)
			.catch((err) =>
				setLoadError(err instanceof ApiError && err.status === 404 ? "This admission form link isn't valid." : "Couldn't load the admission form. Please try again later."),
			);
	}, [orgSlug]);

	function field<K extends keyof FormState>(key: K) {
		return (event: React.ChangeEvent<HTMLInputElement>) => setForm((prev) => ({ ...prev, [key]: event.target.value }));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		const found = validateInquiry(form, new Date().toISOString().slice(0, 10));
		setErrors(found);
		if (Object.keys(found).length > 0) return;
		setSubmitting(true);
		try {
			await submitAdmissionInquiry(orgSlug, {
				firstName: form.firstName.trim(),
				lastName: form.lastName.trim(),
				dateOfBirth: form.dateOfBirth,
				gender: form.gender || null,
				desiredGradeId: form.desiredGradeId ? Number(form.desiredGradeId) : null,
				guardianName: form.guardianName.trim(),
				guardianPhone: form.guardianPhone.trim(),
				guardianEmail: form.guardianEmail.trim() || null,
				notes: form.notes.trim() || null,
				website,
			});
			setSubmitted(true);
		} catch (err) {
			if (err instanceof ApiError && err.status === 429) {
				setError("Too many submissions from your connection. Please wait a while and try again.");
			} else if (err instanceof ApiError && err.status === 400) {
				setError(err.message);
			} else {
				setError("Something went wrong submitting your inquiry. Please try again.");
			}
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Box sx={{ display: "flex", justifyContent: "center", alignItems: "flex-start", minHeight: "100vh", bgcolor: colors.paper, p: { xs: 1.5, sm: 4 } }}>
			<Paper
				component="form"
				onSubmit={handleSubmit}
				noValidate
				variant="outlined"
				sx={{ p: { xs: 2.5, sm: 4 }, width: "100%", maxWidth: 560, borderColor: colors.rule, boxShadow: "0 1px 2px rgba(22,35,58,0.09)" }}
			>
				<Stack spacing={2}>
					<Box sx={{ mb: 0.5 }}>
						<Wordmark tagline="Admissions" />
					</Box>

					{loadError && <Alert severity="error">{loadError}</Alert>}

					{!loadError && submitted && (
						<Alert severity="success">
							Thank you - your inquiry has been received{options ? ` by ${options.organisationName}` : ""}. The admissions office will contact you using the details provided.
						</Alert>
					)}

					{!loadError && !submitted && (
						<>
							<Typography variant="h6" component="h1">
								{options ? `Admission inquiry - ${options.organisationName}` : "Admission inquiry"}
							</Typography>
							{error && <Alert severity="error">{error}</Alert>}
							<Typography variant="subtitle2" color="text.secondary">
								Student
							</Typography>
							<Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
								<TextField label="First name" value={form.firstName} onChange={field("firstName")} error={!!errors.firstName} helperText={errors.firstName} required fullWidth autoFocus />
								<TextField label="Last name" value={form.lastName} onChange={field("lastName")} error={!!errors.lastName} helperText={errors.lastName} required fullWidth />
							</Stack>
							<Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
								<TextField
									label="Date of birth"
									type="date"
									value={form.dateOfBirth}
									onChange={field("dateOfBirth")}
									error={!!errors.dateOfBirth}
									helperText={errors.dateOfBirth}
									required
									fullWidth
									slotProps={{ inputLabel: { shrink: true } }}
								/>
								<TextField select label="Gender" value={form.gender} onChange={field("gender")} fullWidth>
									<MenuItem value="">Prefer not to say</MenuItem>
									<MenuItem value="Male">Male</MenuItem>
									<MenuItem value="Female">Female</MenuItem>
									<MenuItem value="Other">Other</MenuItem>
								</TextField>
							</Stack>
							{options && options.grades.length > 0 && (
								<TextField select label="Class applying for" value={form.desiredGradeId} onChange={field("desiredGradeId")} fullWidth>
									<MenuItem value="">Not sure yet</MenuItem>
									{options.grades.map((g) => (
										<MenuItem key={g.id} value={String(g.id)}>
											{g.name}
										</MenuItem>
									))}
								</TextField>
							)}
							<Typography variant="subtitle2" color="text.secondary">
								Parent / guardian
							</Typography>
							<TextField label="Guardian name" value={form.guardianName} onChange={field("guardianName")} error={!!errors.guardianName} helperText={errors.guardianName} required fullWidth />
							<Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
								<TextField label="Phone" type="tel" value={form.guardianPhone} onChange={field("guardianPhone")} error={!!errors.guardianPhone} helperText={errors.guardianPhone} required fullWidth />
								<TextField label="Email (optional)" type="email" value={form.guardianEmail} onChange={field("guardianEmail")} error={!!errors.guardianEmail} helperText={errors.guardianEmail} fullWidth />
							</Stack>
							<TextField label="Anything else we should know? (optional)" value={form.notes} onChange={field("notes")} multiline rows={3} fullWidth />

							{/* Honeypot - hidden from people and assistive tech; bots that fill it get dropped. */}
							<Box aria-hidden="true" sx={{ position: "absolute", left: "-10000px", width: 1, height: 1, overflow: "hidden" }}>
								<input type="text" name="website" tabIndex={-1} autoComplete="off" value={website} onChange={(e) => setWebsite(e.target.value)} />
							</Box>

							<Button type="submit" variant="contained" size="large" disabled={submitting || !options}>
								{submitting ? "Submitting..." : "Submit inquiry"}
							</Button>
						</>
					)}
				</Stack>
			</Paper>
		</Box>
	);
}
