import { useEffect, useState } from "react";
import { Link as RouterLink, useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Link from "@mui/material/Link";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import {
	discardPayrollRun,
	finalizePayrollRun,
	getPayrollRun,
	startPayrollCorrection,
	type PayrollRunResponse,
	type PayrollTotals,
} from "../../api/payroll";
import { money, monthLabel, runStatusColor } from "./format";

function TotalsRow({ label, totals }: { label: string; totals: PayrollTotals }) {
	return (
		<TableRow>
			<TableCell>{label}</TableCell>
			<TableCell align="right">{totals.count}</TableCell>
			<TableCell align="right">{money(totals.gross)}</TableCell>
			<TableCell align="right">{money(totals.deductions)}</TableCell>
			<TableCell align="right">{money(totals.net)}</TableCell>
		</TableRow>
	);
}

/** Per-run breakdown (#224): teacher vs. other-staff totals, every payslip, and the run's lifecycle actions. */
export function PayrollRunDetailPage() {
	const { runId } = useParams();
	const navigate = useNavigate();
	const [run, setRun] = useState<PayrollRunResponse | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getPayrollRun(Number(runId))
			.then(setRun)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load payroll run"));
	}, [runId]);

	async function act(action: () => Promise<PayrollRunResponse | void>, after?: (result: PayrollRunResponse | void) => void) {
		setError(null);
		try {
			after?.(await action());
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Action failed");
		}
	}

	if (!run) return error ? <Alert severity="error">{error}</Alert> : null;

	return (
		<Stack spacing={3}>
			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
						<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
							<Typography variant="h6">Payroll - {monthLabel(run.payYear, run.payMonth)}</Typography>
							<Chip label={run.status} size="small" color={runStatusColor(run.status)} />
							{run.supersedesRunId && <Chip label="correction" size="small" />}
						</Stack>
						<Can anyOf={["PAYROLL_MANAGE"]}>
							<Stack direction="row" spacing={1}>
								{run.status === "DRAFT" && (
									<>
										<Button color="error" onClick={() => act(() => discardPayrollRun(run.id), () => navigate("/payroll"))}>
											Discard draft
										</Button>
										<Button variant="contained" onClick={() => act(() => finalizePayrollRun(run.id), (r) => setRun(r as PayrollRunResponse))}>
											Finalize run
										</Button>
									</>
								)}
								{run.status === "FINALIZED" && (
									<Button
										onClick={() =>
											act(() => startPayrollCorrection(run.id), (r) => navigate(`/payroll/runs/${(r as PayrollRunResponse).id}`))
										}
									>
										Start correction run
									</Button>
								)}
							</Stack>
						</Can>
					</Stack>
					{error && <Alert severity="error">{error}</Alert>}
					{run.status === "DRAFT" && (
						<Alert severity="info">
							Draft - open a payslip to add an adjustment (e.g. unpaid leave). Finalizing locks every payslip; corrections then go through a correction run.
						</Alert>
					)}
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell />
								<TableCell align="right">Payslips</TableCell>
								<TableCell align="right">Gross</TableCell>
								<TableCell align="right">Deductions</TableCell>
								<TableCell align="right">Net</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							<TotalsRow label="Teachers" totals={run.teacherTotals} />
							<TotalsRow label="Other staff" totals={run.staffTotals} />
						</TableBody>
					</Table>
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Table size="small">
					<TableHead>
						<TableRow>
							<TableCell>Staff</TableCell>
							<TableCell>Code</TableCell>
							<TableCell>Designation</TableCell>
							<TableCell align="right">Gross</TableCell>
							<TableCell align="right">Deductions</TableCell>
							<TableCell align="right">Net</TableCell>
						</TableRow>
					</TableHead>
					<TableBody>
						{run.payslips.map((slip) => (
							<TableRow key={slip.id} hover>
								<TableCell>
									<Link component={RouterLink} to={`/payroll/payslips/${slip.id}`}>
										{slip.staffName}
									</Link>
									{slip.netPay < 0 && <Chip label="negative net" size="small" color="error" sx={{ ml: 1 }} />}
								</TableCell>
								<TableCell>{slip.employeeCode}</TableCell>
								<TableCell>{slip.designationName}</TableCell>
								<TableCell align="right">{money(slip.grossEarnings)}</TableCell>
								<TableCell align="right">{money(slip.totalDeductions)}</TableCell>
								<TableCell align="right">{money(slip.netPay)}</TableCell>
							</TableRow>
						))}
					</TableBody>
				</Table>
				{run.payslips.length === 0 && <Alert severity="info">No staff had a salary structure in force for this month.</Alert>}
			</Paper>
		</Stack>
	);
}
