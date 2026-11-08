import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import Paper from "@mui/material/Paper";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { getAdmissionSettings, updateAdmissionSettings, type AdmissionFeeClearanceMode } from "../../api/admission";
import { ApiError } from "../../api/client";
import { Can } from "../../auth/Can";
import { useCan } from "../../auth/useCan";

const MODE_OPTIONS: { mode: AdmissionFeeClearanceMode; label: string; help: string }[] = [
	{ mode: "OFF", label: "Off", help: "Nothing. Enrolling a student assigns and invoices no fee." },
	{
		mode: "TRACK",
		label: "Track",
		help: "Fees are assigned and invoiced at enrolment, and the admission-fee status is shown.",
	},
	{
		mode: "BLOCK",
		label: "Block",
		help: "Coming next: will additionally block marks and academic records for students whose admission fee is pending. Until then it behaves like Track.",
	},
];

/** Whether enrolling a student charges the fees marked "Collect at admission" and tracks them. Anyone can read it; saving needs ORGANISATION_MANAGE because it switches a financial policy on for the whole school. */
export function AdmissionFeeSettingsPanel() {
	const canEdit = useCan("ORGANISATION_MANAGE");
	const [mode, setMode] = useState<AdmissionFeeClearanceMode | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		getAdmissionSettings()
			.then((settings) => setMode(settings.feeClearanceMode))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load admission fee settings"));
	}, []);

	async function handleSave() {
		if (!mode) return;
		setSaving(true);
		setSaved(false);
		setError(null);
		try {
			const settings = await updateAdmissionSettings({ feeClearanceMode: mode });
			setMode(settings.feeClearanceMode);
			setSaved(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save admission fee settings");
		} finally {
			setSaving(false);
		}
	}

	if (!mode) {
		return (
			<Paper sx={{ p: 3 }}>
				{error ? <Alert severity="error">{error}</Alert> : <Typography variant="body2">Loading…</Typography>}
			</Paper>
		);
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Admission fee</Typography>

				{error && <Alert severity="error">{error}</Alert>}
				{saved && <Alert severity="success">Saved.</Alert>}

				<FormControl disabled={!canEdit}>
					<FormLabel id="admission-fee-mode-label">What happens at enrolment</FormLabel>
					<RadioGroup
						aria-labelledby="admission-fee-mode-label"
						value={mode}
						onChange={(e) => {
							setMode(e.target.value as AdmissionFeeClearanceMode);
							setSaved(false);
						}}
					>
						{MODE_OPTIONS.map((option) => (
							<Box key={option.mode} sx={{ mb: 1 }}>
								<FormControlLabel value={option.mode} control={<Radio />} label={option.label} sx={{ mr: 0 }} />
								<Typography variant="body2" color="text.secondary" sx={{ ml: 4 }}>
									{option.help}
								</Typography>
							</Box>
						))}
					</RadioGroup>
				</FormControl>

				<Typography variant="body2" color="text.secondary">
					Attendance is never blocked. Only the Fees module is affected - if Fees is switched off, none of this applies.
				</Typography>

				<Box>
					<Can anyOf={["ORGANISATION_MANAGE"]}>
						<Button variant="contained" onClick={handleSave} disabled={saving}>
							Save
						</Button>
					</Can>
				</Box>
			</Stack>
		</Paper>
	);
}
