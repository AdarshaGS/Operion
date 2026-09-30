import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import {
	createSalaryStructure,
	listSalaryComponents,
	listSalaryStructures,
	type SalaryComponentResponse,
	type SalaryStructureResponse,
} from "../../api/payroll";
import { money } from "./format";

/** A staff member's effective-dated compensation (#221). History is insert-only - a raise is a new
 * version, never an edit - so the newest row is what payroll reads for any month on/after its date. */
export function SalaryStructurePanel({ staffProfileId }: { staffProfileId: number }) {
	const [structures, setStructures] = useState<SalaryStructureResponse[]>([]);
	const [components, setComponents] = useState<SalaryComponentResponse[]>([]);
	const [effectiveFrom, setEffectiveFrom] = useState("");
	const [basicAmount, setBasicAmount] = useState("");
	const [values, setValues] = useState<Record<number, string>>({});
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		listSalaryStructures(staffProfileId)
			.then(setStructures)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load salary structures"));
	}

	useEffect(() => {
		refresh();
		listSalaryComponents()
			.then((rows) => setComponents(rows.filter((c) => c.status === "ACTIVE")))
			.catch(() => {});
	}, [staffProfileId]);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		const lines = components
			.filter((c) => (values[c.id] ?? "").trim() !== "")
			.map((c) => ({ componentId: c.id, value: Number(values[c.id]) }));
		try {
			await createSalaryStructure(staffProfileId, { effectiveFrom, basicAmount: Number(basicAmount), lines });
			setEffectiveFrom("");
			setBasicAmount("");
			setValues({});
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save salary structure");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Salary structure</Typography>
				{error && <Alert severity="error">{error}</Alert>}
				{structures.length === 0 ? (
					<Alert severity="info">No salary structure yet - this staff member is left out of payroll runs until one is added.</Alert>
				) : (
					structures.map((structure, index) => (
						<Stack key={structure.id} spacing={0.5}>
							<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
								<Typography variant="subtitle2">From {structure.effectiveFrom}</Typography>
								{index === 0 && <Chip label="latest" size="small" color="success" />}
							</Stack>
							<Typography variant="body2">Basic: {money(structure.basicAmount)}</Typography>
							{structure.lines.map((line) => (
								<Typography key={line.componentId} variant="body2" color="text.secondary">
									{line.type === "EARNING" ? "+" : "−"} {line.componentName}:{" "}
									{line.calculationBasis === "FLAT" ? money(line.value) : `${line.value}% of basic`}
								</Typography>
							))}
						</Stack>
					))
				)}
				<Can anyOf={["PAYROLL_MANAGE"]}>
					<Stack component="form" spacing={2} onSubmit={handleSubmit}>
						<Typography variant="subtitle2">New version (must take effect after the latest)</Typography>
						<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap", gap: 2 }}>
							<TextField
								type="date"
								label="Effective from"
								value={effectiveFrom}
								onChange={(e) => setEffectiveFrom(e.target.value)}
								required
								slotProps={{ inputLabel: { shrink: true } }}
							/>
							<TextField
								type="number"
								label="Basic amount"
								value={basicAmount}
								onChange={(e) => setBasicAmount(e.target.value)}
								required
								slotProps={{ htmlInput: { min: 0, step: "0.01" } }}
							/>
						</Stack>
						{components.length > 0 && (
							<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap", gap: 2 }}>
								{components.map((component) => (
									<TextField
										key={component.id}
										type="number"
										label={`${component.name} (${component.type === "EARNING" ? "earning" : "deduction"}, ${
											component.calculationBasis === "FLAT" ? "amount" : "% of basic"
										})`}
										value={values[component.id] ?? ""}
										onChange={(e) => setValues((prev) => ({ ...prev, [component.id]: e.target.value }))}
										helperText="Leave blank to skip"
										slotProps={{ htmlInput: { min: 0, step: "0.01" } }}
										sx={{ minWidth: 240 }}
									/>
								))}
							</Stack>
						)}
						<Stack direction="row">
							<Button type="submit" variant="contained">
								Save structure
							</Button>
						</Stack>
					</Stack>
				</Can>
			</Stack>
		</Paper>
	);
}
