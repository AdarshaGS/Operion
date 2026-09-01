import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { getMonthlySummary, type MonthlyAttendanceSummaryResponse } from "../../api/attendance";

const MONTH_NAMES = [
	"January", "February", "March", "April", "May", "June",
	"July", "August", "September", "October", "November", "December",
];

function percentageColor(percentage: number): "success" | "warning" | "error" {
	if (percentage >= 75) return "success";
	if (percentage >= 50) return "warning";
	return "error";
}

export function StudentAttendanceSummaryPanel({ enrollmentId }: { enrollmentId: number }) {
	const now = new Date();
	const [year, setYear] = useState(now.getFullYear());
	const [month, setMonth] = useState(now.getMonth() + 1);
	const [summary, setSummary] = useState<MonthlyAttendanceSummaryResponse | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getMonthlySummary(enrollmentId, year, month)
			.then(setSummary)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load attendance summary"));
	}, [enrollmentId, year, month]);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="subtitle1">Attendance</Typography>
					<Stack direction="row" spacing={1}>
						<TextField select size="small" label="Month" value={month} onChange={(e) => setMonth(Number(e.target.value))}>
							{MONTH_NAMES.map((name, index) => (
								<MenuItem key={name} value={index + 1}>
									{name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							size="small"
							label="Year"
							type="number"
							value={year}
							onChange={(e) => setYear(Number(e.target.value))}
							sx={{ width: 100 }}
						/>
					</Stack>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{summary && (
					<Stack direction="row" spacing={3} sx={{ alignItems: "center", flexWrap: "wrap" }}>
						<Chip label={`${summary.percentage}% present`} color={percentageColor(summary.percentage)} />
						<Typography variant="body2" color="text.secondary">
							Present {summary.presentCount} · Late {summary.lateCount} · Half-day {summary.halfDayCount} · Absent{" "}
							{summary.absentCount} · Leave {summary.leaveCount} · {summary.totalMarkedDays} days marked
						</Typography>
					</Stack>
				)}
			</Stack>
		</Paper>
	);
}
