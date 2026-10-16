import { useEffect, useMemo, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import {
	applyRollover,
	listRolloverSteps,
	previewRollover,
	type AcademicYearResponse,
	type RolloverReport,
	type RolloverRequest,
	type RolloverStepOption,
} from "../../api/academicYears";

interface Props {
	target: AcademicYearResponse | null;
	years: AcademicYearResponse[];
	onClose: () => void;
	onApplied: (report: RolloverReport) => void;
}

/** Copy-forward: set the new year up from an earlier one. Always previews first - the preview
 * runs the real logic and rolls it back, so what it lists is exactly what Copy will do - and
 * any change to the choices clears it, so Copy can never run on a stale preview. */
export function RolloverDialog({ target, years, onClose, onApplied }: Props) {
	const sources = useMemo(() => years.filter((year) => year.id !== target?.id), [years, target]);
	const [steps, setSteps] = useState<RolloverStepOption[]>([]);
	const [selected, setSelected] = useState<Set<string>>(new Set());
	const [sourceId, setSourceId] = useState<number | "">("");
	const [feeIncrease, setFeeIncrease] = useState("");
	const [report, setReport] = useState<RolloverReport | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		if (!target) {
			return;
		}
		setReport(null);
		setError(null);
		setFeeIncrease("");
		setSourceId((sources.find((year) => year.current) ?? sources[0])?.id ?? "");
		listRolloverSteps()
			.then((options) => {
				setSteps(options);
				setSelected(new Set(options.map((option) => option.key)));
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the copy-forward steps"));
	}, [target, sources]);

	function request(): RolloverRequest {
		const percent = feeIncrease.trim() === "" ? null : Number(feeIncrease);
		return {
			sourceYearId: Number(sourceId),
			steps: [...selected],
			feeIncreasePercent: percent !== null && Number.isFinite(percent) ? percent : null,
		};
	}

	function changed(update: () => void) {
		update();
		setReport(null);
	}

	async function run(action: "preview" | "apply") {
		if (!target) {
			return;
		}
		setBusy(true);
		setError(null);
		try {
			if (action === "preview") {
				setReport(await previewRollover(target.id, request()));
			} else {
				onApplied(await applyRollover(target.id, request()));
			}
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Copy-forward failed");
		} finally {
			setBusy(false);
		}
	}

	const nothingSelected = selected.size === 0;
	const totalToCopy = report ? report.steps.reduce((sum, step) => sum + step.items.reduce((inner, item) => inner + item.copied, 0), 0) : 0;

	return (
		<Dialog open={target !== null} onClose={busy ? undefined : onClose} fullWidth maxWidth="sm">
			<DialogTitle>Copy forward into {target?.name}</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ pt: 0.5 }}>
					<Typography variant="body2" color="text.secondary">
						Sets up this year from an earlier one: structure only (classes, assignments, timetable, fee amounts, leave
						entitlements). Attendance, invoices, payments and marks are never copied. Anything already in this year is left
						alone, so it is safe to run again.
					</Typography>
					{error && <Alert severity="error">{error}</Alert>}
					{sources.length === 0 ? (
						<Alert severity="info">There is no other academic year to copy from yet.</Alert>
					) : (
						<>
							<TextField
								select
								label="Copy from"
								value={sourceId}
								onChange={(event) => changed(() => setSourceId(Number(event.target.value)))}
								size="small"
							>
								{sources.map((year) => (
									<MenuItem key={year.id} value={year.id}>
										{year.name}
										{year.current ? " (current)" : ""}
									</MenuItem>
								))}
							</TextField>
							<Stack>
								{steps.map((step) => (
									<FormControlLabel
										key={step.key}
										control={
											<Checkbox
												checked={selected.has(step.key)}
												onChange={(event) =>
													changed(() =>
														setSelected((previous) => {
															const next = new Set(previous);
															if (event.target.checked) {
																next.add(step.key);
															} else {
																next.delete(step.key);
															}
															return next;
														}),
													)
												}
											/>
										}
										label={step.label}
									/>
								))}
							</Stack>
							{selected.has("FEE_STRUCTURES") && (
								<TextField
									label="Raise fees by (%)"
									type="number"
									size="small"
									value={feeIncrease}
									onChange={(event) => changed(() => setFeeIncrease(event.target.value))}
									helperText="Optional. Leave empty to keep amounts as they are; installment dates move forward by the year difference."
								/>
							)}
						</>
					)}
					{report && (
						<Stack spacing={1.5}>
							<Typography variant="subtitle2">What will be copied</Typography>
							{report.steps.map((step) => (
								<Stack key={step.key} spacing={0.25}>
									<Typography variant="body2" sx={{ fontWeight: 600 }}>
										{step.label}
									</Typography>
									{step.items.map((item) => (
										<Typography key={item.name} variant="body2" color="text.secondary">
											{item.name}: {item.copied} to copy
											{item.alreadyThere > 0 ? `, ${item.alreadyThere} already there` : ""}
										</Typography>
									))}
									{step.notes.map((note) => (
										<Alert key={note} severity="warning" variant="outlined" sx={{ py: 0 }}>
											{note}
										</Alert>
									))}
								</Stack>
							))}
							{report.unavailable.length > 0 && (
								<Typography variant="caption" color="text.secondary">
									Not offered because they are switched off for your school: {report.unavailable.join(", ")}.
								</Typography>
							)}
							{totalToCopy === 0 && <Alert severity="info">Nothing new to copy - this year already has everything.</Alert>}
						</Stack>
					)}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={busy}>
					Cancel
				</Button>
				<Button onClick={() => run("preview")} disabled={busy || sourceId === "" || nothingSelected}>
					Preview
				</Button>
				<Button variant="contained" onClick={() => run("apply")} disabled={busy || !report || totalToCopy === 0}>
					Copy forward
				</Button>
			</DialogActions>
		</Dialog>
	);
}
