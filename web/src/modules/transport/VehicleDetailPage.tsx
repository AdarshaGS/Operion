import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { ApiError } from "../../api/client";
import { listVehicleRoster, type RouteRosterEntryResponse } from "../../api/transportAssignments";
import { listVehicles, type VehicleResponse } from "../../api/vehicles";

/** No GET-by-id exists for Vehicle - resolving via list+find, same tradeoff documented for
 * RouteDetailPage at this data scale. */
export function VehicleDetailPage() {
	const { vehicleId } = useParams<{ vehicleId: string }>();
	const navigate = useNavigate();

	const [vehicle, setVehicle] = useState<VehicleResponse | null>(null);
	const [roster, setRoster] = useState<RouteRosterEntryResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		if (!vehicleId) return;
		listVehicles()
			.then((vehicles) => {
				const found = vehicles.find((v) => v.id === Number(vehicleId));
				setVehicle(found ?? null);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load vehicle"))
			.finally(() => setLoading(false));
		listVehicleRoster(Number(vehicleId))
			.then(setRoster)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load student roster"));
	}, [vehicleId]);

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
				{vehicle?.registrationNumber ?? `Vehicle #${vehicleId}`}
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Students</Typography>

					{roster.length === 0 && <Alert severity="info">No students assigned to this vehicle.</Alert>}

					{roster.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>#</TableCell>
										<TableCell>Stop</TableCell>
										<TableCell>Student</TableCell>
										<TableCell>Admission #</TableCell>
										<TableCell>Legs</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{roster.map((entry) => (
										<TableRow key={entry.assignmentId}>
											<TableCell>{entry.sequenceNumber}</TableCell>
											<TableCell>{entry.stopName}</TableCell>
											<TableCell>{entry.studentName}</TableCell>
											<TableCell>{entry.admissionNumber}</TableCell>
											<TableCell>
												{entry.usesPickup ? "Pickup" : ""}
												{entry.usesPickup && entry.usesDrop ? " & " : ""}
												{entry.usesDrop ? "Drop" : ""}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>
		</Stack>
	);
}
