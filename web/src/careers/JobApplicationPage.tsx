import { useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { submitJobApplication } from "../api/jobApplications";
import { ApiError } from "../api/client";
import { Wordmark } from "../branding/Wordmark";
import { colors } from "../theme";

/** Public, unauthenticated - same trust tier as ClaimInvitePage. A generic "apply for
 * an open role" form, not tied to any one vertical - specialization is free text so
 * the same pipeline works whatever role an org is hiring for. */
export function JobApplicationPage() {
	const [organisationSlug, setOrganisationSlug] = useState("");
	const [applicantName, setApplicantName] = useState("");
	const [email, setEmail] = useState("");
	const [specialization, setSpecialization] = useState("");
	const [yearsExperience, setYearsExperience] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitted, setSubmitted] = useState(false);
	const [submitting, setSubmitting] = useState(false);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSubmitting(true);
		try {
			await submitJobApplication({
				organisationSlug: organisationSlug.trim(),
				applicantName,
				email,
				specialization: specialization || null,
				yearsExperience: yearsExperience ? Number(yearsExperience) : null,
			});
			setSubmitted(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Couldn't submit your application - check the organisation slug and try again");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", bgcolor: colors.paper }}>
			<Paper
				component="form"
				onSubmit={handleSubmit}
				variant="outlined"
				sx={{ p: 4, width: 420, borderColor: colors.rule, boxShadow: "0 1px 2px rgba(22,35,58,0.09)" }}
			>
				<Stack spacing={2}>
					<Box sx={{ mb: 0.5 }}>
						<Wordmark tagline="Careers" />
					</Box>

					{submitted ? (
						<Alert severity="success">Your application has been submitted. The organisation will get in touch if there's a fit.</Alert>
					) : (
						<>
							<Typography variant="body2" color="text.secondary">
								Apply for an open role
							</Typography>
							{error && <Alert severity="error">{error}</Alert>}
							<TextField
								label="Organisation slug"
								value={organisationSlug}
								onChange={(e) => setOrganisationSlug(e.target.value)}
								required
								autoFocus
								autoComplete="off"
								autoCapitalize="off"
								autoCorrect="off"
								spellCheck={false}
								helperText="The organisation's login identifier - ask them if you don't have it"
							/>
							<TextField label="Full name" value={applicantName} onChange={(e) => setApplicantName(e.target.value)} required />
							<TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
							<TextField
								label="Specialization"
								value={specialization}
								onChange={(e) => setSpecialization(e.target.value)}
								helperText="Subjects, skills, or area of expertise"
							/>
							<TextField
								label="Years of experience"
								type="number"
								value={yearsExperience}
								onChange={(e) => setYearsExperience(e.target.value)}
							/>
							<Button type="submit" variant="contained" disabled={submitting}>
								{submitting ? "Submitting..." : "Submit application"}
							</Button>
						</>
					)}
				</Stack>
			</Paper>
		</Box>
	);
}
