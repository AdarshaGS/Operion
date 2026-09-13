import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { PlatformApiError } from "./api/platformClient";
import { getPlatformSettings, updatePlatformSettings } from "./api/settings";

/** Deliberately just the one setting for now (trial length) - see the ticket for why the
 * page isn't a bigger dumping ground yet (notification templates, module catalogue, etc.
 * all need their own scope pass first). */
export function PlatformSettingsPage() {
	const [trialDays, setTrialDays] = useState<string>("");
	const [savedTrialDays, setSavedTrialDays] = useState<number | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [success, setSuccess] = useState(false);
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		getPlatformSettings()
			.then((settings) => {
				setTrialDays(String(settings.trialDays));
				setSavedTrialDays(settings.trialDays);
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load settings"));
	}, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		setSuccess(false);
		setSaving(true);
		try {
			const updated = await updatePlatformSettings(Number(trialDays));
			setSavedTrialDays(updated.trialDays);
			setSuccess(true);
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to save settings");
		} finally {
			setSaving(false);
		}
	}

	const canSave = trialDays !== "" && Number(trialDays) > 0 && Number(trialDays) !== savedTrialDays;

	return (
		<Stack spacing={2}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Settings
				</Typography>
				<Typography variant="h4" component="h1">
					Platform Settings
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}
			{success && <Alert severity="success">Saved.</Alert>}

			<Paper variant="outlined" sx={{ p: 3, maxWidth: 480 }} component="form" onSubmit={handleSubmit}>
				<Stack spacing={2}>
					<Typography variant="h6">Trials</Typography>
					<TextField
						label="Trial length (days)"
						type="number"
						value={trialDays}
						onChange={(e) => setTrialDays(e.target.value)}
						helperText="Trial end date is computed live as createdAt + this many days, not stored per organisation - changing it recalculates every organisation's trial end immediately, including ones already provisioned."
						slotProps={{ htmlInput: { min: 1 } }}
					/>
					<Stack direction="row">
						<Button type="submit" variant="contained" disabled={!canSave || saving}>
							Save
						</Button>
					</Stack>
				</Stack>
			</Paper>
		</Stack>
	);
}
