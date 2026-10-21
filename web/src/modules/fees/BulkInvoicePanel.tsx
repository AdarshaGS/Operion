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
import { useSchoolClassLabel } from "../../utils/useSchoolClassLabel";

function today(): string {
	return new Date().toISOString().slice(0, 10);
}

/** Generate every invoice that has fallen due in one action: preview the count, then generate. */
export function BulkInvoicePanel({ classes, onGenerated }: { classes: SchoolClassResponse[]; onGenerated: () => void }) {
	const classLabel = useSchoolClassLabel();
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

	// Any change to the inputs invalidates the last preview.
	useEffect(() => {
		setPreview(null);
	}, [academicYearId, schoolClassId, dueOnOrBefore]);

	async function run(dryRun: boolean) {
		if (!academicYearId || !dueOnOrBefore) return;
		setBusy(true);
		setError(null);
		setResult(null);
		try {
			const response = await bulkGenerateInvoices({
				academicYearId: Number(academicYearId),
				schoolClassId: schoolClassId ? Number(schoolClassId) : undefined,
				dueOnOrBefore,
				dryRun,
			});
			if (dryRun) {
				setPreview(response);
			} else {
				setPreview(null);
				setResult(response);
				onGenerated();
			}
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to generate invoices");
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
						<MenuItem value="">All classes</MenuItem>
						{classes.map((schoolClass) => (
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
					<Button variant="outlined" disabled={busy || !academicYearId} onClick={() => run(true)}>
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
