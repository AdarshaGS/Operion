import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
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
import { createRoute, listRoutes, type RouteResponse } from "../../api/transportRoutes";
import { listVehicles, type VehicleResponse } from "../../api/vehicles";

export function RoutesPanel() {
	const navigate = useNavigate();
	const [routes, setRoutes] = useState<RouteResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [campusId, setCampusId] = useState("");
	const [name, setName] = useState("");
	const [code, setCode] = useState("");
	const [vehicleId, setVehicleId] = useState("");
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listRoutes()
			.then(setRoutes)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load routes"));
	}

	useEffect(refresh, []);
	useEffect(() => {
		listCampuses().then(setCampuses).catch(() => {});
		listVehicles().then(setVehicles).catch(() => {});
	}, []);

	function campusName(id: number): string {
		return campuses.find((c) => c.id === id)?.name ?? `Campus #${id}`;
	}

	function vehicleLabel(id: number | null): string {
		if (id === null) return "—";
		const vehicle = vehicles.find((v) => v.id === id);
		return vehicle ? vehicle.registrationNumber : `Vehicle #${id}`;
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createRoute({ campusId: Number(campusId), name, code, vehicleId: vehicleId ? Number(vehicleId) : null });
			setCampusId("");
			setName("");
			setCode("");
			setVehicleId("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create route");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Routes</Typography>
					<Button
						size="small"
						startIcon={<AddIcon />}
						onClick={() => {
							listVehicles().then(setVehicles).catch(() => {});
							setDialogOpen(true);
						}}
					>
						Add route
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{routes.length === 0 && <Alert severity="info">No routes yet.</Alert>}

				{routes.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Name</TableCell>
									<TableCell>Code</TableCell>
									<TableCell>Campus</TableCell>
									<TableCell>Vehicle</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{routes.map((route) => (
									<TableRow key={route.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/transport/routes/${route.id}`)}>
										<TableCell>{route.name}</TableCell>
										<TableCell>{route.code}</TableCell>
										<TableCell>{campusName(route.campusId)}</TableCell>
										<TableCell>{vehicleLabel(route.vehicleId)}</TableCell>
										<TableCell>
											<Chip label={route.status} size="small" />
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add route</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value)} required fullWidth>
							{campuses.map((campus) => (
								<MenuItem key={campus.id} value={campus.id}>
									{campus.name}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Name" placeholder="Route 12 - North" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
						<TextField label="Code" placeholder="R12" value={code} onChange={(e) => setCode(e.target.value)} required fullWidth />
						<TextField select label="Vehicle (optional)" value={vehicleId} onChange={(e) => setVehicleId(e.target.value)} fullWidth>
							<MenuItem value="">— None —</MenuItem>
							{vehicles.map((vehicle) => (
								<MenuItem key={vehicle.id} value={vehicle.id}>
									{vehicle.registrationNumber}
								</MenuItem>
							))}
						</TextField>
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
