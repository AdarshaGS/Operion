import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import FormControlLabel from "@mui/material/FormControlLabel";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { getExaminationSettings, updateExaminationSettings, type PassFailStrategy } from "../../api/examinationSettings";

const STRATEGY_LABELS: Record<PassFailStrategy, string> = {
	PASS_EVERY_SUBJECT: "Must pass every subject",
	MINIMUM_AGGREGATE_PERCENTAGE: "Minimum aggregate percentage",
	BOTH: "Both (every subject and minimum aggregate)",
};

/** Org-wide examination policy: ranking toggle (#136) and pass/fail strategy (#135), configurable per institution. */
export function ExaminationSettingsPanel() {
	const [rankingEnabled, setRankingEnabled] = useState(false);
	const [passFailStrategy, setPassFailStrategy] = useState<PassFailStrategy>("PASS_EVERY_SUBJECT");
	const [minimumAggregatePercentage, setMinimumAggregatePercentage] = useState("33");
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		getExaminationSettings()
			.then((settings) => {
				setRankingEnabled(settings.rankingEnabled);
				setPassFailStrategy(settings.passFailStrategy);
				setMinimumAggregatePercentage(String(settings.minimumAggregatePercentage));
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load examination settings"));
	}, []);

	async function handleSave() {
		setSaving(true);
		setSaved(false);
		try {
			const updated = await updateExaminationSettings({
				rankingEnabled,
				passFailStrategy,
				minimumAggregatePercentage: Number(minimumAggregatePercentage),
			});
			setRankingEnabled(updated.rankingEnabled);
			setPassFailStrategy(updated.passFailStrategy);
			setMinimumAggregatePercentage(String(updated.minimumAggregatePercentage));
			setSaved(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save examination settings");
		} finally {
			setSaving(false);
		}
	}

	const needsThreshold = passFailStrategy === "MINIMUM_AGGREGATE_PERCENTAGE" || passFailStrategy === "BOTH";

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Examination settings</Typography>

				{error && <Alert severity="error">{error}</Alert>}
				{saved && <Alert severity="success">Saved.</Alert>}

				<FormControlLabel
					control={<Switch checked={rankingEnabled} onChange={(e) => setRankingEnabled(e.target.checked)} />}
					label="Compute subject-wise and class-wise rank"
				/>

				<TextField
					select
					label="Overall pass/fail strategy"
					value={passFailStrategy}
					onChange={(e) => setPassFailStrategy(e.target.value as PassFailStrategy)}
					sx={{ maxWidth: 360 }}
				>
					{Object.entries(STRATEGY_LABELS).map(([value, label]) => (
						<MenuItem key={value} value={value}>
							{label}
						</MenuItem>
					))}
				</TextField>

				{needsThreshold && (
					<TextField
						label="Minimum aggregate percentage"
						type="number"
						value={minimumAggregatePercentage}
						onChange={(e) => setMinimumAggregatePercentage(e.target.value)}
						sx={{ maxWidth: 240 }}
					/>
				)}

				<Box>
					<Button variant="contained" onClick={handleSave} disabled={saving}>
						Save
					</Button>
				</Box>
			</Stack>
		</Paper>
	);
}
