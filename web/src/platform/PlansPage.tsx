import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
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
import { changePlanStatus, createPlan, listPlans, type PlanResponse } from "./api/plans";
import { PlatformApiError } from "./api/platformClient";

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

export function PlansPage() {
	const [plans, setPlans] = useState<PlanResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [code, setCode] = useState("");
	const [name, setName] = useState("");
	const [price, setPrice] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listPlans()
			.then(setPlans)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load plans"));
	}

	useEffect(refresh, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createPlan({ code, name, pricePerStudentPerYear: Number(price) });
			setDialogOpen(false);
			setCode("");
			setName("");
			setPrice("");
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to create plan");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleToggleStatus(plan: PlanResponse) {
		try {
			await changePlanStatus(plan.id, plan.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to change plan status");
		}
	}

	return (
		<Stack spacing={2}>
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					Billing catalog
				</Typography>
				<Typography variant="h4" component="h1">
					Plans
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Plans</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
							Add plan
						</Button>
					</Box>

					{plans.length === 0 && <Alert severity="info">No plans yet.</Alert>}

					{plans.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Code</TableCell>
										<TableCell>Name</TableCell>
										<TableCell>Price / student / year</TableCell>
										<TableCell>Status</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{plans.map((plan) => (
										<TableRow key={plan.id}>
											<TableCell>{plan.code}</TableCell>
											<TableCell>{plan.name}</TableCell>
											<TableCell>{currency(plan.pricePerStudentPerYear)}</TableCell>
											<TableCell>
												<Chip label={plan.status} size="small" color={plan.status === "ACTIVE" ? "success" : "default"} />
											</TableCell>
											<TableCell>
												<Button size="small" onClick={() => handleToggleStatus(plan)}>
													{plan.status === "ACTIVE" ? "Deactivate" : "Activate"}
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

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add plan</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} required fullWidth />
						<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
						<TextField
							label="Price per student per year"
							type="number"
							value={price}
							onChange={(e) => setPrice(e.target.value)}
							required
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !code || !name || !price}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
