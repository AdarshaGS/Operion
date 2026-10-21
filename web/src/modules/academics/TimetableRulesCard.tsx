import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { getTimetableSettings, updateTimetableSettings, type TimetableSettingsResponse } from "../../api/timetable";
import { Can } from "../../auth/Can";

function toLimit(value: string): number | null {
	const number = Number(value);
	return value.trim() === "" || number <= 0 ? null : number;
}

/** The org-wide timetable rules: the minimum gap between class periods and the most periods a teacher may take.
 * Blank limits mean no limit; breaks and special slots are exempt from the gap. */
export function TimetableRulesCard({ onChanged }: { onChanged?: (settings: TimetableSettingsResponse) => void }) {
	const [gap, setGap] = useState("0");
	const [perDay, setPerDay] = useState("");
	const [perWeek, setPerWeek] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);
	const [submitting, setSubmitting] = useState(false);

	function apply(settings: TimetableSettingsResponse) {
		setGap(String(settings.gapMinutes));
		setPerDay(settings.maxTeacherPeriodsPerDay === null ? "" : String(settings.maxTeacherPeriodsPerDay));
		setPerWeek(settings.maxTeacherPeriodsPerWeek === null ? "" : String(settings.maxTeacherPeriodsPerWeek));
		onChanged?.(settings);
	}

	useEffect(() => {
		getTimetableSettings()
			.then(apply)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load timetable rules"));
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setError(null);
		setSaved(false);
		try {
			apply(
				await updateTimetableSettings({
					gapMinutes: Number(gap) || 0,
					maxTeacherPeriodsPerDay: toLimit(perDay),
					maxTeacherPeriodsPerWeek: toLimit(perWeek),
				}),
			);
			setSaved(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save timetable rules");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Stack spacing={2} component="form" onSubmit={handleSubmit} aria-label="Timetable rules">
			<Typography variant="subtitle1">Timetable rules</Typography>
			{error && <Alert severity="error">{error}</Alert>}
			{saved && <Alert severity="success">Timetable rules saved.</Alert>}
			<Stack direction={{ xs: "column", md: "row" }} spacing={2}>
				<TextField
					label="Minimum gap between class periods (minutes)"
					type="number"
					value={gap}
					onChange={(e) => setGap(e.target.value)}
					helperText="Breaks and special slots are exempt. Existing periods are not changed."
					slotProps={{ htmlInput: { min: 0 } }}
					fullWidth
				/>
				<TextField
					label="Max periods per teacher per day"
					type="number"
					value={perDay}
					onChange={(e) => setPerDay(e.target.value)}
					helperText="Blank for no limit"
					slotProps={{ htmlInput: { min: 0 } }}
					fullWidth
				/>
				<TextField
					label="Max periods per teacher per week"
					type="number"
					value={perWeek}
					onChange={(e) => setPerWeek(e.target.value)}
					helperText="Blank for no limit"
					slotProps={{ htmlInput: { min: 0 } }}
					fullWidth
				/>
			</Stack>
			<Can anyOf={["TIMETABLE_MANAGE"]}>
				<Button type="submit" variant="outlined" disabled={submitting} sx={{ alignSelf: "flex-start" }}>
					Save rules
				</Button>
			</Can>
		</Stack>
	);
}
