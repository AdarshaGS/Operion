import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import type { PayslipLineResponse, PayslipResponse } from "../../api/payroll";
import { money, monthLabel, runStatusColor } from "./format";

interface PayslipViewProps {
	payslip: PayslipResponse;
	/** Renders a trailing cell per line - the admin draft view uses it for "remove adjustment". */
	lineAction?: (line: PayslipLineResponse) => React.ReactNode;
}

/** Print-styled payslip shared by the admin and self-service pages (no PDF infra - browser print). */
export function PayslipView({ payslip, lineAction }: PayslipViewProps) {
	const earnings = payslip.lines.filter((line) => line.type === "EARNING");
	const deductions = payslip.lines.filter((line) => line.type === "DEDUCTION");

	function section(title: string, lines: PayslipLineResponse[], total: number) {
		return (
			<Box sx={{ flex: 1, minWidth: 260 }}>
				<Typography variant="subtitle2" sx={{ mb: 1 }}>
					{title}
				</Typography>
				<Table size="small">
					<TableHead>
						<TableRow>
							<TableCell>Item</TableCell>
							<TableCell align="right">Amount</TableCell>
							{lineAction && <TableCell />}
						</TableRow>
					</TableHead>
					<TableBody>
						{lines.map((line) => (
							<TableRow key={line.id}>
								<TableCell>
									{line.label}
									{line.source === "ADJUSTMENT" && <Chip label="adjustment" size="small" sx={{ ml: 1 }} />}
								</TableCell>
								<TableCell align="right" sx={{ fontVariantNumeric: "tabular-nums" }}>
									{money(line.amount)}
								</TableCell>
								{lineAction && <TableCell align="right">{lineAction(line)}</TableCell>}
							</TableRow>
						))}
						<TableRow>
							<TableCell sx={{ fontWeight: 600 }}>Total</TableCell>
							<TableCell align="right" sx={{ fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>
								{money(total)}
							</TableCell>
							{lineAction && <TableCell />}
						</TableRow>
					</TableBody>
				</Table>
			</Box>
		);
	}

	return (
		<Paper sx={{ p: 3 }} className="payslip-print">
			<Stack spacing={2}>
				<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: 1 }}>
					<Box>
						<Typography variant="h6">Payslip - {monthLabel(payslip.payYear, payslip.payMonth)}</Typography>
						<Typography variant="body2" color="text.secondary">
							{payslip.staffName} · {payslip.employeeCode}
							{payslip.designationName ? ` · ${payslip.designationName}` : ""}
							{payslip.departmentName ? ` · ${payslip.departmentName}` : ""}
						</Typography>
					</Box>
					<Chip label={payslip.runStatus} size="small" color={runStatusColor(payslip.runStatus)} />
				</Stack>
				<Divider />
				<Stack direction={{ xs: "column", md: "row" }} spacing={3}>
					{section("Earnings", earnings, payslip.grossEarnings)}
					{section("Deductions", deductions, payslip.totalDeductions)}
				</Stack>
				<Divider />
				<Stack direction="row" sx={{ justifyContent: "space-between" }}>
					<Typography variant="h6">Net pay</Typography>
					<Typography variant="h6" sx={{ fontVariantNumeric: "tabular-nums" }}>
						{money(payslip.netPay)}
					</Typography>
				</Stack>
			</Stack>
		</Paper>
	);
}
