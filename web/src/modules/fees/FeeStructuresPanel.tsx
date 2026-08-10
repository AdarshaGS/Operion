import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import IconButton from "@mui/material/IconButton";
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
import DeleteIcon from "@mui/icons-material/Delete";
import { type AcademicYearResponse, listAcademicYears } from "../../api/academicYears";
import { ApiError } from "../../api/client";
import { type FeeCategoryResponse, listFeeCategories } from "../../api/feeCategories";
import { createFeeStructure, listFeeStructures, type FeeStructureResponse, type InstallmentEntry } from "../../api/feeStructures";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";

const EMPTY_INSTALLMENT: InstallmentEntry = { installmentNumber: 1, dueDate: "", amount: 0 };

export function FeeStructuresPanel() {
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
	const [categories, setCategories] = useState<FeeCategoryResponse[]>([]);
	const [academicYearId, setAcademicYearId] = useState("");
	const [schoolClassId, setSchoolClassId] = useState("");
	const [structures, setStructures] = useState<FeeStructureResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [feeCategoryId, setFeeCategoryId] = useState("");
	const [amount, setAmount] = useState("");
	const [installments, setInstallments] = useState<InstallmentEntry[]>([{ ...EMPTY_INSTALLMENT }]);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		listAcademicYears().then(setAcademicYears).catch(() => {});
		listSchoolClasses().then(setClasses).catch(() => {});
		listFeeCategories().then(setCategories).catch(() => {});
	}, []);

	function refresh() {
		if (!academicYearId || !schoolClassId) {
			setStructures([]);
			return;
		}
		listFeeStructures(Number(academicYearId), Number(schoolClassId))
			.then(setStructures)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load fee structures"));
	}

	useEffect(refresh, [academicYearId, schoolClassId]);

	const installmentTotal = installments.reduce((sum, entry) => sum + (Number(entry.amount) || 0), 0);
	const totalsMatch = installments.length > 0 && Math.abs(installmentTotal - (Number(amount) || 0)) < 0.005;

	function openDialog() {
		listFeeCategories().then(setCategories).catch(() => {});
		setFeeCategoryId("");
		setAmount("");
		setInstallments([{ ...EMPTY_INSTALLMENT }]);
		setDialogOpen(true);
	}

	function updateInstallment(index: number, patch: Partial<InstallmentEntry>) {
		setInstallments((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
	}

	function addInstallment() {
		setInstallments((rows) => [...rows, { installmentNumber: rows.length + 1, dueDate: "", amount: 0 }]);
	}

	function removeInstallment(index: number) {
		setInstallments((rows) => rows.filter((_, i) => i !== index));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		if (!totalsMatch) return;
		setSubmitting(true);
		try {
			await createFeeStructure({
				academicYearId: Number(academicYearId),
				schoolClassId: Number(schoolClassId),
				feeCategoryId: Number(feeCategoryId),
				amount: Number(amount),
				installments,
			});
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create fee structure");
		} finally {
			setSubmitting(false);
		}
	}

	function categoryName(id: number): string {
		return categories.find((c) => c.id === id)?.name ?? `#${id}`;
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Fee structures</Typography>

				<Box sx={{ display: "flex", gap: 2, alignItems: "flex-end" }}>
					<TextField
						select
						label="Academic year"
						value={academicYearId}
						onChange={(e) => setAcademicYearId(e.target.value)}
						sx={{ minWidth: 200 }}
					>
						{academicYears.map((year) => (
							<MenuItem key={year.id} value={year.id}>
								{year.name}
							</MenuItem>
						))}
					</TextField>
					<TextField select label="Class" value={schoolClassId} onChange={(e) => setSchoolClassId(e.target.value)} sx={{ minWidth: 200 }}>
						{classes.map((schoolClass) => (
							<MenuItem key={schoolClass.id} value={schoolClass.id}>
								{schoolClass.displayName ?? `Class #${schoolClass.id}`}
							</MenuItem>
						))}
					</TextField>
					<Button startIcon={<AddIcon />} onClick={openDialog} disabled={!academicYearId || !schoolClassId}>
						Add structure
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{academicYearId && schoolClassId && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Category</TableCell>
									<TableCell>Amount</TableCell>
									<TableCell>Installments</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{structures.map((structure) => (
									<TableRow key={structure.id}>
										<TableCell>{categoryName(structure.feeCategoryId)}</TableCell>
										<TableCell>{structure.amount}</TableCell>
										<TableCell>{structure.installments.length}</TableCell>
										<TableCell>
											<Chip label={structure.status} size="small" />
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>Add fee structure</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Fee category" value={feeCategoryId} onChange={(e) => setFeeCategoryId(e.target.value)} required fullWidth>
							{categories.map((category) => (
								<MenuItem key={category.id} value={category.id}>
									{category.name}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Total amount" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} required fullWidth />

						<Typography variant="subtitle2">Installments</Typography>
						{installments.map((installment, index) => (
							<Box key={index} sx={{ display: "flex", gap: 1, alignItems: "center" }}>
								<TextField
									label="#"
									type="number"
									value={installment.installmentNumber}
									onChange={(e) => updateInstallment(index, { installmentNumber: Number(e.target.value) })}
									sx={{ width: 70 }}
								/>
								<TextField
									label="Due date"
									type="date"
									value={installment.dueDate}
									onChange={(e) => updateInstallment(index, { dueDate: e.target.value })}
									slotProps={{ inputLabel: { shrink: true } }}
									sx={{ flex: 1 }}
								/>
								<TextField
									label="Amount"
									type="number"
									value={installment.amount}
									onChange={(e) => updateInstallment(index, { amount: Number(e.target.value) })}
									sx={{ flex: 1 }}
								/>
								<IconButton onClick={() => removeInstallment(index)} disabled={installments.length === 1}>
									<DeleteIcon fontSize="small" />
								</IconButton>
							</Box>
						))}
						<Button size="small" startIcon={<AddIcon />} onClick={addInstallment} sx={{ alignSelf: "flex-start" }}>
							Add installment
						</Button>

						{!totalsMatch && (
							<Alert severity="warning">
								Installments total {installmentTotal}, must equal the structure amount ({amount || 0}).
							</Alert>
						)}
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !totalsMatch}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
