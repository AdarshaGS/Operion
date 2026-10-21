import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import {
	addTeacherUnavailability,
	listPeriods,
	listTeacherUnavailability,
	removeTeacherUnavailability,
	type PeriodResponse,
	type UnavailabilityResponse,
} from "../../api/timetable";
import { useAuth } from "../../auth/AuthContext";
import { BASE_DAYS, dayLongLabel } from "./timetableViewShared";

/** When a teacher cannot be given timetable slots (a part-time day off, a fixed commitment elsewhere). The timetable
 * refuses to place them then, and the generator and substitute suggestions steer around it. Existing slots are not touched. */
export function TeacherUnavailabilityPanel({ personId }: { personId: number }) {
	const { hasAnyPermission } = useAuth();
	const canManage = hasAnyPermission(["TIMETABLE_MANAGE"]);

	const [blocks, setBlocks] = useState<UnavailabilityResponse[]>([]);
	const [periods, setPeriods] = useState<PeriodResponse[]>([]);
	const [day, setDay] = useState("MONDAY");
	const [periodId, setPeriodId] = useState("");
	const [reason, setReason] = useState("");
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		listTeacherUnavailability(personId)
			.then(setBlocks)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load availability"));
	}

	useEffect(() => {
		setBlocks([]);
		listPeriods().then(setPeriods).catch(() => {});
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [personId]);

	async function handleAdd() {
		setError(null);
		try {
			await addTeacherUnavailability(personId, { dayOfWeek: day, periodId: periodId ? Number(periodId) : null, reason: reason || null });
			setReason("");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save");
		}
	}

	async function handleRemove(id: number) {
		setError(null);
		try {
			await removeTeacherUnavailability(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to remove");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6" component="h2">
					Unavailable times
				</Typography>
				{error && <Alert severity="error">{error}</Alert>}
				{blocks.length === 0 && <Alert severity="info">No unavailable times - this teacher can be given any slot.</Alert>}
				<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", rowGap: 1 }}>
					{blocks.map((block) => (
						<Chip
							key={block.id}
							label={`${dayLongLabel(block.dayOfWeek)} ${block.periodLabel ?? "all day"}${block.reason ? ` · ${block.reason}` : ""}`}
							onDelete={canManage ? () => handleRemove(block.id) : undefined}
						/>
					))}
				</Stack>
				{canManage && (
					<Stack direction={{ xs: "column", md: "row" }} spacing={2}>
						<TextField select label="Day" value={day} onChange={(e) => setDay(e.target.value)} sx={{ minWidth: 160 }}>
							{[...BASE_DAYS, "SUNDAY"].map((d) => (
								<MenuItem key={d} value={d}>
									{dayLongLabel(d)}
								</MenuItem>
							))}
						</TextField>
						<TextField select label="Period" value={periodId} onChange={(e) => setPeriodId(e.target.value)} sx={{ minWidth: 200 }}>
							<MenuItem value="">All day</MenuItem>
							{periods
								.filter((p) => !p.breakSlot)
								.map((p) => (
									<MenuItem key={p.id} value={String(p.id)}>
										{p.label}
									</MenuItem>
								))}
						</TextField>
						<TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} sx={{ flexGrow: 1 }} />
						<Button variant="outlined" onClick={handleAdd}>
							Add unavailable time
						</Button>
					</Stack>
				)}
			</Stack>
		</Paper>
	);
}
