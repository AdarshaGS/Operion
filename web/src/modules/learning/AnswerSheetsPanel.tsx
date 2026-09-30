import { useEffect, useState, type ChangeEvent } from "react";
import Alert from "@mui/material/Alert";
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
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import { uploadAsset } from "../../api/assets";
import { ApiError } from "../../api/client";
import {
	getAnswerSheet,
	getAnswerSheetRoster,
	listAnswerSheetSchedules,
	releaseAnswerSheet,
	saveAnswerSheetMarking,
	uploadAnswerSheet,
	type AnnotationKind,
	type AnswerSheet,
	type AnswerSheetRosterRow,
	type AnswerSheetSchedule,
	type SheetAnnotation,
} from "../../api/learning";
import { AnnotatedPage } from "./AnnotatedPage";

const STATUS_COLOR = { NOT_UPLOADED: "default", UPLOADED: "warning", MARKED: "info", RELEASED: "success" } as const;

/** Scanned answer sheets: upload per student, mark on-screen, release to the guardian portal (#230). */
export function AnswerSheetsPanel() {
	const [schedules, setSchedules] = useState<AnswerSheetSchedule[] | null>(null);
	const [scheduleId, setScheduleId] = useState<number | "">("");
	const [roster, setRoster] = useState<AnswerSheetRosterRow[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [busy, setBusy] = useState(false);
	const [marking, setMarking] = useState<number | null>(null);
	const schedule = schedules?.find((s) => s.id === scheduleId);

	useEffect(() => {
		listAnswerSheetSchedules()
			.then(setSchedules)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load exam schedules"));
	}, []);

	function refreshRoster(id: number) {
		getAnswerSheetRoster(id)
			.then(setRoster)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the roster"));
	}

	useEffect(() => {
		if (scheduleId === "") {
			setRoster([]);
		} else {
			refreshRoster(scheduleId);
		}
	}, [scheduleId]);

	async function handleFiles(row: AnswerSheetRosterRow, event: ChangeEvent<HTMLInputElement>) {
		const files = Array.from(event.target.files ?? []);
		event.target.value = "";
		if (files.length === 0 || scheduleId === "") {
			return;
		}
		setBusy(true);
		setError(null);
		try {
			const refs: string[] = [];
			for (const file of files) {
				refs.push((await uploadAsset(file)).reference);
			}
			await uploadAnswerSheet(scheduleId, row.enrollmentId, refs);
			refreshRoster(scheduleId);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Upload failed");
		} finally {
			setBusy(false);
		}
	}

	async function handleRelease(row: AnswerSheetRosterRow) {
		if (row.sheetId == null || scheduleId === "") {
			return;
		}
		try {
			await releaseAnswerSheet(row.sheetId);
			refreshRoster(scheduleId);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to release the sheet");
		}
	}

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					{schedules?.length === 0 && <Alert severity="info">No exam schedules are available for you to mark.</Alert>}
					<TextField select size="small" label="Exam schedule" value={scheduleId} onChange={(e) => setScheduleId(Number(e.target.value))} sx={{ maxWidth: 420 }}>
						{schedules?.map((s) => (
							<MenuItem key={s.id} value={s.id}>
								{s.examName} · {s.className}{s.sectionName ? ` – ${s.sectionName}` : ""} · {s.subjectName} · {s.examDate}
							</MenuItem>
						))}
					</TextField>
					{schedule && <Typography variant="body2" color="text.secondary">Maximum marks: {schedule.maxMarks}. Totals stay on the answer sheet; enter them in the marks register separately.</Typography>}
					{roster.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Roll</TableCell>
										<TableCell>Student</TableCell>
										<TableCell>Sheet</TableCell>
										<TableCell>Marks</TableCell>
										<TableCell />
									</TableRow>
								</TableHead>
								<TableBody>
									{roster.map((row) => (
										<TableRow key={row.enrollmentId}>
											<TableCell>{row.rollNumber ?? "—"}</TableCell>
											<TableCell>{row.studentName}</TableCell>
											<TableCell><Chip size="small" color={STATUS_COLOR[row.status]} label={row.status === "NOT_UPLOADED" ? "Not uploaded" : `${row.status.toLowerCase()} · ${row.pages}p`} /></TableCell>
											<TableCell>{row.totalMarks ?? "—"}</TableCell>
											<TableCell align="right">
												{(row.status === "NOT_UPLOADED" || row.status === "UPLOADED") && (
													<Button component="label" size="small" disabled={busy}>
														{row.status === "UPLOADED" ? "Replace scans" : "Upload scans"}
														<input hidden multiple type="file" accept="image/png,image/jpeg,application/pdf" onChange={(e) => handleFiles(row, e)} />
													</Button>
												)}
												{(row.status === "UPLOADED" || row.status === "MARKED") && <Button size="small" onClick={() => setMarking(row.sheetId)}>Mark</Button>}
												{row.status === "MARKED" && <Button size="small" variant="contained" onClick={() => handleRelease(row)}>Release</Button>}
												{row.status === "RELEASED" && <Button size="small" onClick={() => setMarking(row.sheetId)}>View</Button>}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>
			{marking != null && <MarkingDialog sheetId={marking} onClose={() => { setMarking(null); if (scheduleId !== "") refreshRoster(scheduleId); }} />}
		</Stack>
	);
}

function MarkingDialog({ sheetId, onClose }: { sheetId: number; onClose: () => void }) {
	const [sheet, setSheet] = useState<AnswerSheet | null>(null);
	const [annotations, setAnnotations] = useState<SheetAnnotation[]>([]);
	const [tool, setTool] = useState<AnnotationKind | "ERASE">("TICK");
	const [markValue, setMarkValue] = useState("1");
	const [commentText, setCommentText] = useState("");
	const [total, setTotal] = useState("");
	const [remarks, setRemarks] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		getAnswerSheet(sheetId)
			.then((s) => {
				setSheet(s);
				setAnnotations(s.annotations);
				setTotal(s.totalMarks == null ? "" : String(s.totalMarks));
				setRemarks(s.overallRemarks ?? "");
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the sheet"));
	}, [sheetId]);

	const locked = sheet?.status === "RELEASED";
	const summed = annotations.reduce((acc, a) => acc + (a.kind === "MARKS" ? (a.marks ?? 0) : 0), 0);

	function place(pageNumber: number, x: number, y: number) {
		if (locked || tool === "ERASE") {
			return;
		}
		if (tool === "MARKS" && !(Number(markValue) >= 0)) {
			setError("Enter the marks to award first");
			return;
		}
		if (tool === "COMMENT" && !commentText.trim()) {
			setError("Type the comment first");
			return;
		}
		setError(null);
		setAnnotations((prev) => [...prev, { pageNumber, kind: tool, x, y, label: tool === "COMMENT" ? commentText.trim() : null, marks: tool === "MARKS" ? Number(markValue) : null }]);
	}

	async function save() {
		setSaving(true);
		setError(null);
		try {
			const saved = await saveAnswerSheetMarking(sheetId, annotations, total.trim() === "" ? null : Number(total), remarks.trim() || null);
			setSheet(saved);
			onClose();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save the marking");
			setSaving(false);
		}
	}

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="lg">
			<DialogTitle>{sheet ? `${sheet.studentName} · ${sheet.subjectName} · ${sheet.examName}` : "Answer sheet"}</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
					{locked && <Alert severity="success">Released to the guardian - marking is locked.</Alert>}
					{!locked && (
						<Stack direction="row" spacing={2} sx={{ alignItems: "center", flexWrap: "wrap" }}>
							<ToggleButtonGroup size="small" exclusive value={tool} onChange={(_, next) => next && setTool(next)}>
								<ToggleButton value="TICK">✓ Tick</ToggleButton>
								<ToggleButton value="CROSS">✗ Cross</ToggleButton>
								<ToggleButton value="MARKS">Marks</ToggleButton>
								<ToggleButton value="COMMENT">Comment</ToggleButton>
								<ToggleButton value="ERASE">Erase</ToggleButton>
							</ToggleButtonGroup>
							{tool === "MARKS" && <TextField size="small" type="number" label="Marks to award" value={markValue} onChange={(e) => setMarkValue(e.target.value)} sx={{ width: 150 }} slotProps={{ htmlInput: { min: 0, step: 0.5 } }} />}
							{tool === "COMMENT" && <TextField size="small" label="Comment" value={commentText} onChange={(e) => setCommentText(e.target.value)} sx={{ width: 260 }} />}
							<Typography variant="caption" color="text.secondary">Click on the page to place a mark.</Typography>
						</Stack>
					)}
					{sheet?.pages.map((page) => (
						<Stack key={page.id} spacing={0.5}>
							<Typography variant="caption" color="text.secondary">Page {page.pageNumber}</Typography>
							<AnnotatedPage
								url={page.url}
								annotations={annotations.filter((a) => a.pageNumber === page.pageNumber)}
								onPlace={locked ? undefined : (x, y) => place(page.pageNumber, x, y)}
								onMarkerClick={locked || tool !== "ERASE" ? undefined : (i) => {
									const target = annotations.filter((a) => a.pageNumber === page.pageNumber)[i];
									setAnnotations((prev) => prev.filter((a) => a !== target));
								}}
							/>
						</Stack>
					))}
					<Stack direction="row" spacing={2}>
						<TextField
							size="small"
							type="number"
							label={`Total marks (max ${sheet?.maxMarks ?? "—"})`}
							value={total}
							onChange={(e) => setTotal(e.target.value)}
							disabled={locked}
							helperText={`Blank uses the sum of awarded marks (${summed})`}
							sx={{ width: 240 }}
						/>
						<TextField size="small" fullWidth label="Overall remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} disabled={locked} />
					</Stack>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>{locked ? "Close" : "Cancel"}</Button>
				{!locked && <Button variant="contained" disabled={saving || !sheet} onClick={save}>Save marking</Button>}
			</DialogActions>
		</Dialog>
	);
}
