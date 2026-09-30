import { useEffect, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Link from "@mui/material/Link";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import PrintIcon from "@mui/icons-material/Print";
import { ApiError } from "../api/client";
import { getMyPayslip, getMyPayslips, type PayslipResponse } from "../api/payroll";
import { money, monthLabel } from "../modules/payroll/format";
import { PayslipView } from "../modules/payroll/PayslipView";

/** Staff self-service payslip history (#224) - only the caller's own finalized payslips come back. */
export function MyPayslipsPage() {
	const [payslips, setPayslips] = useState<PayslipResponse[] | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getMyPayslips()
			.then(setPayslips)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load payslips"));
	}, []);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">My payslips</Typography>
				{error && <Alert severity="error">{error}</Alert>}
				{payslips && payslips.length === 0 && <Alert severity="info">No payslips have been published for you yet.</Alert>}
				{payslips && payslips.length > 0 && (
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Month</TableCell>
								<TableCell align="right">Gross</TableCell>
								<TableCell align="right">Deductions</TableCell>
								<TableCell align="right">Net pay</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{payslips.map((slip) => (
								<TableRow key={slip.id} hover>
									<TableCell>
										<Link component={RouterLink} to={`/portal/payslips/${slip.id}`}>
											{monthLabel(slip.payYear, slip.payMonth)}
										</Link>
									</TableCell>
									<TableCell align="right">{money(slip.grossEarnings)}</TableCell>
									<TableCell align="right">{money(slip.totalDeductions)}</TableCell>
									<TableCell align="right">{money(slip.netPay)}</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				)}
			</Stack>
		</Paper>
	);
}

export function MyPayslipPage() {
	const { payslipId } = useParams();
	const [payslip, setPayslip] = useState<PayslipResponse | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getMyPayslip(Number(payslipId))
			.then(setPayslip)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load payslip"));
	}, [payslipId]);

	if (!payslip) return error ? <Alert severity="error">{error}</Alert> : null;
	return (
		<Stack spacing={2}>
			<PayslipView payslip={payslip} />
			<Stack direction="row" spacing={1}>
				<Button component={RouterLink} to="/portal/payslips">
					Back to payslips
				</Button>
				<Button startIcon={<PrintIcon />} onClick={() => window.print()}>
					Print
				</Button>
			</Stack>
		</Stack>
	);
}
