import { useEffect, useMemo, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import ListSubheader from "@mui/material/ListSubheader";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { getOrganisationSettings, updateOrganisationSettings } from "../../api/organisationSettings";
import { listTimezones, type TimezoneResponse } from "../../api/timezones";

/** Bit 0 = Monday .. bit 6 = Sunday, matching OrganisationConfiguration.workingDaysMask
 * on the backend - a plain int, not worth a child table or JSON for 7 static days. */
const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

function maskToDays(mask: number): boolean[] {
	return DAYS.map((_, index) => (mask & (1 << index)) !== 0);
}

function daysToMask(days: boolean[]): number {
	return days.reduce((mask, checked, index) => (checked ? mask | (1 << index) : mask), 0);
}

export function BusinessSettingsPanel() {
	const [timezone, setTimezone] = useState("");
	const [timezones, setTimezones] = useState<TimezoneResponse[]>([]);
	const [defaultCurrency, setDefaultCurrency] = useState("");
	const [dateFormat, setDateFormat] = useState("");
	const [workingDays, setWorkingDays] = useState<boolean[]>(new Array(7).fill(false));
	const [logoUrl, setLogoUrl] = useState("");
	const [primaryColor, setPrimaryColor] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		getOrganisationSettings()
			.then((settings) => {
				setTimezone(settings.timezone ?? "");
				setDefaultCurrency(settings.defaultCurrency ?? "");
				setDateFormat(settings.dateFormat ?? "");
				setWorkingDays(maskToDays(settings.workingDaysMask));
				setLogoUrl(settings.logoUrl ?? "");
				setPrimaryColor(settings.primaryColor ?? "");
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load business settings"))
			.finally(() => setLoading(false));
		listTimezones()
			.then(setTimezones)
			.catch(() => undefined);
	}, []);

	// Regions in name order, but each region's own zones already arrive name-sorted from
	// the API - grouped here purely for the picker's <ListSubheader> dividers.
	const timezonesByRegion = useMemo(() => {
		const groups = new Map<string, TimezoneResponse[]>();
		for (const tz of timezones) {
			const existing = groups.get(tz.region) ?? [];
			existing.push(tz);
			groups.set(tz.region, existing);
		}
		return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
	}, [timezones]);

	function toggleDay(index: number) {
		setWorkingDays((prev) => prev.map((value, i) => (i === index ? !value : value)));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setSaved(false);
		try {
			await updateOrganisationSettings({
				timezone: timezone || null,
				defaultCurrency: defaultCurrency || null,
				dateFormat: dateFormat || null,
				workingDaysMask: daysToMask(workingDays),
				logoUrl: logoUrl || null,
				primaryColor: primaryColor || null,
			});
			setSaved(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update business settings");
		} finally {
			setSubmitting(false);
		}
	}

	if (loading) {
		return null;
	}

	return (
		<Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Business settings</Typography>

				{error && <Alert severity="error">{error}</Alert>}
				{saved && <Alert severity="success">Settings updated</Alert>}

				<Box sx={{ display: "flex", gap: 2 }}>
					<TextField
						select
						label="Timezone"
						value={timezone}
						onChange={(e) => setTimezone(e.target.value)}
						slotProps={{ select: { MenuProps: { slotProps: { paper: { sx: { maxHeight: 420 } } } } } }}
						fullWidth
					>
						<MenuItem value="">Not set</MenuItem>
						{timezonesByRegion.flatMap(([region, zones]) => [
							<ListSubheader key={`header-${region}`}>{region}</ListSubheader>,
							...zones.map((tz) => (
								<MenuItem key={tz.id} value={tz.name}>
									{tz.name}
								</MenuItem>
							)),
						])}
					</TextField>
					<TextField
						label="Default currency"
						placeholder="INR"
						value={defaultCurrency}
						onChange={(e) => setDefaultCurrency(e.target.value)}
						fullWidth
					/>
				</Box>
				<Box sx={{ display: "flex", gap: 2 }}>
					<TextField
						label="Date format"
						placeholder="dd-MM-yyyy"
						value={dateFormat}
						onChange={(e) => setDateFormat(e.target.value)}
						fullWidth
					/>
					<TextField label="Primary color" placeholder="#1976d2" value={primaryColor} onChange={(e) => setPrimaryColor(e.target.value)} fullWidth />
				</Box>
				<TextField label="Logo URL" value={logoUrl} onChange={(e) => setLogoUrl(e.target.value)} fullWidth />

				<Box>
					<Typography variant="body2" sx={{ mb: 1 }}>
						Working days
					</Typography>
					<Box sx={{ display: "flex", flexWrap: "wrap" }}>
						{DAYS.map((day, index) => (
							<FormControlLabel
								key={day}
								control={<Checkbox checked={workingDays[index]} onChange={() => toggleDay(index)} />}
								label={day}
							/>
						))}
					</Box>
				</Box>

				<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
					<Button type="submit" variant="contained" disabled={submitting}>
						{submitting ? "Saving..." : "Save"}
					</Button>
				</Box>
			</Stack>
		</Paper>
	);
}
