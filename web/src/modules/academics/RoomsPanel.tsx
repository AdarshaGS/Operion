import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
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
import { ApiError } from "../../api/client";
import { createRoom, deleteRoom, listRooms, updateRoom, type RoomResponse } from "../../api/timetable";
import { Can } from "../../auth/Can";

/** Rooms a timetable slot can be held in (classrooms, labs, a hall). A room cannot host two sections at overlapping
 * times, and one that is in use can be deactivated but not deleted. */
export function RoomsPanel() {
	const [rooms, setRooms] = useState<RoomResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [editing, setEditing] = useState<RoomResponse | null>(null);
	const [name, setName] = useState("");
	const [roomType, setRoomType] = useState("");
	const [capacity, setCapacity] = useState("");
	const [active, setActive] = useState(true);

	function refresh() {
		listRooms()
			.then(setRooms)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load rooms"));
	}

	useEffect(refresh, []);

	function openCreate() {
		setEditing(null);
		setName("");
		setRoomType("");
		setCapacity("");
		setActive(true);
		setError(null);
		setDialogOpen(true);
	}

	function openEdit(room: RoomResponse) {
		setEditing(room);
		setName(room.name);
		setRoomType(room.roomType ?? "");
		setCapacity(room.capacity === null ? "" : String(room.capacity));
		setActive(room.active);
		setError(null);
		setDialogOpen(true);
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setError(null);
		try {
			const body = { name, roomType: roomType || null, capacity: capacity ? Number(capacity) : null, active };
			if (editing) {
				await updateRoom(editing.id, body);
			} else {
				await createRoom(body);
			}
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save room");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleDelete(id: number) {
		setError(null);
		try {
			await deleteRoom(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to delete room");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Rooms</Typography>
					<Can anyOf={["TIMETABLE_MANAGE"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={openCreate}>
							Add room
						</Button>
					</Can>
				</Box>

				{error && !dialogOpen && <Alert severity="error">{error}</Alert>}
				{rooms.length === 0 && <Alert severity="info">No rooms yet. Add the classrooms and labs timetable slots can be held in.</Alert>}

				{rooms.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Name</TableCell>
									<TableCell>Type</TableCell>
									<TableCell>Capacity</TableCell>
									<TableCell>Status</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{rooms.map((room) => (
									<TableRow key={room.id}>
										<TableCell>{room.name}</TableCell>
										<TableCell>{room.roomType ?? "—"}</TableCell>
										<TableCell>{room.capacity ?? "—"}</TableCell>
										<TableCell>
											<Chip label={room.active ? "Active" : "Inactive"} size="small" />
										</TableCell>
										<TableCell align="right">
											<Can anyOf={["TIMETABLE_MANAGE"]}>
												<Button size="small" onClick={() => openEdit(room)}>
													Edit
												</Button>
												<Button size="small" color="error" onClick={() => handleDelete(room.id)}>
													Delete
												</Button>
											</Can>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>{editing ? "Edit room" : "Add room"}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{error && <Alert severity="error">{error}</Alert>}
						<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
						<TextField label="Type" value={roomType} onChange={(e) => setRoomType(e.target.value)} helperText="e.g. Classroom, Lab, Hall" fullWidth />
						<TextField
							label="Capacity"
							type="number"
							value={capacity}
							onChange={(e) => setCapacity(e.target.value)}
							slotProps={{ htmlInput: { min: 1 } }}
							fullWidth
						/>
						{editing && <FormControlLabel control={<Checkbox checked={active} onChange={(e) => setActive(e.target.checked)} />} label="Active" />}
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !name.trim()}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
