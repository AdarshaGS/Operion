import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import { DialogErrorAlert } from "../../components/DialogErrorAlert";
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
import { type AcademicYearResponse, listAcademicYears } from "../../api/academicYears";
import { type CampusResponse, listCampuses } from "../../api/campuses";
import { ApiError } from "../../api/client";
import { type GradeLevelResponse, listGradeLevels } from "../../api/gradeLevels";
import {
	changeSchoolClassStatus,
	createSchoolClass,
	listSchoolClasses,
	updateSchoolClassDisplayName,
	type SchoolClassResponse,
} from "../../api/schoolClasses";
import { useSelectedCampusId } from "../../layout/selectedCampus";

export function SchoolClassesPanel() {
	const navigate = useNavigate();
	const selectedCampusId = useSelectedCampusId();
	const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [gradeLevels, setGradeLevels] = useState<GradeLevelResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	// Failures from inside a dialog show in that dialog: the page's own banner sits behind the modal.
	const [dialogError, setDialogError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [academicYearId, setAcademicYearId] = useState("");
	const [campusId, setCampusId] = useState("");
	const [gradeLevelId, setGradeLevelId] = useState("");
	const [displayName, setDisplayName] = useState("");
	const [submitting, setSubmitting] = useState(false);
	const [editTarget, setEditTarget] = useState<SchoolClassResponse | null>(null);
	const [editDisplayName, setEditDisplayName] = useState("");

	function refresh() {
		listSchoolClasses()
			.then(setClasses)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load classes"));
	}

	function refreshLookups() {
		listAcademicYears().then(setAcademicYears).catch(() => {});
		listCampuses().then(setCampuses).catch(() => {});
		listGradeLevels().then(setGradeLevels).catch(() => {});
	}

	// Grade levels (and, less often, campuses/academic years) can be added by a sibling
	// panel on the same page - refreshLookups() also re-runs right before the "Add
	// class" dialog opens, so this mount-time fetch is only for the empty-state hint.
	useEffect(() => {
		refresh();
		refreshLookups();
	}, []);

	async function handleToggleStatus(schoolClass: SchoolClassResponse) {
		try {
			await changeSchoolClassStatus(schoolClass.id, schoolClass.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update class status");
		}
	}

	function openEdit(schoolClass: SchoolClassResponse) {
		setDialogError(null);
		setEditTarget(schoolClass);
		setEditDisplayName(schoolClass.displayName ?? "");
	}

	async function handleEditSave(event: FormEvent) {
		event.preventDefault();
		if (!editTarget) return;
		setDialogError(null);
		setSubmitting(true);
		try {
			await updateSchoolClassDisplayName(editTarget.id, editDisplayName || null);
			setEditTarget(null);
			refresh();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : "Failed to update class");
		} finally {
			setSubmitting(false);
		}
	}

	const academicYearsById = new Map(academicYears.map((year) => [year.id, year]));
	const campusesById = new Map(campuses.map((campus) => [campus.id, campus]));
	// Org -> campus -> class: with a campus picked in the top bar, only that campus's classes show (and new ones land in it).
	const visibleClasses = selectedCampusId === null ? classes : classes.filter((schoolClass) => schoolClass.campusId === selectedCampusId);
	const activeCampusName = selectedCampusId === null ? null : (campusesById.get(selectedCampusId)?.name ?? null);
	const gradeLevelsById = new Map(gradeLevels.map((level) => [level.id, level]));

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setDialogError(null);
		setSubmitting(true);
		try {
			await createSchoolClass({
				academicYearId: Number(academicYearId),
				campusId: selectedCampusId ?? Number(campusId),
				gradeLevelId: Number(gradeLevelId),
				displayName: displayName || null,
			});
			setAcademicYearId("");
			setCampusId("");
			setGradeLevelId("");
			setDisplayName("");
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : "Failed to create class");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">{activeCampusName ? `Classes - ${activeCampusName}` : "Classes"}</Typography>
					<Button
						size="small"
						startIcon={<AddIcon />}
						onClick={() => {
							refreshLookups();
							setDialogError(null);
							setDialogOpen(true);
						}}
					>
						Add class
					</Button>
				</Box>

				{(academicYears.length === 0 || campuses.length === 0 || gradeLevels.length === 0) && (
					<Alert severity="info">
						Add at least one academic year (Settings), campus (Settings), and grade level (above) before creating a class.
					</Alert>
				)}

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Class</TableCell>
								<TableCell>Academic year</TableCell>
								{selectedCampusId === null && <TableCell>Campus</TableCell>}
								<TableCell>Status</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{visibleClasses.map((schoolClass) => (
								<TableRow
									key={schoolClass.id}
									hover
									sx={{ cursor: "pointer" }}
									onClick={() => navigate(`/academics/classes/${schoolClass.id}`)}
								>
									<TableCell>
										{schoolClass.displayName ?? gradeLevelsById.get(schoolClass.gradeLevelId)?.name ?? `Class #${schoolClass.id}`}
									</TableCell>
									<TableCell>{academicYearsById.get(schoolClass.academicYearId)?.name ?? "—"}</TableCell>
									{selectedCampusId === null && <TableCell>{campusesById.get(schoolClass.campusId)?.name ?? "—"}</TableCell>}
									<TableCell>
										<Chip label={schoolClass.status} size="small" />
									</TableCell>
									<TableCell onClick={(e) => e.stopPropagation()}>
										<Button size="small" onClick={() => openEdit(schoolClass)}>
											Edit
										</Button>
										<Button size="small" onClick={() => handleToggleStatus(schoolClass)}>
											{schoolClass.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
										</Button>
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>{activeCampusName ? `Add class to ${activeCampusName}` : "Add class"}</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{dialogError && <DialogErrorAlert>{dialogError}</DialogErrorAlert>}
						<TextField select label="Academic year" value={academicYearId} onChange={(e) => setAcademicYearId(e.target.value)} required fullWidth>
							{academicYears.map((year) => (
								<MenuItem key={year.id} value={year.id}>
									{year.name}
								</MenuItem>
							))}
						</TextField>
						{selectedCampusId === null && (
							<TextField select label="Campus" value={campusId} onChange={(e) => setCampusId(e.target.value)} required fullWidth>
								{campuses.map((campus) => (
									<MenuItem key={campus.id} value={campus.id}>
										{campus.name}
									</MenuItem>
								))}
							</TextField>
						)}
						<TextField select label="Grade level" value={gradeLevelId} onChange={(e) => setGradeLevelId(e.target.value)} required fullWidth>
							{gradeLevels.map((level) => (
								<MenuItem key={level.id} value={level.id}>
									{level.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Display name"
							placeholder="Defaults to the grade level's name"
							value={displayName}
							onChange={(e) => setDisplayName(e.target.value)}
							fullWidth
						/>
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
				<DialogTitle>Edit class</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{dialogError && <DialogErrorAlert>{dialogError}</DialogErrorAlert>}
						<TextField
							label="Display name"
							placeholder="Defaults to the grade level's name"
							value={editDisplayName}
							onChange={(e) => setEditDisplayName(e.target.value)}
							autoFocus
							fullWidth
						/>
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
