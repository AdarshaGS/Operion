import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
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
import { type CampusResponse, listCampuses } from "../../api/campuses";
import { ApiError } from "../../api/client";
import { changeVehicleStatus, createVehicle, listVehicles, type VehicleResponse } from "../../api/vehicles";

const VEHICLE_TYPES = ["BUS", "VAN", "CAR"];
const VEHICLE_STATUSES = ["ACTIVE", "MAINTENANCE", "INACTIVE", "RETIRED"];

export function VehiclesPanel() {
	const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [campusId, setCampusId] = useState("");
	const [registrationNumber, setRegistrationNumber] = useState("");
	const [vehicleType, setVehicleType] = useState("BUS");
	const [capacity, setCapacity] = useState("40");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listVehicles()
			.then(setVehicles)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load vehicles"));
	}

	useEffect(refresh, []);
	useEffect(() => {
		listCampuses().then(setCampuses).catch(() => {});
	}, []);

	function campusName(id: number): string {
		return campuses.find((c) => c.id === id)?.name ?? `Campus #${id}`;
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createVehicle({ campusId: Number(campusId), registrationNumber, vehicleType, capacity: Number(capacity) });
			setCampusId("");
			setRegistrationNumber("");
			setVehicleType("BUS");
			setCapacity("40");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create vehicle");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleStatusChange(id: number, status: string) {
		try {
			await changeVehicleStatus(id, status);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to change vehicle status");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Vehicles</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add vehicle
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{vehicles.length === 0 && <Alert severity="info">No vehicles yet.</Alert>}

				{vehicles.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Registration</TableCell>
									<TableCell>Type</TableCell>
									<TableCell>Capacity</TableCell>
									<TableCell>Campus</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{vehicles.map((vehicle) => (
									<TableRow key={vehicle.id}>
										<TableCell>{vehicle.registrationNumber}</TableCell>
										<TableCell>{vehicle.vehicleType}</TableCell>
										<TableCell>{vehicle.capacity}</TableCell>
										<TableCell>{campusName(vehicle.campusId)}</TableCell>
										<TableCell>
											<TextField
												select
												size="small"
												value={vehicle.status}
												onChange={(e) => handleStatusChange(vehicle.id, e.target.value)}
												sx={{ minWidth: 140 }}
											>
												{VEHICLE_STATUSES.map((status) => (
													<MenuItem key={status} value={status}>
														{status}
													</MenuItem>
												))}
											</TextField>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add vehicle</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value)} required fullWidth>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Registration number"
							value={registrationNumber}
							onChange={(e) => setRegistrationNumber(e.target.value)}
							required
							fullWidth
						/>
						<TextField select label="Type" value={vehicleType} onChange={(e) => setVehicleType(e.target.value)} required fullWidth>
							{VEHICLE_TYPES.map((type) => (
								<MenuItem key={type} value={type}>
									{type}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Capacity" type="number" value={capacity} onChange={(e) => setCapacity(e.target.value)} required fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
