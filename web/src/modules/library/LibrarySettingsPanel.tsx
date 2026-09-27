import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { getLibrarySettings, updateLibrarySettings, type LibrarySettingsResponse } from "../../api/librarySettings";

/** Borrowing policy (#169: max concurrent loans + default period, per borrower type;
 * #170: block issue when a borrower already has an overdue book). */
export function LibrarySettingsPanel() {
	const [settings, setSettings] = useState<LibrarySettingsResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		getLibrarySettings()
			.then(setSettings)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load library settings"));
	}, []);

	async function handleSave() {
		if (!settings) return;
		setSaving(true);
		setSaved(false);
		try {
			setSettings(await updateLibrarySettings(settings));
			setSaved(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save library settings");
		} finally {
			setSaving(false);
		}
	}

	function update(field: keyof LibrarySettingsResponse, value: number | boolean) {
		if (!settings) return;
		setSettings({ ...settings, [field]: value });
	}

	if (!settings) {
		return (
			<Paper sx={{ p: 3 }}>
				{error ? <Alert severity="error">{error}</Alert> : <Typography variant="body2">Loading…</Typography>}
			</Paper>
		);
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Borrowing rules</Typography>

				{error && <Alert severity="error">{error}</Alert>}
				{saved && <Alert severity="success">Saved.</Alert>}

				<Grid container spacing={2}>
					<Grid size={{ xs: 12, sm: 6 }}>
						<TextField
							label="Max books - student"
							type="number"
							fullWidth
							value={settings.maxLoansStudent}
							onChange={(e) => update("maxLoansStudent", Number(e.target.value))}
						/>
					</Grid>
					<Grid size={{ xs: 12, sm: 6 }}>
						<TextField
							label="Max books - staff"
							type="number"
							fullWidth
							value={settings.maxLoansStaff}
							onChange={(e) => update("maxLoansStaff", Number(e.target.value))}
						/>
					</Grid>
					<Grid size={{ xs: 12, sm: 6 }}>
						<TextField
							label="Borrowing period (days) - student"
							type="number"
							fullWidth
							value={settings.borrowingPeriodDaysStudent}
							onChange={(e) => update("borrowingPeriodDaysStudent", Number(e.target.value))}
						/>
					</Grid>
					<Grid size={{ xs: 12, sm: 6 }}>
						<TextField
							label="Borrowing period (days) - staff"
							type="number"
							fullWidth
							value={settings.borrowingPeriodDaysStaff}
							onChange={(e) => update("borrowingPeriodDaysStaff", Number(e.target.value))}
						/>
					</Grid>
				</Grid>

				<FormControlLabel
					control={
						<Switch checked={settings.blockIssueOnOverdue} onChange={(e) => update("blockIssueOnOverdue", e.target.checked)} />
					}
					label="Block issuing a new book to a borrower who already has an overdue book"
				/>

				<Box>
					<Button variant="contained" onClick={handleSave} disabled={saving}>
						Save
					</Button>
				</Box>
			</Stack>
		</Paper>
	);
}
