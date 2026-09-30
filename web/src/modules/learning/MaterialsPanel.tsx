import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
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
import { ApiError } from "../../api/client";
import {
	archiveMaterial,
	createMaterial,
	listMaterials,
	type LearningScope,
	type MaterialKind,
	type MaterialResponse,
} from "../../api/learning";
import { AttachmentField } from "./AttachmentField";
import { MaterialAction, MaterialSummary } from "./MaterialList";

/** Class/subject-scoped learning materials (#228) - notes, links and files. */
export function MaterialsPanel({ scope }: { scope: LearningScope }) {
	const [items, setItems] = useState<MaterialResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [adding, setAdding] = useState(false);

	// A class is writable when the caller may author for at least one subject in one of its sections.
	const writableClasses = Array.from(
		new Map(scope.sections.filter((s) => s.writableSubjectIds.length > 0).map((s) => [s.classId, s.className])).entries(),
	);

	function refresh() {
		listMaterials()
			.then(setItems)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load materials"));
	}

	useEffect(refresh, []);

	async function handleArchive(id: number) {
		try {
			await archiveMaterial(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to archive material");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Learning materials</Typography>
					{writableClasses.length > 0 && (
						<Button variant="contained" startIcon={<AddIcon />} onClick={() => setAdding(true)}>
							Add material
						</Button>
					)}
				</Stack>
				{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
				{items.length === 0 && <Alert severity="info">No materials yet.</Alert>}
				{items.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Material</TableCell>
									<TableCell>Class</TableCell>
									<TableCell>Added by</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{items.map((m) => (
									<TableRow key={m.id} sx={{ opacity: m.status === "ARCHIVED" ? 0.5 : 1 }}>
										<TableCell>
											<MaterialSummary material={m} />
										</TableCell>
										<TableCell>{m.className}</TableCell>
										<TableCell>{m.uploadedByName}</TableCell>
										<TableCell align="right">
											<MaterialAction material={m} />
											{m.status === "ACTIVE" && (
												<Button size="small" color="error" onClick={() => handleArchive(m.id)}>
													Archive
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
			{adding && (
				<AddMaterialDialog
					scope={scope}
					classes={writableClasses}
					onClose={() => setAdding(false)}
					onAdded={() => {
						setAdding(false);
						refresh();
					}}
				/>
			)}
		</Paper>
	);
}

function AddMaterialDialog({ scope, classes, onClose, onAdded }: { scope: LearningScope; classes: [number, string][]; onClose: () => void; onAdded: () => void }) {
	const [classId, setClassId] = useState<number | "">(classes[0]?.[0] ?? "");
	const [subjectId, setSubjectId] = useState<number | "">("");
	const [kind, setKind] = useState<MaterialKind>("FILE");
	const [title, setTitle] = useState("");
	const [description, setDescription] = useState("");
	const [url, setUrl] = useState("");
	const [attachmentRef, setAttachmentRef] = useState<string | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);

	// Subjects the caller may author for in this class (union over the class's sections).
	const allowedSubjectIds = new Set(scope.sections.filter((s) => s.classId === classId).flatMap((s) => s.writableSubjectIds));
	const subjects = scope.subjects.filter((s) => allowedSubjectIds.has(s.id));

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		if (classId === "") {
			setError("Choose a class");
			return;
		}
		setSaving(true);
		try {
			await createMaterial({ classId, subjectId: subjectId === "" ? null : subjectId, title, description: description || null, kind, attachmentRef, url: url || null });
			onAdded();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add material");
			setSaving(false);
		}
	}

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={handleSubmit}>
			<DialogTitle>Add learning material</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField select required label="Class" value={classId} onChange={(e) => { setClassId(Number(e.target.value)); setSubjectId(""); }}>
						{classes.map(([id, name]) => (
							<MenuItem key={id} value={id}>{name}</MenuItem>
						))}
					</TextField>
					<TextField select label="Subject (optional)" value={subjectId} onChange={(e) => setSubjectId(e.target.value === "" ? "" : Number(e.target.value))}>
						<MenuItem value="">Whole class</MenuItem>
						{subjects.map((s) => (
							<MenuItem key={s.id} value={s.id}>{s.name}</MenuItem>
						))}
					</TextField>
					<TextField select label="Type" value={kind} onChange={(e) => setKind(e.target.value as MaterialKind)}>
						<MenuItem value="FILE">File</MenuItem>
						<MenuItem value="LINK">Link</MenuItem>
						<MenuItem value="NOTE">Note</MenuItem>
					</TextField>
					<TextField required label="Title" value={title} onChange={(e) => setTitle(e.target.value)} />
					<TextField label={kind === "NOTE" ? "Note" : "Description"} required={kind === "NOTE"} multiline minRows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
					{kind === "LINK" && <TextField required label="Link" placeholder="https://" value={url} onChange={(e) => setUrl(e.target.value)} />}
					{kind === "FILE" && <AttachmentField value={attachmentRef} onChange={setAttachmentRef} onError={setError} />}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" disabled={saving}>Add</Button>
			</DialogActions>
		</Dialog>
	);
}
