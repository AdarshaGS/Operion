import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
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
import { ApiError } from "../../api/client";
import { changeSectionStatus, createSection, listSections, updateSection, type SectionResponse } from "../../api/sections";

/** Sections for one class offering - a standalone panel (#246) so it can be reused both
 * in the Academics tabbed configuration area and on SchoolClassSectionsPage's own
 * per-class route, from a single source of truth. */
export function SectionsPanel({ classId, onSectionClick }: { classId: number; onSectionClick?: (section: SectionResponse) => void }) {
	const [sections, setSections] = useState<SectionResponse[] | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [name, setName] = useState("");
	const [capacity, setCapacity] = useState("");
	const [room, setRoom] = useState("");
	const [submitting, setSubmitting] = useState(false);

	const [editTarget, setEditTarget] = useState<SectionResponse | null>(null);
	const [editName, setEditName] = useState("");
	const [editCapacity, setEditCapacity] = useState("");
	const [editRoom, setEditRoom] = useState("");

	function refresh() {
		listSections(classId)
			.then(setSections)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load sections"));
	}

	useEffect(() => {
		setSections(null);
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [classId]);

	async function handleToggleStatus(section: SectionResponse) {
		try {
			await changeSectionStatus(classId, section.id, section.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update section status");
		}
	}

	function openEdit(section: SectionResponse) {
		setEditTarget(section);
		setEditName(section.name);
		setEditCapacity(section.capacity != null ? String(section.capacity) : "");
		setEditRoom(section.room ?? "");
	}

	async function handleEditSave(event: FormEvent) {
		event.preventDefault();
		if (!editTarget) return;
		setSubmitting(true);
		try {
			await updateSection(classId, editTarget.id, {
				name: editName,
				capacity: editCapacity ? Number(editCapacity) : null,
				room: editRoom || null,
			});
			setEditTarget(null);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update section");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createSection(classId, { name, capacity: capacity ? Number(capacity) : null, room: room || null });
			setName("");
			setCapacity("");
			setRoom("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create section");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Sections</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add section
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{!sections && (
					<Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
						<CircularProgress size={24} />
					</Box>
				)}

				{sections && sections.length === 0 && <Alert severity="info">No sections yet.</Alert>}

				{sections && sections.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Name</TableCell>
									<TableCell>Capacity</TableCell>
									<TableCell>Room</TableCell>
									<TableCell>Status</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{sections.map((section) => (
									<TableRow
										key={section.id}
										hover={Boolean(onSectionClick)}
										sx={onSectionClick ? { cursor: "pointer" } : undefined}
										onClick={() => onSectionClick?.(section)}
									>
										<TableCell>{section.name}</TableCell>
										<TableCell>{section.capacity ?? "—"}</TableCell>
										<TableCell>{section.room ?? "—"}</TableCell>
										<TableCell>
											<Chip label={section.status} size="small" />
										</TableCell>
										<TableCell onClick={(e) => e.stopPropagation()}>
											<Button size="small" onClick={() => openEdit(section)}>
												Edit
											</Button>
											<Button size="small" onClick={() => handleToggleStatus(section)}>
												{section.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
											</Button>
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add section</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Name" placeholder="A" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
						<TextField label="Capacity" type="number" value={capacity} onChange={(e) => setCapacity(e.target.value)} fullWidth />
						<TextField label="Room" value={room} onChange={(e) => setRoom(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={editTarget !== null} onClose={() => setEditTarget(null)} component="form" onSubmit={handleEditSave} fullWidth maxWidth="xs">
				<DialogTitle>Edit section</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Name" value={editName} onChange={(e) => setEditName(e.target.value)} required autoFocus fullWidth />
						<TextField label="Capacity" type="number" value={editCapacity} onChange={(e) => setEditCapacity(e.target.value)} fullWidth />
						<TextField label="Room" value={editRoom} onChange={(e) => setEditRoom(e.target.value)} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setEditTarget(null)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
