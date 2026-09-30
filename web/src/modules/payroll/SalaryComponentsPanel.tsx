import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import {
	changeSalaryComponentStatus,
	createSalaryComponent,
	listSalaryComponents,
	type CalculationBasis,
	type ComponentType,
	type SalaryComponentResponse,
} from "../../api/payroll";

/** The org's own catalog of earnings/deductions (#222) - starts empty, nothing is pre-named after a
 * country's statutory scheme. Type and basis can't change once created; retire via Deactivate. */
export function SalaryComponentsPanel() {
	const [components, setComponents] = useState<SalaryComponentResponse[]>([]);
	const [name, setName] = useState("");
	const [type, setType] = useState<ComponentType>("EARNING");
	const [basis, setBasis] = useState<CalculationBasis>("FLAT");
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		listSalaryComponents()
			.then(setComponents)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load components"));
	}

	useEffect(refresh, []);

	async function handleCreate(event: FormEvent) {
		event.preventDefault();
		setError(null);
		try {
			await createSalaryComponent({ name, type, calculationBasis: basis });
			setName("");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create component");
		}
	}

	async function toggle(component: SalaryComponentResponse) {
		setError(null);
		try {
			await changeSalaryComponentStatus(component.id, component.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update component");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Salary components</Typography>
				{error && <Alert severity="error">{error}</Alert>}
				<Can anyOf={["PAYROLL_MANAGE"]}>
					<Stack component="form" direction="row" spacing={2} onSubmit={handleCreate} sx={{ flexWrap: "wrap", gap: 2 }}>
						<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required sx={{ minWidth: 220 }} />
						<TextField select label="Type" value={type} onChange={(e) => setType(e.target.value as ComponentType)} sx={{ minWidth: 150 }}>
							<MenuItem value="EARNING">Earning</MenuItem>
							<MenuItem value="DEDUCTION">Deduction</MenuItem>
						</TextField>
						<TextField
							select
							label="Calculated as"
							value={basis}
							onChange={(e) => setBasis(e.target.value as CalculationBasis)}
							sx={{ minWidth: 200 }}
						>
							<MenuItem value="FLAT">Flat amount</MenuItem>
							<MenuItem value="PERCENT_OF_BASIC">Percentage of basic</MenuItem>
						</TextField>
						<Button type="submit" variant="contained">
							Add component
						</Button>
					</Stack>
				</Can>
				{components.length === 0 ? (
					<Alert severity="info">No components yet. Add the earnings and deductions your organisation pays, then build salary structures from them.</Alert>
				) : (
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Name</TableCell>
								<TableCell>Type</TableCell>
								<TableCell>Calculated as</TableCell>
								<TableCell>Status</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{components.map((component) => (
								<TableRow key={component.id}>
									<TableCell>{component.name}</TableCell>
									<TableCell>{component.type === "EARNING" ? "Earning" : "Deduction"}</TableCell>
									<TableCell>{component.calculationBasis === "FLAT" ? "Flat amount" : "% of basic"}</TableCell>
									<TableCell>
										<Chip label={component.status} size="small" color={component.status === "ACTIVE" ? "success" : "default"} />
									</TableCell>
									<TableCell align="right">
										<Can anyOf={["PAYROLL_MANAGE"]}>
											<Button size="small" onClick={() => toggle(component)}>
												{component.status === "ACTIVE" ? "Deactivate" : "Activate"}
											</Button>
										</Can>
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				)}
			</Stack>
		</Paper>
	);
}
