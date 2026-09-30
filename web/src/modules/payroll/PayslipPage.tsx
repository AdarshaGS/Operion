import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import PrintIcon from "@mui/icons-material/Print";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import {
	addPayslipAdjustment,
	getPayslip,
	removePayslipAdjustment,
	type ComponentType,
	type PayslipResponse,
} from "../../api/payroll";
import { PayslipView } from "./PayslipView";

/** Admin payslip view. While the run is DRAFT an adjustment line (unpaid leave, recovery, bonus) can be
 * added/removed; once finalized the payslip is locked and this is a read-only, printable record. */
export function PayslipPage() {
	const { payslipId } = useParams();
	const [payslip, setPayslip] = useState<PayslipResponse | null>(null);
	const [label, setLabel] = useState("");
	const [type, setType] = useState<ComponentType>("DEDUCTION");
	const [amount, setAmount] = useState("");
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getPayslip(Number(payslipId))
			.then(setPayslip)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load payslip"));
	}, [payslipId]);

	async function handleAdd(event: FormEvent) {
		event.preventDefault();
		if (!payslip) return;
		setError(null);
		try {
			setPayslip(await addPayslipAdjustment(payslip.id, { label, type, amount: Number(amount) }));
			setLabel("");
			setAmount("");
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add adjustment");
		}
	}

	async function handleRemove(lineId: number) {
		setError(null);
		try {
			setPayslip(await removePayslipAdjustment(lineId));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to remove adjustment");
		}
	}

	if (!payslip) return error ? <Alert severity="error">{error}</Alert> : null;
	const draft = payslip.runStatus === "DRAFT";

	return (
		<Stack spacing={3}>
			{error && <Alert severity="error">{error}</Alert>}
			<PayslipView
				payslip={payslip}
				lineAction={
					draft
						? (line) =>
								line.source === "ADJUSTMENT" ? (
									<Can anyOf={["PAYROLL_MANAGE"]}>
										<Button size="small" color="error" onClick={() => handleRemove(line.id)}>
											Remove
										</Button>
									</Can>
								) : null
						: undefined
				}
			/>
			<Stack direction="row">
				<Button startIcon={<PrintIcon />} onClick={() => window.print()}>
					Print
				</Button>
			</Stack>
			{draft && (
				<Can anyOf={["PAYROLL_MANAGE"]}>
					<Paper sx={{ p: 3 }} component="form" onSubmit={handleAdd}>
						<Stack spacing={2}>
							<Typography variant="h6">Add adjustment</Typography>
							<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap", gap: 2 }}>
								<TextField label="Label" value={label} onChange={(e) => setLabel(e.target.value)} required sx={{ minWidth: 220 }} />
								<TextField select label="Type" value={type} onChange={(e) => setType(e.target.value as ComponentType)} sx={{ minWidth: 150 }}>
									<MenuItem value="EARNING">Earning</MenuItem>
									<MenuItem value="DEDUCTION">Deduction</MenuItem>
								</TextField>
								<TextField
									label="Amount"
									type="number"
									value={amount}
									onChange={(e) => setAmount(e.target.value)}
									required
									slotProps={{ htmlInput: { min: 0, step: "0.01" } }}
									sx={{ width: 160 }}
								/>
								<Button type="submit" variant="contained">
									Add
								</Button>
							</Stack>
						</Stack>
					</Paper>
				</Can>
			)}
		</Stack>
	);
}
