import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { ApiError } from "../../api/client";
import { assignFee, listFeeAssignments, type StudentFeeAssignmentResponse } from "../../api/feeAssignments";
import { listFeeCategories, type FeeCategoryResponse } from "../../api/feeCategories";
import { listFeeStructures, type FeeStructureResponse } from "../../api/feeStructures";
import { generateInvoice, listInvoices, type InvoiceResponse } from "../../api/invoices";
import { recordPayment, type AllocationEntry } from "../../api/payments";

const PAYMENT_METHODS = ["CASH", "CHEQUE", "UPI", "CARD", "BANK_TRANSFER"];

interface Props {
	studentEnrollmentId: number;
	academicYearId: number;
	schoolClassId: number;
}

export function StudentFeesPanel({ studentEnrollmentId, academicYearId, schoolClassId }: Props) {
	const [assignments, setAssignments] = useState<StudentFeeAssignmentResponse[]>([]);
	const [invoices, setInvoices] = useState<InvoiceResponse[]>([]);
	const [feeStructures, setFeeStructures] = useState<FeeStructureResponse[]>([]);
	const [categories, setCategories] = useState<FeeCategoryResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [assignDialogOpen, setAssignDialogOpen] = useState(false);
	const [feeStructureId, setFeeStructureId] = useState("");
	const [discountAmount, setDiscountAmount] = useState("");
	const [discountReason, setDiscountReason] = useState("");
	const [approvedBy, setApprovedBy] = useState("");

	const [invoiceDialogAssignment, setInvoiceDialogAssignment] = useState<StudentFeeAssignmentResponse | null>(null);
	const [installmentId, setInstallmentId] = useState("");

	const [paymentDialogOpen, setPaymentDialogOpen] = useState(false);
	const [paymentAmount, setPaymentAmount] = useState("");
	const [paymentMethod, setPaymentMethod] = useState("CASH");
	const [paymentDate, setPaymentDate] = useState(new Date().toISOString().slice(0, 10));
	const [paymentRemarks, setPaymentRemarks] = useState("");
	const [allocations, setAllocations] = useState<Record<number, string>>({});

	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listFeeAssignments(studentEnrollmentId)
			.then(setAssignments)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load fee assignments"));
		listInvoices(studentEnrollmentId)
			.then(setInvoices)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load invoices"));
	}

	useEffect(refresh, [studentEnrollmentId]);

	useEffect(() => {
		listFeeStructures(academicYearId, schoolClassId).then(setFeeStructures).catch(() => {});
		listFeeCategories().then(setCategories).catch(() => {});
	}, [academicYearId, schoolClassId]);

	function structureFor(id: number): FeeStructureResponse | undefined {
		return feeStructures.find((s) => s.id === id);
	}

	function categoryName(feeCategoryId: number): string {
		return categories.find((c) => c.id === feeCategoryId)?.name ?? `Category #${feeCategoryId}`;
	}

	function structureLabel(structure: FeeStructureResponse): string {
		return `${categoryName(structure.feeCategoryId)} — ${structure.amount}`;
	}

	async function handleAssign(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await assignFee({
				studentEnrollmentId,
				feeStructureId: Number(feeStructureId),
				discountAmount: discountAmount ? Number(discountAmount) : null,
				discountReason: discountReason || null,
				approvedBy: approvedBy ? Number(approvedBy) : null,
			});
			setFeeStructureId("");
			setDiscountAmount("");
			setDiscountReason("");
			setApprovedBy("");
			setAssignDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to assign fee");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleGenerateInvoice(event: FormEvent) {
		event.preventDefault();
		if (!invoiceDialogAssignment) return;
		setSubmitting(true);
		try {
			await generateInvoice(invoiceDialogAssignment.id, Number(installmentId));
			setInvoiceDialogAssignment(null);
			setInstallmentId("");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to generate invoice");
		} finally {
			setSubmitting(false);
		}
	}

	const outstandingInvoices = invoices.filter((invoice) => invoice.outstanding > 0);
	const allocatedTotal = Object.values(allocations).reduce((sum, value) => sum + (Number(value) || 0), 0);
	const allocationsMatch = Math.abs(allocatedTotal - (Number(paymentAmount) || 0)) < 0.005;

	function openPaymentDialog() {
		setPaymentAmount("");
		setPaymentMethod("CASH");
		setPaymentDate(new Date().toISOString().slice(0, 10));
		setPaymentRemarks("");
		setAllocations({});
		setPaymentDialogOpen(true);
	}

	function toggleInvoiceAllocation(invoice: InvoiceResponse, checked: boolean) {
		setAllocations((prev) => {
			const next = { ...prev };
			if (checked) {
				next[invoice.id] = String(invoice.outstanding);
			} else {
				delete next[invoice.id];
			}
			return next;
		});
	}

	async function handleRecordPayment(event: FormEvent) {
		event.preventDefault();
		if (!allocationsMatch || Object.keys(allocations).length === 0) return;
		setSubmitting(true);
		try {
			const entries: AllocationEntry[] = Object.entries(allocations).map(([invoiceId, amount]) => ({
				invoiceId: Number(invoiceId),
				amount: Number(amount),
			}));
			await recordPayment({
				academicYearId,
				amount: Number(paymentAmount),
				paymentMethod,
				paymentDate,
				remarks: paymentRemarks || null,
				allocations: entries,
			});
			setPaymentDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record payment");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Stack spacing={3}>
			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Fee assignments</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setAssignDialogOpen(true)} disabled={feeStructures.length === 0}>
							Assign fee
						</Button>
					</Box>

					{feeStructures.length === 0 && (
						<Alert severity="info">No fee structures exist for this student's class/year yet — add one under Fee structures first.</Alert>
					)}

					{assignments.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Structure</TableCell>
										<TableCell>Base</TableCell>
										<TableCell>Discount</TableCell>
										<TableCell>Effective</TableCell>
										<TableCell>Status</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{assignments.map((assignment) => (
										<TableRow key={assignment.id}>
											<TableCell>
											{(() => {
												const structure = structureFor(assignment.feeStructureId);
												return structure ? categoryName(structure.feeCategoryId) : `Structure #${assignment.feeStructureId}`;
											})()}
										</TableCell>
											<TableCell>{assignment.baseAmount}</TableCell>
											<TableCell>{assignment.discountAmount ?? "—"}</TableCell>
											<TableCell>{assignment.effectiveAmount}</TableCell>
											<TableCell>
												<Chip label={assignment.status} size="small" />
											</TableCell>
											<TableCell>
												<Button size="small" onClick={() => setInvoiceDialogAssignment(assignment)}>
													Generate invoice
												</Button>
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Invoices</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={openPaymentDialog} disabled={outstandingInvoices.length === 0}>
							Record payment
						</Button>
					</Box>

					{invoices.length === 0 && <Alert severity="info">No invoices generated yet.</Alert>}

					{invoices.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Invoice #</TableCell>
										<TableCell>Due date</TableCell>
										<TableCell>Total</TableCell>
										<TableCell>Paid</TableCell>
										<TableCell>Outstanding</TableCell>
										<TableCell>Status</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{invoices.map((invoice) => (
										<TableRow key={invoice.id}>
											<TableCell>{invoice.invoiceNumber}</TableCell>
											<TableCell>{invoice.dueDate}</TableCell>
											<TableCell>{invoice.totalAmount}</TableCell>
											<TableCell>{invoice.amountPaid}</TableCell>
											<TableCell>{invoice.outstanding}</TableCell>
											<TableCell>
												<Chip label={invoice.status} size="small" />
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Dialog open={assignDialogOpen} onClose={() => setAssignDialogOpen(false)} component="form" onSubmit={handleAssign} fullWidth maxWidth="xs">
				<DialogTitle>Assign fee</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Fee structure" value={feeStructureId} onChange={(e) => setFeeStructureId(e.target.value)} required fullWidth>
							{feeStructures.map((structure) => (
								<MenuItem key={structure.id} value={structure.id}>
									{structureLabel(structure)}
								</MenuItem>
							))}
						</TextField>
						<Divider />
						<Typography variant="caption" color="text.secondary">
							Optional discount — requires both a reason and an approver id
						</Typography>
						<TextField label="Discount amount" type="number" value={discountAmount} onChange={(e) => setDiscountAmount(e.target.value)} fullWidth />
						<TextField label="Discount reason" value={discountReason} onChange={(e) => setDiscountReason(e.target.value)} fullWidth />
						<TextField label="Approved by (user id)" type="number" value={approvedBy} onChange={(e) => setApprovedBy(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAssignDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !feeStructureId}>
						Assign
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={invoiceDialogAssignment !== null} onClose={() => setInvoiceDialogAssignment(null)} component="form" onSubmit={handleGenerateInvoice} fullWidth maxWidth="xs">
				<DialogTitle>Generate invoice</DialogTitle>
				<DialogContent>
					<TextField
						select
						label="Installment"
						value={installmentId}
						onChange={(e) => setInstallmentId(e.target.value)}
						required
						fullWidth
						sx={{ mt: 1 }}
					>
						{(invoiceDialogAssignment ? structureFor(invoiceDialogAssignment.feeStructureId)?.installments ?? [] : []).map((installment) => (
							<MenuItem key={installment.id} value={installment.id}>
								#{installment.installmentNumber} — due {installment.dueDate} — {installment.amount}
							</MenuItem>
						))}
					</TextField>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setInvoiceDialogAssignment(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !installmentId}>
						Generate
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={paymentDialogOpen} onClose={() => setPaymentDialogOpen(false)} component="form" onSubmit={handleRecordPayment} fullWidth maxWidth="sm">
				<DialogTitle>Record payment</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField label="Amount" type="number" value={paymentAmount} onChange={(e) => setPaymentAmount(e.target.value)} required fullWidth />
							<TextField select label="Method" value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)} required fullWidth>
								{PAYMENT_METHODS.map((method) => (
									<MenuItem key={method} value={method}>
										{method}
									</MenuItem>
								))}
							</TextField>
						</Box>
						<TextField
							label="Payment date"
							type="date"
							value={paymentDate}
							onChange={(e) => setPaymentDate(e.target.value)}
							required
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
						<TextField label="Remarks" value={paymentRemarks} onChange={(e) => setPaymentRemarks(e.target.value)} fullWidth />

						<Divider />
						<Typography variant="subtitle2">Allocate to outstanding invoices</Typography>
						{outstandingInvoices.map((invoice) => (
							<Box key={invoice.id} sx={{ display: "flex", gap: 2, alignItems: "center" }}>
								<Checkbox
									checked={invoice.id in allocations}
									onChange={(e) => toggleInvoiceAllocation(invoice, e.target.checked)}
								/>
								<Typography sx={{ flex: 1 }}>
									{invoice.invoiceNumber} (outstanding {invoice.outstanding})
								</Typography>
								<TextField
									label="Allocate"
									type="number"
									size="small"
									value={allocations[invoice.id] ?? ""}
									disabled={!(invoice.id in allocations)}
									onChange={(e) => setAllocations((prev) => ({ ...prev, [invoice.id]: e.target.value }))}
									sx={{ width: 140 }}
								/>
							</Box>
						))}

						{!allocationsMatch && (
							<Alert severity="warning">
								Allocated {allocatedTotal}, must equal the payment amount ({paymentAmount || 0}).
							</Alert>
						)}
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setPaymentDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !allocationsMatch || Object.keys(allocations).length === 0}>
						Record
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
