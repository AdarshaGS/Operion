import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import { generatePayrollRun, listPayrollRuns, type PayrollRunResponse } from "../../api/payroll";
import { money, monthLabel, runStatusColor } from "./format";

export function PayrollRunsPanel() {
	const navigate = useNavigate();
	const now = new Date();
	const [runs, setRuns] = useState<PayrollRunResponse[]>([]);
	const [year, setYear] = useState(String(now.getFullYear()));
	const [month, setMonth] = useState(String(now.getMonth() + 1));
	const [error, setError] = useState<string | null>(null);
	const [loaded, setLoaded] = useState(false);

	useEffect(() => {
		listPayrollRuns()
			.then((list) => {
				setRuns(list);
				setLoaded(true);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load payroll runs"));
	}, []);

	async function handleGenerate(event: FormEvent) {
		event.preventDefault();
		setError(null);
		try {
			const run = await generatePayrollRun(Number(year), Number(month));
			navigate(`/payroll/runs/${run.id}`);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to generate payroll run");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Payroll runs</Typography>
				{error && <Alert severity="error">{error}</Alert>}
				<Can anyOf={["PAYROLL_MANAGE"]}>
					<Stack component="form" direction="row" spacing={2} onSubmit={handleGenerate} sx={{ flexWrap: "wrap", gap: 2 }}>
						<TextField select label="Month" value={month} onChange={(e) => setMonth(e.target.value)} sx={{ minWidth: 160 }}>
							{Array.from({ length: 12 }, (_, i) => (
								<MenuItem key={i + 1} value={String(i + 1)}>
									{monthLabel(2000, i + 1).replace(" 2000", "")}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Year" type="number" value={year} onChange={(e) => setYear(e.target.value)} sx={{ width: 120 }} />
						<Button type="submit" variant="contained">
							Generate run
						</Button>
					</Stack>
				</Can>
				{!loaded ? null : runs.length === 0 ? (
					<Alert severity="info">
						No payroll runs yet. A run covers every active staff member with a salary structure in force for the month.
					</Alert>
				) : (
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Period</TableCell>
								<TableCell>Status</TableCell>
								<TableCell align="right">Payslips</TableCell>
								<TableCell align="right">Teachers net</TableCell>
								<TableCell align="right">Other staff net</TableCell>
								<TableCell align="right">Total net</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{runs.map((run) => (
								<TableRow key={run.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/payroll/runs/${run.id}`)}>
									<TableCell>
										{monthLabel(run.payYear, run.payMonth)}
										{run.supersedesRunId && <Chip label="correction" size="small" sx={{ ml: 1 }} />}
									</TableCell>
									<TableCell>
										<Chip label={run.status} size="small" color={runStatusColor(run.status)} />
									</TableCell>
									<TableCell align="right">{run.payslipCount}</TableCell>
									<TableCell align="right">{money(run.teacherTotals.net)}</TableCell>
									<TableCell align="right">{money(run.staffTotals.net)}</TableCell>
									<TableCell align="right">{money(run.teacherTotals.net + run.staffTotals.net)}</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				)}
			</Stack>
		</Paper>
	);
}
