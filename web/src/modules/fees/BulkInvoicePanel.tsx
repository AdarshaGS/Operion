import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { listAcademicYears, type AcademicYearResponse } from "../../api/academicYears";
import { bulkGenerateInvoices, type BulkInvoiceResult } from "../../api/invoices";
import { type SchoolClassResponse } from "../../api/schoolClasses";
import { useCampusFiltered, useSelectedCampusId } from "../../layout/selectedCampus";
import { useSchoolClassLabel } from "../../utils/useSchoolClassLabel";

function today(): string {
	return new Date().toISOString().slice(0, 10);
}

/** Adds one class's bulk result into the running total when "All classes" fans out over a campus's classes. */
function addResult(total: BulkInvoiceResult, next: BulkInvoiceResult): BulkInvoiceResult {
	return {
		dryRun: total.dryRun,
		toGenerate: total.toGenerate + next.toGenerate,
		generated: total.generated + next.generated,
		alreadyInvoiced: total.alreadyInvoiced + next.alreadyInvoiced,
		invoiceIds: [...total.invoiceIds, ...next.invoiceIds],
		failures: [...total.failures, ...next.failures],
	};
}

/** Generate every invoice that has fallen due in one action: preview the count, then generate.
 * `classes` is every class; the picker and the action are limited to the campus picked in the top bar. The
 * bulk endpoint only narrows by a single class, so with a campus picked, "All classes" runs once per class
 * of that campus (never the endpoint's organisation-wide mode) and the counts are summed. */
export function BulkInvoicePanel({ classes, onGenerated }: { classes: SchoolClassResponse[]; onGenerated: () => void }) {
	const classLabel = useSchoolClassLabel();
	const selectedCampusId = useSelectedCampusId();
	const campusClasses = useCampusFiltered(classes);
	const [years, setYears] = useState<AcademicYearResponse[]>([]);
	const [academicYearId, setAcademicYearId] = useState("");
	const [schoolClassId, setSchoolClassId] = useState("");
	const [dueOnOrBefore, setDueOnOrBefore] = useState(today());
	const [preview, setPreview] = useState<BulkInvoiceResult | null>(null);
	const [result, setResult] = useState<BulkInvoiceResult | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [busy, setBusy] = useState(false);

	useEffect(() => {
		listAcademicYears()
			.then((rows) => {
				setYears(rows);
				const current = rows.find((y) => y.current);
				if (current) setAcademicYearId(String(current.id));
			})
			.catch(() => {});
	}, []);

	// A class picked under another campus is dropped when the campus changes.
	useEffect(() => {
		if (schoolClassId && !campusClasses.some((c) => String(c.id) === String(schoolClassId))) setSchoolClassId("");
	}, [campusClasses, schoolClassId]);

	// Any change to the inputs invalidates the last preview.
	useEffect(() => {
		setPreview(null);
	}, [academicYearId, schoolClassId, dueOnOrBefore]);

	const noCampusClasses = selectedCampusId !== null && campusClasses.length === 0;

	async function run(dryRun: boolean) {
		if (!academicYearId || !dueOnOrBefore) return;
		setBusy(true);
		setError(null);
		setResult(null);
		let response: BulkInvoiceResult = { dryRun, toGenerate: 0, generated: 0, alreadyInvoiced: 0, invoiceIds: [], failures: [] };
		try {
			if (schoolClassId) {
				response = await bulkGenerateInvoices({ academicYearId: Number(academicYearId), schoolClassId: Number(schoolClassId), dueOnOrBefore, dryRun });
			} else if (selectedCampusId === null) {
				response = await bulkGenerateInvoices({ academicYearId: Number(academicYearId), dueOnOrBefore, dryRun });
			} else {
				for (const schoolClass of campusClasses.filter((c) => c.academicYearId === Number(academicYearId))) {
					response = addResult(response, await bulkGenerateInvoices({ academicYearId: Number(academicYearId), schoolClassId: schoolClass.id, dueOnOrBefore, dryRun }));
				}
			}
			if (dryRun) {
				setPreview(response);
			} else {
				setPreview(null);
				setResult(response);
				onGenerated();
			}
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to generate invoices");
			// A campus-wide run that stopped part-way still created invoices for the classes before the failure.
			if (!dryRun && response.generated > 0) {
				setResult(response);
				onGenerated();
			}
		} finally {
			setBusy(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Generate due invoices</Typography>
				<Typography variant="body2" color="text.secondary">
					Creates an invoice for every installment due on or before the date, for every student with an active fee assignment.
					Installments that already have an invoice are skipped, so it is safe to run again.
				</Typography>
				<Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
					<TextField select label="Academic year" value={academicYearId} onChange={(e) => setAcademicYearId(e.target.value)} sx={{ minWidth: 200 }}>
						{years.map((year) => (
							<MenuItem key={year.id} value={year.id}>
								{year.name}
							</MenuItem>
						))}
					</TextField>
					<TextField select label="Class" value={schoolClassId} onChange={(e) => setSchoolClassId(e.target.value)} sx={{ minWidth: 200 }}>
						<MenuItem value="">{selectedCampusId === null ? "All classes" : "All classes in this campus"}</MenuItem>
						{campusClasses.map((schoolClass) => (
							<MenuItem key={schoolClass.id} value={schoolClass.id}>
								{classLabel(schoolClass)}
							</MenuItem>
						))}
					</TextField>
					<TextField
						label="Due on or before"
						type="date"
						value={dueOnOrBefore}
						onChange={(e) => setDueOnOrBefore(e.target.value)}
						slotProps={{ inputLabel: { shrink: true } }}
					/>
				</Stack>

				{selectedCampusId !== null && classes.length > 0 && campusClasses.length === 0 && (
					<Alert severity="info">No classes in the selected campus.</Alert>
				)}
				{error && <Alert severity="error">{error}</Alert>}
				{preview && (
					<Alert severity={preview.toGenerate > 0 ? "info" : "success"}>
						{preview.toGenerate > 0
							? `${preview.toGenerate} invoice${preview.toGenerate === 1 ? "" : "s"} will be generated (${preview.alreadyInvoiced} already invoiced).`
							: `Nothing to generate - ${preview.alreadyInvoiced} due installment${preview.alreadyInvoiced === 1 ? " is" : "s are"} already invoiced.`}
					</Alert>
				)}
				{result && (
					<Alert severity={result.failures.length > 0 ? "warning" : "success"}>
						Generated {result.generated} invoice{result.generated === 1 ? "" : "s"}.
						{result.failures.length > 0 &&
							` ${result.failures.length} could not be generated: ${result.failures
								.slice(0, 3)
								.map((f) => f.reason)
								.join("; ")}${result.failures.length > 3 ? "; ..." : ""}`}
					</Alert>
				)}

				<Stack direction="row" spacing={1}>
					<Button variant="outlined" disabled={busy || !academicYearId || noCampusClasses} onClick={() => run(true)}>
						Preview
					</Button>
					<Button variant="contained" disabled={busy || !preview || preview.toGenerate === 0} onClick={() => run(false)}>
						Generate {preview && preview.toGenerate > 0 ? `${preview.toGenerate} invoice${preview.toGenerate === 1 ? "" : "s"}` : ""}
					</Button>
				</Stack>
			</Stack>
		</Paper>
	);
}
