import { useEffect, useMemo, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
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
import {
	createFeeStructureGroup,
	listFeeStructureGroups,
	type FeeStructureGroupResponse,
} from "../../api/feeStructureGroups";
import { createFeeStructure, listFeeStructures, type FeeStructureResponse, type InstallmentEntry } from "../../api/feeStructures";
import { listCampuses, type CampusResponse } from "../../api/campuses";
import { listGradeLevels, type GradeLevelResponse } from "../../api/gradeLevels";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { useCampusFiltered, useSelectedCampusId } from "../../layout/selectedCampus";

const EMPTY_INSTALLMENT: InstallmentEntry = { installmentNumber: 1, dueDate: "", amount: 0 };
const PAYMENT_FREQUENCIES = ["MONTHLY", "QUARTERLY", "HALF_YEARLY", "ONE_SHOT"];

export function FeeStructuresPanel() {
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
	const [categories, setCategories] = useState<FeeCategoryResponse[]>([]);
	const [gradeLevels, setGradeLevels] = useState<GradeLevelResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [academicYearId, setAcademicYearId] = useState("");
	const [schoolClassId, setSchoolClassId] = useState("");
	const [group, setGroup] = useState<FeeStructureGroupResponse | null | undefined>(undefined);
	const [structures, setStructures] = useState<FeeStructureResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [groupDialogOpen, setGroupDialogOpen] = useState(false);
	const [groupName, setGroupName] = useState("");
	const [creatingGroup, setCreatingGroup] = useState(false);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [feeCategoryId, setFeeCategoryId] = useState("");
	const [amount, setAmount] = useState("");
	const [installments, setInstallments] = useState<InstallmentEntry[]>([{ ...EMPTY_INSTALLMENT }]);
	const [paymentFrequency, setPaymentFrequency] = useState("MONTHLY");
	// Off = paid at once: one due date, one payment for the full amount. On = split into installments.
	const [usesInstallments, setUsesInstallments] = useState(false);
	const [singleDueDate, setSingleDueDate] = useState("");
	const [oneShotDiscountAmount, setOneShotDiscountAmount] = useState("");
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		listAcademicYears().then(setAcademicYears).catch(() => {});
		listSchoolClasses().then(setClasses).catch(() => {});
		listFeeCategories().then(setCategories).catch(() => {});
		listGradeLevels().then(setGradeLevels).catch(() => {});
		listCampuses().then(setCampuses).catch(() => {});
	}, []);

	// Template-created classes have no display name, and the same grade exists once per
	// campus - label by grade and campus so each option is identifiable.
	function classLabel(schoolClass: SchoolClassResponse): string {
		const name = schoolClass.displayName ?? gradeLevels.find((g) => g.id === schoolClass.gradeLevelId)?.name ?? `Class #${schoolClass.id}`;
		const campus = campuses.find((c) => c.id === schoolClass.campusId)?.name;
		return campus ? `${name} · ${campus}` : name;
	}

	const selectedCampusId = useSelectedCampusId();
	const yearClasses = useMemo(() => classes.filter((c) => String(c.academicYearId) === String(academicYearId)), [classes, academicYearId]);
	const campusYearClasses = useCampusFiltered(yearClasses);

	// A class picked under another campus is dropped when the campus changes.
	useEffect(() => {
		// The Select hands back the MenuItem's numeric value, so compare as strings.
		if (schoolClassId && !campusYearClasses.some((c) => String(c.id) === String(schoolClassId))) setSchoolClassId("");
	}, [campusYearClasses, schoolClassId]);

	function refreshGroup() {
		if (!academicYearId || !schoolClassId) {
			setGroup(undefined);
			setStructures([]);
			return;
		}
		listFeeStructureGroups(Number(academicYearId), Number(schoolClassId))
			.then((groups) => setGroup(groups[0] ?? null))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load fee structure"));
	}

	useEffect(refreshGroup, [academicYearId, schoolClassId]);

	function refreshStructures() {
		if (!group) {
			setStructures([]);
			return;
		}
		listFeeStructures(group.id)
			.then(setStructures)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load fee structure components"));
	}

	useEffect(refreshStructures, [group]);

	const installmentTotal = installments.reduce((sum, entry) => sum + (Number(entry.amount) || 0), 0);
	const totalsMatch = usesInstallments
		? installments.length > 0 && Math.abs(installmentTotal - (Number(amount) || 0)) < 0.005
		: Number(amount) > 0 && singleDueDate !== "";

	function openGroupDialog() {
		const year = academicYears.find((y) => String(y.id) === String(academicYearId));
		const schoolClass = classes.find((c) => String(c.id) === String(schoolClassId));
		const defaultName = [schoolClass && classLabel(schoolClass), year?.name].filter(Boolean).join(" Fees ");
		setGroupName(defaultName || "");
		setGroupDialogOpen(true);
	}

	async function handleCreateGroup(event: FormEvent) {
		event.preventDefault();
		setCreatingGroup(true);
		try {
			const created = await createFeeStructureGroup({
				name: groupName,
				academicYearId: Number(academicYearId),
				schoolClassId: Number(schoolClassId),
			});
			setGroup(created);
			setGroupDialogOpen(false);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to set up fee structure");
		} finally {
			setCreatingGroup(false);
		}
	}

	function openDialog() {
		listFeeCategories().then(setCategories).catch(() => {});
		setFeeCategoryId("");
		setAmount("");
		setInstallments([{ ...EMPTY_INSTALLMENT }]);
		setPaymentFrequency("MONTHLY");
		setUsesInstallments(false);
		setSingleDueDate("");
		setOneShotDiscountAmount("");
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
		if (!totalsMatch || !group) return;
		setSubmitting(true);
		try {
			await createFeeStructure({
				feeStructureGroupId: group.id,
				feeCategoryId: Number(feeCategoryId),
				amount: Number(amount),
				installments: usesInstallments
					? installments
					: [{ installmentNumber: 1, dueDate: singleDueDate, amount: Number(amount) }],
				paymentFrequency: usesInstallments ? paymentFrequency : "ONE_SHOT",
				oneShotDiscountAmount: usesInstallments && oneShotDiscountAmount ? Number(oneShotDiscountAmount) : null,
			});
			setDialogOpen(false);
			refreshStructures();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add fee component");
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
						onChange={(e) => {
							setAcademicYearId(e.target.value);
							setSchoolClassId("");
						}}
						sx={{ minWidth: 200 }}
					>
						{academicYears.map((year) => (
							<MenuItem key={year.id} value={year.id}>
								{year.name}
							</MenuItem>
						))}
					</TextField>
					<TextField
						select
						label="Class"
						value={schoolClassId}
						onChange={(e) => setSchoolClassId(e.target.value)}
						disabled={!academicYearId}
						sx={{ minWidth: 260 }}
					>
						{campusYearClasses.map((schoolClass) => (
							<MenuItem key={schoolClass.id} value={schoolClass.id}>
								{classLabel(schoolClass)}
							</MenuItem>
						))}
					</TextField>
				</Box>

				{selectedCampusId !== null && yearClasses.length > 0 && campusYearClasses.length === 0 && (
					<Alert severity="info">No classes in the selected campus.</Alert>
				)}

				{error && <Alert severity="error">{error}</Alert>}

				{academicYearId && schoolClassId && group === null && (
					<Alert
						severity="info"
						action={
							<Button color="inherit" size="small" onClick={openGroupDialog}>
								Set up
							</Button>
						}
					>
						No fee structure has been set up for this class yet.
					</Alert>
				)}

				{group && (
					<>
						<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
							<Typography variant="subtitle1">{group.name}</Typography>
							<Button startIcon={<AddIcon />} onClick={openDialog}>
								Add component
							</Button>
						</Box>

						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Category</TableCell>
										<TableCell>Amount</TableCell>
										<TableCell>Installments</TableCell>
										<TableCell>Frequency</TableCell>
										<TableCell>One-shot discount</TableCell>
										<TableCell>Status</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{structures.map((structure) => (
										<TableRow key={structure.id}>
											<TableCell>{categoryName(structure.feeCategoryId)}</TableCell>
											<TableCell>{structure.amount}</TableCell>
											<TableCell>{structure.installments.length}</TableCell>
											<TableCell>{structure.paymentFrequency}</TableCell>
											<TableCell>{structure.oneShotDiscountAmount ?? "—"}</TableCell>
											<TableCell>
												<Chip label={structure.status} size="small" />
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					</>
				)}
			</Stack>

			<Dialog open={groupDialogOpen} onClose={() => setGroupDialogOpen(false)} component="form" onSubmit={handleCreateGroup} fullWidth maxWidth="xs">
				<DialogTitle>Set up fee structure</DialogTitle>
				<DialogContent>
					<TextField
						label="Name"
						value={groupName}
						onChange={(e) => setGroupName(e.target.value)}
						required
						fullWidth
						sx={{ mt: 1 }}
						helperText='e.g. "Grade 5 Annual Fees 2026-27"'
					/>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setGroupDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={creatingGroup || !groupName}>
						Create
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>Add fee component</DialogTitle>
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
						<FormControlLabel
							control={<Checkbox checked={usesInstallments} onChange={(e) => setUsesInstallments(e.target.checked)} />}
							label="Pay in installments"
						/>
						{!usesInstallments && (
							<TextField
								label="Due date"
								type="date"
								value={singleDueDate}
								onChange={(e) => setSingleDueDate(e.target.value)}
								required
								slotProps={{ inputLabel: { shrink: true } }}
								helperText="The full amount is payable at once on this date"
								fullWidth
							/>
						)}
						{usesInstallments && (
							<>
								<Box sx={{ display: "flex", gap: 2 }}>
									<TextField
										select
										label="Payment frequency"
										value={paymentFrequency}
										onChange={(e) => setPaymentFrequency(e.target.value)}
										fullWidth
									>
										{PAYMENT_FREQUENCIES.map((frequency) => (
											<MenuItem key={frequency} value={frequency}>
												{frequency}
											</MenuItem>
										))}
									</TextField>
									<TextField
										label="One-shot discount (optional)"
										type="number"
										value={oneShotDiscountAmount}
										onChange={(e) => setOneShotDiscountAmount(e.target.value)}
										helperText="Flat amount off when paid in full instead of by installment"
										fullWidth
									/>
								</Box>

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
							</>
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
