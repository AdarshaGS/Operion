import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
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
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { ApiError } from "../../api/client";
import { addRouteStop, listRoutes, listRouteStops, type RouteResponse, type RouteStopResponse } from "../../api/transportRoutes";
import { cancelTrip, completeTrip, listTripLogs, scheduleTrip, startTrip, type TripLogResponse } from "../../api/tripLogs";
import { listVehicles, type VehicleResponse } from "../../api/vehicles";

const TRIP_TYPES = ["PICKUP", "DROP"];

function todayIso(): string {
	return new Date().toISOString().slice(0, 10);
}

/** No GET-by-id exists for Route (or TripLog) - resolving via list+find, same tradeoff
 * documented for SchoolClassSectionsPage and MarksEntryPage at this data scale. */
export function RouteDetailPage() {
	const { routeId } = useParams<{ routeId: string }>();
	const navigate = useNavigate();

	const [route, setRoute] = useState<RouteResponse | null>(null);
	const [stops, setStops] = useState<RouteStopResponse[]>([]);
	const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	const [stopDialogOpen, setStopDialogOpen] = useState(false);
	const [stopName, setStopName] = useState("");
	const [sequenceNumber, setSequenceNumber] = useState("1");
	const [pickupTime, setPickupTime] = useState("");
	const [dropTime, setDropTime] = useState("");

	const [tripDate, setTripDate] = useState(todayIso());
	const [trips, setTrips] = useState<TripLogResponse[]>([]);
	const [tripDialogOpen, setTripDialogOpen] = useState(false);
	const [tripVehicleId, setTripVehicleId] = useState("");
	const [tripType, setTripType] = useState("PICKUP");

	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		if (!routeId) return;
		listRoutes()
			.then((routes) => {
				const found = routes.find((r) => r.id === Number(routeId));
				setRoute(found ?? null);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load route"))
			.finally(() => setLoading(false));
		listVehicles().then(setVehicles).catch(() => {});
		refreshStops();
	}, [routeId]);

	useEffect(refreshTrips, [routeId, tripDate]);

	function refreshStops() {
		if (!routeId) return;
		listRouteStops(Number(routeId))
			.then(setStops)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load stops"));
	}

	function refreshTrips() {
		if (!routeId) return;
		listTripLogs(Number(routeId), tripDate)
			.then(setTrips)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load trip logs"));
	}

	function vehicleLabel(id: number): string {
		return vehicles.find((v) => v.id === id)?.registrationNumber ?? `Vehicle #${id}`;
	}

	async function handleAddStop(event: FormEvent) {
		event.preventDefault();
		if (!routeId) return;
		setSubmitting(true);
		try {
			await addRouteStop(Number(routeId), {
				stopName,
				sequenceNumber: Number(sequenceNumber),
				pickupTime: pickupTime || null,
				dropTime: dropTime || null,
			});
			setStopName("");
			setSequenceNumber(String(stops.length + 2));
			setPickupTime("");
			setDropTime("");
			setStopDialogOpen(false);
			refreshStops();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add stop");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleScheduleTrip(event: FormEvent) {
		event.preventDefault();
		if (!routeId) return;
		setSubmitting(true);
		try {
			await scheduleTrip({ routeId: Number(routeId), vehicleId: Number(tripVehicleId), tripDate, tripType });
			setTripDialogOpen(false);
			refreshTrips();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to schedule trip");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleStart(id: number) {
		try {
			await startTrip(id);
			refreshTrips();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to start trip");
		}
	}

	async function handleComplete(id: number) {
		try {
			await completeTrip(id, "");
			refreshTrips();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to complete trip");
		}
	}

	async function handleCancel(id: number) {
		try {
			await cancelTrip(id, "");
			refreshTrips();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to cancel trip");
		}
	}

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/transport")}>
					Back to transport
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				{route?.name ?? `Route #${routeId}`}
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Stops</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setStopDialogOpen(true)}>
							Add stop
						</Button>
					</Box>

					{stops.length === 0 && <Alert severity="info">No stops yet.</Alert>}

					{stops.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>#</TableCell>
										<TableCell>Stop</TableCell>
										<TableCell>Pickup</TableCell>
										<TableCell>Drop</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{stops.map((stop) => (
										<TableRow key={stop.id}>
											<TableCell>{stop.sequenceNumber}</TableCell>
											<TableCell>{stop.stopName}</TableCell>
											<TableCell>{stop.pickupTime ?? "—"}</TableCell>
											<TableCell>{stop.dropTime ?? "—"}</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Trip logs</Typography>
						<Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
							<TextField
								label="Date"
								type="date"
								size="small"
								value={tripDate}
								onChange={(e) => setTripDate(e.target.value)}
								slotProps={{ inputLabel: { shrink: true } }}
							/>
							<Button
								size="small"
								startIcon={<AddIcon />}
								onClick={() => {
									listVehicles().then(setVehicles).catch(() => {});
									setTripDialogOpen(true);
								}}
							>
								Schedule trip
							</Button>
						</Box>
					</Box>

					{trips.length === 0 && <Alert severity="info">No trips scheduled for this date.</Alert>}

					{trips.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Type</TableCell>
										<TableCell>Vehicle</TableCell>
										<TableCell>Status</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{trips.map((trip) => (
										<TableRow key={trip.id}>
											<TableCell>{trip.tripType}</TableCell>
											<TableCell>{vehicleLabel(trip.vehicleId)}</TableCell>
											<TableCell>
												<Chip label={trip.status} size="small" />
											</TableCell>
											<TableCell>
												{trip.status === "SCHEDULED" && (
													<>
														<Button size="small" onClick={() => handleStart(trip.id)}>
															Start
														</Button>
														<Button size="small" color="error" onClick={() => handleCancel(trip.id)}>
															Cancel
														</Button>
													</>
												)}
												{trip.status === "IN_PROGRESS" && (
													<Button size="small" onClick={() => handleComplete(trip.id)}>
														Complete
													</Button>
												)}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Dialog open={stopDialogOpen} onClose={() => setStopDialogOpen(false)} component="form" onSubmit={handleAddStop} fullWidth maxWidth="xs">
				<DialogTitle>Add stop</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Stop name" value={stopName} onChange={(e) => setStopName(e.target.value)} required autoFocus fullWidth />
						<TextField
							label="Sequence"
							type="number"
							value={sequenceNumber}
							onChange={(e) => setSequenceNumber(e.target.value)}
							required
							fullWidth
						/>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField
								label="Pickup time"
								type="time"
								value={pickupTime}
								onChange={(e) => setPickupTime(e.target.value)}
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
							<TextField
								label="Drop time"
								type="time"
								value={dropTime}
								onChange={(e) => setDropTime(e.target.value)}
								slotProps={{ inputLabel: { shrink: true } }}
								fullWidth
							/>
						</Box>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setStopDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={tripDialogOpen} onClose={() => setTripDialogOpen(false)} component="form" onSubmit={handleScheduleTrip} fullWidth maxWidth="xs">
				<DialogTitle>Schedule trip</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Vehicle" value={tripVehicleId} onChange={(e) => setTripVehicleId(e.target.value)} required fullWidth>
							{vehicles.map((vehicle) => (
								<MenuItem key={vehicle.id} value={vehicle.id}>
									{vehicle.registrationNumber}
								</MenuItem>
							))}
						</TextField>
						<TextField select label="Type" value={tripType} onChange={(e) => setTripType(e.target.value)} required fullWidth>
							{TRIP_TYPES.map((type) => (
								<MenuItem key={type} value={type}>
									{type}
								</MenuItem>
							))}
						</TextField>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setTripDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !tripVehicleId}>
						Schedule
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
