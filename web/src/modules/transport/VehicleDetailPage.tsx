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
import { getDriverProfile, upsertDriverProfile, type DriverProfileResponse } from "../../api/driverProfiles";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listVehicleRoster, type RouteRosterEntryResponse } from "../../api/transportAssignments";
import {
	addVehicleDocument,
	listVehicleDocuments,
	listVehicles,
	reassignCrew,
	verifyVehicleDocument,
	type VehicleDocumentResponse,
	type VehicleResponse,
} from "../../api/vehicles";

function documentExpiryChip(expiryDate: string | null) {
	if (!expiryDate) return null;
	const daysUntil = (new Date(expiryDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24);
	if (daysUntil < 0) return <Chip label={`Expired ${expiryDate}`} size="small" color="error" />;
	if (daysUntil <= 30) return <Chip label={`Expires ${expiryDate}`} size="small" color="warning" />;
	return <Chip label={`Expires ${expiryDate}`} size="small" variant="outlined" />;
}

function licenceChip(profile: DriverProfileResponse | null) {
	if (!profile) return <Chip label="No licence on file" size="small" variant="outlined" />;
	const daysUntil = (new Date(profile.licenceExpiryDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24);
	const label = `${profile.licenceNumber} — expires ${profile.licenceExpiryDate}`;
	if (daysUntil < 0) return <Chip label={label} size="small" color="error" />;
	if (daysUntil <= 30) return <Chip label={label} size="small" color="warning" />;
	return <Chip label={label} size="small" variant="outlined" />;
}

/** No GET-by-id exists for Vehicle - resolving via list+find, same tradeoff documented for
 * RouteDetailPage at this data scale. */
export function VehicleDetailPage() {
	const { vehicleId } = useParams<{ vehicleId: string }>();
	const navigate = useNavigate();

	const [vehicle, setVehicle] = useState<VehicleResponse | null>(null);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [roster, setRoster] = useState<RouteRosterEntryResponse[]>([]);
	const [documents, setDocuments] = useState<VehicleDocumentResponse[]>([]);
	const [driverProfile, setDriverProfile] = useState<DriverProfileResponse | null>(null);
	const [attendantProfile, setAttendantProfile] = useState<DriverProfileResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);

	const [documentDialogOpen, setDocumentDialogOpen] = useState(false);
	const [documentType, setDocumentType] = useState("");
	const [fileName, setFileName] = useState("");
	const [fileReference, setFileReference] = useState("");
	const [documentExpiryDate, setDocumentExpiryDate] = useState("");

	const [crewDialogOpen, setCrewDialogOpen] = useState(false);
	const [crewDriverPersonId, setCrewDriverPersonId] = useState("");
	const [crewAttendantPersonId, setCrewAttendantPersonId] = useState("");

	const [licenceDialogPersonId, setLicenceDialogPersonId] = useState<number | null>(null);
	const [licenceNumber, setLicenceNumber] = useState("");
	const [licenceExpiryDate, setLicenceExpiryDate] = useState("");

	useEffect(() => {
		if (!vehicleId) return;
		listVehicles()
			.then((vehicles) => {
				const found = vehicles.find((v) => v.id === Number(vehicleId));
				setVehicle(found ?? null);
				refreshLicences(found ?? null);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load vehicle"))
			.finally(() => setLoading(false));
		listPersons().then(setPersons).catch(() => {});
		listVehicleRoster(Number(vehicleId))
			.then(setRoster)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load student roster"));
		refreshDocuments();
	}, [vehicleId]);

	function refreshDocuments() {
		if (!vehicleId) return;
		listVehicleDocuments(Number(vehicleId))
			.then(setDocuments)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load documents"));
	}

	function refreshLicences(currentVehicle: VehicleResponse | null) {
		if (currentVehicle?.driverPersonId) {
			getDriverProfile(currentVehicle.driverPersonId).then(setDriverProfile).catch(() => setDriverProfile(null));
		} else {
			setDriverProfile(null);
		}
		if (currentVehicle?.attendantPersonId) {
			getDriverProfile(currentVehicle.attendantPersonId).then(setAttendantProfile).catch(() => setAttendantProfile(null));
		} else {
			setAttendantProfile(null);
		}
	}

	function personName(id: number | null): string {
		if (!id) return "—";
		const person = persons.find((p) => p.id === id);
		return person ? `${person.firstName} ${person.lastName}` : `Person #${id}`;
	}

	async function handleReassignCrew(event: FormEvent) {
		event.preventDefault();
		if (!vehicleId) return;
		setSubmitting(true);
		try {
			const updated = await reassignCrew(Number(vehicleId), {
				driverPersonId: crewDriverPersonId ? Number(crewDriverPersonId) : null,
				attendantPersonId: crewAttendantPersonId ? Number(crewAttendantPersonId) : null,
			});
			setVehicle(updated);
			refreshLicences(updated);
			setCrewDialogOpen(false);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to reassign crew");
		} finally {
			setSubmitting(false);
		}
	}

	function openLicenceDialog(personId: number, existing: DriverProfileResponse | null) {
		setLicenceDialogPersonId(personId);
		setLicenceNumber(existing?.licenceNumber ?? "");
		setLicenceExpiryDate(existing?.licenceExpiryDate ?? "");
	}

	async function handleSaveLicence(event: FormEvent) {
		event.preventDefault();
		if (!licenceDialogPersonId) return;
		setSubmitting(true);
		try {
			await upsertDriverProfile(licenceDialogPersonId, { licenceNumber, licenceExpiryDate });
			setLicenceDialogPersonId(null);
			refreshLicences(vehicle);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save licence");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleAddDocument(event: FormEvent) {
		event.preventDefault();
		if (!vehicleId) return;
		setSubmitting(true);
		try {
			await addVehicleDocument(Number(vehicleId), { documentType, fileReference, fileName, expiryDate: documentExpiryDate || null });
			setDocumentType("");
			setFileReference("");
			setFileName("");
			setDocumentExpiryDate("");
			setDocumentDialogOpen(false);
			refreshDocuments();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add document");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleVerifyDocument(id: number, verificationStatus: string) {
		try {
			await verifyVehicleDocument(id, { verificationStatus, verifiedBy: 1 });
			refreshDocuments();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to verify document");
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
				{vehicle?.registrationNumber ?? `Vehicle #${vehicleId}`}
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Crew</Typography>
						<Button
							size="small"
							onClick={() => {
								setCrewDriverPersonId(vehicle?.driverPersonId ? String(vehicle.driverPersonId) : "");
								setCrewAttendantPersonId(vehicle?.attendantPersonId ? String(vehicle.attendantPersonId) : "");
								setCrewDialogOpen(true);
							}}
						>
							Reassign crew
						</Button>
					</Box>

					<Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
						<Typography sx={{ minWidth: 90 }}>Driver</Typography>
						<Typography>{personName(vehicle?.driverPersonId ?? null)}</Typography>
						{licenceChip(driverProfile)}
						{vehicle?.driverPersonId && (
							<Button size="small" onClick={() => openLicenceDialog(vehicle.driverPersonId!, driverProfile)}>
								Edit licence
							</Button>
						)}
					</Box>
					<Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
						<Typography sx={{ minWidth: 90 }}>Attendant</Typography>
						<Typography>{personName(vehicle?.attendantPersonId ?? null)}</Typography>
						{licenceChip(attendantProfile)}
						{vehicle?.attendantPersonId && (
							<Button size="small" onClick={() => openLicenceDialog(vehicle.attendantPersonId!, attendantProfile)}>
								Edit licence
							</Button>
						)}
					</Box>
				</Stack>
			</Paper>

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

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Documents</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setDocumentDialogOpen(true)}>
							Add document
						</Button>
					</Box>

					{documents.length === 0 && <Alert severity="info">No documents on file.</Alert>}

					{documents.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Type</TableCell>
										<TableCell>File name</TableCell>
										<TableCell>Expiry</TableCell>
										<TableCell>Verification</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{documents.map((doc) => (
										<TableRow key={doc.id}>
											<TableCell>{doc.documentType}</TableCell>
											<TableCell>{doc.fileName}</TableCell>
											<TableCell>{documentExpiryChip(doc.expiryDate) ?? "—"}</TableCell>
											<TableCell>
												<Chip label={doc.verificationStatus} size="small" />
											</TableCell>
											<TableCell>
												{doc.verificationStatus === "PENDING" && (
													<>
														<Button size="small" onClick={() => handleVerifyDocument(doc.id, "VERIFIED")}>
															Verify
														</Button>
														<Button size="small" color="error" onClick={() => handleVerifyDocument(doc.id, "REJECTED")}>
															Reject
														</Button>
													</>
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

			<Dialog
				open={documentDialogOpen}
				onClose={() => setDocumentDialogOpen(false)}
				component="form"
				onSubmit={handleAddDocument}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Add document</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Document type"
							placeholder="INSURANCE"
							value={documentType}
							onChange={(e) => setDocumentType(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<TextField label="File name" value={fileName} onChange={(e) => setFileName(e.target.value)} required fullWidth />
						<TextField
							label="File reference"
							placeholder="Storage key / URL"
							value={fileReference}
							onChange={(e) => setFileReference(e.target.value)}
							required
							fullWidth
						/>
						<TextField
							label="Expiry date (optional)"
							type="date"
							value={documentExpiryDate}
							onChange={(e) => setDocumentExpiryDate(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDocumentDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog
				open={crewDialogOpen}
				onClose={() => setCrewDialogOpen(false)}
				component="form"
				onSubmit={handleReassignCrew}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Reassign crew</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							select
							label="Driver"
							value={crewDriverPersonId}
							onChange={(e) => setCrewDriverPersonId(e.target.value)}
							fullWidth
						>
							<MenuItem value="">None</MenuItem>
							{persons.map((person) => (
								<MenuItem key={person.id} value={person.id}>
									{person.firstName} {person.lastName}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Attendant"
							value={crewAttendantPersonId}
							onChange={(e) => setCrewAttendantPersonId(e.target.value)}
							fullWidth
						>
							<MenuItem value="">None</MenuItem>
							{persons.map((person) => (
								<MenuItem key={person.id} value={person.id}>
									{person.firstName} {person.lastName}
								</MenuItem>
							))}
						</TextField>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setCrewDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog
				open={licenceDialogPersonId !== null}
				onClose={() => setLicenceDialogPersonId(null)}
				component="form"
				onSubmit={handleSaveLicence}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Edit licence</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Licence number"
							value={licenceNumber}
							onChange={(e) => setLicenceNumber(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<TextField
							label="Licence expiry date"
							type="date"
							value={licenceExpiryDate}
							onChange={(e) => setLicenceExpiryDate(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
							required
							fullWidth
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setLicenceDialogPersonId(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
