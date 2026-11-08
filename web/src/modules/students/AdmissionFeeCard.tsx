import { useCallback, useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import RadioButtonUncheckedIcon from "@mui/icons-material/RadioButtonUnchecked";
import { getFeeClearance, type FeeClearanceStatus, type FeeClearanceStepKey, type FeeClearanceViewResponse } from "../../api/admission";
import { ApiError } from "../../api/client";
import type { StudentEnrollmentResponse } from "../../api/enrollments";
import { Can } from "../../auth/Can";
import { CollectFeeDialog } from "../fees/CollectFeeDialog";

const STEP_LABEL: Record<FeeClearanceStepKey, string> = {
	ENROLLED: "Enrolled",
	FEES_ASSIGNED: "Fees assigned",
	INVOICES_CREATED: "Invoices created",
	FIRST_PAYMENT: "First payment",
};

const STATUS_LABEL: Record<FeeClearanceStatus, string> = {
	NOT_REQUIRED: "No admission fee",
	PENDING: "Admission fee pending",
	CLEARED: "Admission fee cleared",
	EXEMPTED: "Admission fee exempted",
};

const STATUS_COLOR: Record<FeeClearanceStatus, "default" | "warning" | "success" | "info"> = {
	NOT_REQUIRED: "default",
	PENDING: "warning",
	CLEARED: "success",
	EXEMPTED: "info",
};

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

function formatDate(iso: string): string {
	return new Date(`${iso}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

function formatInstant(iso: string): string {
	return new Date(iso).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

interface Props {
	enrollment: StudentEnrollmentResponse;
	/** Called after the Collect dialog closes, so the page can re-read the enrollment's own status. */
	onChanged?: () => void;
}

/** The current enrollment's admission-fee standing: the four steps, the admission invoices still owing, and a status chip. The caller wraps it in ModuleEnabled FEES and Can FEE_VIEW (the endpoint needs FEE_VIEW). Hidden while the status is NOT_REQUIRED and the mode is OFF, so a school not using the workflow sees nothing. */
export function AdmissionFeeCard({ enrollment, onChanged }: Props) {
	const [view, setView] = useState<FeeClearanceViewResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [collectOpen, setCollectOpen] = useState(false);

	const load = useCallback(() => {
		getFeeClearance(enrollment.studentId, enrollment.id)
			.then((response) => {
				setView(response);
				setError(null);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load admission fee status"));
	}, [enrollment.studentId, enrollment.id]);

	useEffect(load, [load]);

	function closeCollect() {
		setCollectOpen(false);
		load();
		onChanged?.();
	}

	if (error) {
		return <Alert severity="error">{error}</Alert>;
	}
	if (!view || (view.status === "NOT_REQUIRED" && view.mode === "OFF")) {
		return null;
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="subtitle1">Admission fee</Typography>
					<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
						<Chip label={STATUS_LABEL[view.status]} color={STATUS_COLOR[view.status]} size="small" />
						{view.status === "PENDING" && (
							<Can anyOf={["FEE_COLLECT"]}>
								<Button size="small" variant="contained" onClick={() => setCollectOpen(true)}>
									Collect
								</Button>
							</Can>
						)}
					</Stack>
				</Box>

				<Stack direction="row" spacing={3} sx={{ flexWrap: "wrap" }}>
					{view.steps.map((step) => (
						<Stack key={step.key} direction="row" spacing={1} sx={{ alignItems: "center" }} data-testid={`admission-step-${step.key}`}>
							{step.done ? <CheckCircleIcon color="success" fontSize="small" /> : <RadioButtonUncheckedIcon color="disabled" fontSize="small" />}
							<Box>
								<Typography variant="body2">{STEP_LABEL[step.key]}</Typography>
								<Typography variant="caption" color="text.secondary">
									{step.done ? (step.at ? formatInstant(step.at) : "Done") : "Not yet"}
								</Typography>
							</Box>
						</Stack>
					))}
				</Stack>

				{view.invoices.length > 0 && (
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Invoice</TableCell>
								<TableCell>Due</TableCell>
								<TableCell align="right">Total</TableCell>
								<TableCell align="right">Paid</TableCell>
								<TableCell align="right">Outstanding</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{view.invoices.map((invoice) => (
								<TableRow key={invoice.id}>
									<TableCell>{invoice.number}</TableCell>
									<TableCell>{formatDate(invoice.dueDate)}</TableCell>
									<TableCell align="right">{currency(invoice.total)}</TableCell>
									<TableCell align="right">{currency(invoice.paid)}</TableCell>
									<TableCell align="right">{currency(invoice.outstanding)}</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				)}
			</Stack>

			<CollectFeeDialog
				open={collectOpen}
				onClose={closeCollect}
				target={{ studentEnrollmentId: enrollment.id, academicYearId: enrollment.academicYearId, schoolClassId: enrollment.schoolClassId }}
			/>
		</Paper>
	);
}
