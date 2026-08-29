import { useEffect, useMemo, useRef, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import IconButton from "@mui/material/IconButton";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import AddPhotoAlternateIcon from "@mui/icons-material/AddPhotoAlternate";
import DeleteIcon from "@mui/icons-material/Delete";
import HorizontalRuleIcon from "@mui/icons-material/HorizontalRule";
import QrCode2Icon from "@mui/icons-material/QrCode2";
import TextFieldsIcon from "@mui/icons-material/TextFields";
import ViewHeadlineIcon from "@mui/icons-material/ViewHeadline";
import ViewColumnIcon from "@mui/icons-material/ViewColumn";
import { ApiError } from "../../api/client";
import {
	createIdCardTemplate,
	listIdCardTemplates,
	renderIdCardTemplate,
	type IdCardElement,
	type IdCardElementType,
	type IdCardTemplateResponse,
	type RenderedIdCardElement,
} from "../../api/idCardTemplates";
import { listStudents, type StudentResponse } from "../../api/students";
import { Can } from "../../auth/Can";
import { useAuth } from "../../auth/AuthContext";
import { colors } from "../../theme";

const MM_TO_PX = 4;
const DEFAULT_WIDTH_MM = 85.6;
const DEFAULT_HEIGHT_MM = 54;

const BINDABLE_FIELDS = [
	{ value: "fullName", label: "Full name" },
	{ value: "admissionNumber", label: "Admission number" },
	{ value: "className", label: "Class" },
	{ value: "section", label: "Section" },
	{ value: "bloodGroup", label: "Blood group" },
	{ value: "dateOfBirth", label: "Date of birth" },
];

const PALETTE: { type: IdCardElementType; label: string; icon: React.ReactNode }[] = [
	{ type: "TEXT", label: "Text box", icon: <TextFieldsIcon fontSize="small" /> },
	{ type: "DATA_FIELD", label: "Data field", icon: <ViewColumnIcon fontSize="small" /> },
	{ type: "PHOTO", label: "Photo box", icon: <AddPhotoAlternateIcon fontSize="small" /> },
	{ type: "QR_CODE", label: "QR code", icon: <QrCode2Icon fontSize="small" /> },
	{ type: "HEADER_BAND", label: "Header band", icon: <ViewHeadlineIcon fontSize="small" /> },
	{ type: "DIVIDER", label: "Divider", icon: <HorizontalRuleIcon fontSize="small" /> },
];

function newElement(type: IdCardElementType): IdCardElement {
	const id = `el-${Date.now()}-${Math.floor(Math.random() * 10000)}`;
	switch (type) {
		case "TEXT":
			return { id, type, x: 4, y: 4, width: 40, height: 8, text: "Label" };
		case "DATA_FIELD":
			return { id, type, x: 4, y: 14, width: 50, height: 8, field: "fullName" };
		case "PHOTO":
			return { id, type, x: 60, y: 4, width: 20, height: 24 };
		case "QR_CODE":
			return { id, type, x: 60, y: 30, width: 18, height: 18, field: "admissionNumber" };
		case "HEADER_BAND":
			return { id, type, x: 0, y: 0, width: DEFAULT_WIDTH_MM, height: 6 };
		case "DIVIDER":
			return { id, type, x: 2, y: 24, width: DEFAULT_WIDTH_MM - 4, height: 0.5 };
	}
}

/** ID Card Studio canvas (#34) - a fixed-slot drag-and-position designer rather than a
 * full drag-drop framework, per #34's own note to consider a simpler implementation over
 * a full DnD library for a first version. Elements are moved by dragging on the canvas;
 * size and field bindings are set numerically in the properties panel. */
export function IdCardStudioPanel() {
	const { hasAnyPermission } = useAuth();
	const canEdit = hasAnyPermission(["ORGANISATION_MANAGE"]);

	const [templates, setTemplates] = useState<IdCardTemplateResponse[]>([]);
	const [loadedTemplateId, setLoadedTemplateId] = useState<number | null>(null);
	const [name, setName] = useState("New ID card");
	const [widthMm, setWidthMm] = useState(DEFAULT_WIDTH_MM);
	const [heightMm, setHeightMm] = useState(DEFAULT_HEIGHT_MM);
	const [elements, setElements] = useState<IdCardElement[]>([]);
	const [selectedId, setSelectedId] = useState<string | null>(null);

	const [students, setStudents] = useState<StudentResponse[]>([]);
	const [previewStudentId, setPreviewStudentId] = useState<number | "">("");
	const [previewElements, setPreviewElements] = useState<RenderedIdCardElement[] | null>(null);

	const [saving, setSaving] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);

	const canvasRef = useRef<HTMLDivElement | null>(null);
	const dragState = useRef<{ id: string; startClientX: number; startClientY: number; origX: number; origY: number } | null>(null);

	useEffect(() => {
		listIdCardTemplates().then(setTemplates).catch(() => undefined);
		listStudents().then(setStudents).catch(() => undefined);
	}, []);

	const selectedElement = useMemo(() => elements.find((el) => el.id === selectedId) ?? null, [elements, selectedId]);

	function addElement(type: IdCardElementType) {
		const el = newElement(type);
		setElements((prev) => [...prev, el]);
		setSelectedId(el.id);
	}

	function updateElement(id: string, patch: Partial<IdCardElement>) {
		setElements((prev) => prev.map((el) => (el.id === id ? { ...el, ...patch } : el)));
	}

	function removeElement(id: string) {
		setElements((prev) => prev.filter((el) => el.id !== id));
		if (selectedId === id) setSelectedId(null);
	}

	function loadTemplate(template: IdCardTemplateResponse) {
		try {
			const layout = JSON.parse(template.layoutJson) as { elements: IdCardElement[] };
			setLoadedTemplateId(template.id);
			setName(template.name);
			setWidthMm(template.widthMm);
			setHeightMm(template.heightMm);
			setElements(layout.elements ?? []);
			setSelectedId(null);
			setPreviewElements(null);
		} catch {
			setError("Could not parse this template's layout");
		}
	}

	function startNewTemplate() {
		setLoadedTemplateId(null);
		setName("New ID card");
		setWidthMm(DEFAULT_WIDTH_MM);
		setHeightMm(DEFAULT_HEIGHT_MM);
		setElements([]);
		setSelectedId(null);
		setPreviewElements(null);
	}

	function handlePointerDown(event: React.PointerEvent, el: IdCardElement) {
		if (!canEdit) return;
		event.stopPropagation();
		(event.target as HTMLElement).setPointerCapture(event.pointerId);
		setSelectedId(el.id);
		dragState.current = { id: el.id, startClientX: event.clientX, startClientY: event.clientY, origX: el.x, origY: el.y };
	}

	function handlePointerMove(event: React.PointerEvent) {
		const drag = dragState.current;
		if (!drag) return;
		const dxMm = (event.clientX - drag.startClientX) / MM_TO_PX;
		const dyMm = (event.clientY - drag.startClientY) / MM_TO_PX;
		updateElement(drag.id, {
			x: Math.max(0, Math.round((drag.origX + dxMm) * 10) / 10),
			y: Math.max(0, Math.round((drag.origY + dyMm) * 10) / 10),
		});
	}

	function handlePointerUp() {
		dragState.current = null;
	}

	async function handleSave() {
		setSaving(true);
		setSaved(false);
		setError(null);
		try {
			const layoutJson = JSON.stringify({ elements });
			const created = await createIdCardTemplate({ name, widthMm, heightMm, layoutJson });
			setTemplates((prev) => [...prev, created]);
			setLoadedTemplateId(created.id);
			setSaved(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save template");
		} finally {
			setSaving(false);
		}
	}

	async function handlePreview() {
		if (!loadedTemplateId || !previewStudentId) return;
		setError(null);
		try {
			const response = await renderIdCardTemplate(loadedTemplateId, previewStudentId);
			setPreviewElements(response.elements);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to render preview");
		}
	}

	function resolvedFor(id: string): RenderedIdCardElement | undefined {
		return previewElements?.find((el) => el.id === id);
	}

	return (
		<Stack spacing={2}>
			<Typography variant="body2" color="text.secondary">
				Design an ID card layout bound to live student data - text boxes, data fields, a photo box, a QR code, a header
				band, and a divider. Drag elements on the card to position them; use the panel on the right for sizing and field
				bindings.
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}
			{saved && <Alert severity="success">Template saved</Alert>}

			<Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
				<TextField
					select
					label="Load template"
					value={loadedTemplateId ?? ""}
					onChange={(e) => {
						const template = templates.find((t) => t.id === Number(e.target.value));
						if (template) loadTemplate(template);
					}}
					sx={{ minWidth: 220 }}
					size="small"
				>
					{templates.map((t) => (
						<MenuItem key={t.id} value={t.id}>
							{t.name}
						</MenuItem>
					))}
				</TextField>
				<Button size="small" onClick={startNewTemplate}>
					New template
				</Button>
			</Box>

			<Box sx={{ display: "flex", gap: 3, flexWrap: "wrap", alignItems: "flex-start" }}>
				{/* Palette */}
				<Paper sx={{ p: 2, width: 180 }}>
					<Typography variant="subtitle2" sx={{ mb: 1 }}>
						Elements
					</Typography>
					<Stack spacing={1}>
						{PALETTE.map((item) => (
							<Button
								key={item.type}
								size="small"
								variant="outlined"
								startIcon={item.icon}
								disabled={!canEdit}
								onClick={() => addElement(item.type)}
								sx={{ justifyContent: "flex-start" }}
							>
								{item.label}
							</Button>
						))}
					</Stack>
				</Paper>

				{/* Canvas */}
				<Box>
					<Box
						ref={canvasRef}
						onPointerMove={handlePointerMove}
						onPointerUp={handlePointerUp}
						onClick={() => setSelectedId(null)}
						sx={{
							position: "relative",
							width: widthMm * MM_TO_PX,
							height: heightMm * MM_TO_PX,
							bgcolor: "background.paper",
							boxShadow: 3,
							border: `1px solid ${colors.rule}`,
							borderRadius: 1,
							overflow: "hidden",
						}}
					>
						{elements.map((el) => (
							<CardElementView
								key={el.id}
								element={el}
								selected={el.id === selectedId}
								resolved={resolvedFor(el.id)}
								onPointerDown={(e) => handlePointerDown(e, el)}
							/>
						))}
					</Box>
					<Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 1 }}>
						{widthMm} mm x {heightMm} mm
					</Typography>
				</Box>

				{/* Properties panel */}
				<Paper sx={{ p: 2, width: 280 }}>
					<Typography variant="subtitle2" sx={{ mb: 1 }}>
						Template
					</Typography>
					<Stack spacing={1.5} sx={{ mb: 2 }}>
						<TextField label="Name" size="small" value={name} onChange={(e) => setName(e.target.value)} disabled={!canEdit} fullWidth />
						<Box sx={{ display: "flex", gap: 1 }}>
							<TextField
								label="Width (mm)"
								size="small"
								type="number"
								value={widthMm}
								onChange={(e) => setWidthMm(Number(e.target.value))}
								disabled={!canEdit}
								fullWidth
							/>
							<TextField
								label="Height (mm)"
								size="small"
								type="number"
								value={heightMm}
								onChange={(e) => setHeightMm(Number(e.target.value))}
								disabled={!canEdit}
								fullWidth
							/>
						</Box>
						<Can anyOf={["ORGANISATION_MANAGE"]}>
							<Button variant="contained" size="small" disabled={saving} onClick={handleSave}>
								{saving ? "Saving..." : "Save template"}
							</Button>
						</Can>
					</Stack>

					<Divider sx={{ mb: 2 }} />

					<Typography variant="subtitle2" sx={{ mb: 1 }}>
						Selected element
					</Typography>
					{!selectedElement && (
						<Typography variant="body2" color="text.secondary">
							Click an element on the card to edit it.
						</Typography>
					)}
					{selectedElement && (
						<Stack spacing={1.5}>
							<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
								<Chip size="small" label={selectedElement.type.replace("_", " ")} />
								<IconButton size="small" onClick={() => removeElement(selectedElement.id)} disabled={!canEdit}>
									<DeleteIcon fontSize="small" />
								</IconButton>
							</Box>
							<Box sx={{ display: "flex", gap: 1 }}>
								<TextField
									label="X (mm)"
									size="small"
									type="number"
									value={selectedElement.x}
									onChange={(e) => updateElement(selectedElement.id, { x: Number(e.target.value) })}
									disabled={!canEdit}
									fullWidth
								/>
								<TextField
									label="Y (mm)"
									size="small"
									type="number"
									value={selectedElement.y}
									onChange={(e) => updateElement(selectedElement.id, { y: Number(e.target.value) })}
									disabled={!canEdit}
									fullWidth
								/>
							</Box>
							<Box sx={{ display: "flex", gap: 1 }}>
								<TextField
									label="Width (mm)"
									size="small"
									type="number"
									value={selectedElement.width}
									onChange={(e) => updateElement(selectedElement.id, { width: Number(e.target.value) })}
									disabled={!canEdit}
									fullWidth
								/>
								<TextField
									label="Height (mm)"
									size="small"
									type="number"
									value={selectedElement.height}
									onChange={(e) => updateElement(selectedElement.id, { height: Number(e.target.value) })}
									disabled={!canEdit}
									fullWidth
								/>
							</Box>
							{selectedElement.type === "TEXT" && (
								<TextField
									label="Text"
									size="small"
									value={selectedElement.text ?? ""}
									onChange={(e) => updateElement(selectedElement.id, { text: e.target.value })}
									disabled={!canEdit}
									fullWidth
								/>
							)}
							{(selectedElement.type === "DATA_FIELD" || selectedElement.type === "QR_CODE") && (
								<TextField
									select
									label="Bound field"
									size="small"
									value={selectedElement.field ?? ""}
									onChange={(e) => updateElement(selectedElement.id, { field: e.target.value })}
									disabled={!canEdit}
									fullWidth
								>
									{BINDABLE_FIELDS.map((f) => (
										<MenuItem key={f.value} value={f.value}>
											{f.label}
										</MenuItem>
									))}
								</TextField>
							)}
						</Stack>
					)}

					<Divider sx={{ my: 2 }} />

					<Typography variant="subtitle2" sx={{ mb: 1 }}>
						Preview with student
					</Typography>
					<Stack spacing={1.5}>
						<TextField
							select
							label="Student"
							size="small"
							value={previewStudentId}
							onChange={(e) => setPreviewStudentId(e.target.value === "" ? "" : Number(e.target.value))}
							fullWidth
						>
							{students.map((s) => (
								<MenuItem key={s.id} value={s.id}>
									{s.admissionNumber}
								</MenuItem>
							))}
						</TextField>
						<Tooltip title={loadedTemplateId ? "" : "Save the template first"}>
							<span>
								<Button
									size="small"
									variant="outlined"
									disabled={!loadedTemplateId || !previewStudentId}
									onClick={handlePreview}
									fullWidth
								>
									Preview
								</Button>
							</span>
						</Tooltip>
						{previewElements && (
							<Button size="small" onClick={() => setPreviewElements(null)}>
								Clear preview
							</Button>
						)}
					</Stack>
				</Paper>
			</Box>
		</Stack>
	);
}

function CardElementView({
	element,
	selected,
	resolved,
	onPointerDown,
}: {
	element: IdCardElement;
	selected: boolean;
	resolved: RenderedIdCardElement | undefined;
	onPointerDown: (event: React.PointerEvent) => void;
}) {
	const base = {
		position: "absolute" as const,
		left: element.x * MM_TO_PX,
		top: element.y * MM_TO_PX,
		width: element.width * MM_TO_PX,
		height: element.height * MM_TO_PX,
		outline: selected ? `2px solid ${colors.accent}` : `1px dashed ${colors.inkFaint}`,
		cursor: "move",
		userSelect: "none" as const,
		display: "flex",
		alignItems: "center",
		justifyContent: element.type === "TEXT" || element.type === "DATA_FIELD" ? "flex-start" : "center",
		overflow: "hidden",
		fontSize: "9px",
		px: 0.5,
	};

	switch (element.type) {
		case "TEXT":
			return (
				<Box onPointerDown={onPointerDown} sx={base}>
					{element.text}
				</Box>
			);
		case "DATA_FIELD":
			return (
				<Box onPointerDown={onPointerDown} sx={{ ...base, color: resolved ? "text.primary" : "text.secondary" }}>
					{resolved ? resolved.value : `{{${element.field}}}`}
				</Box>
			);
		case "PHOTO":
			return (
				<Box onPointerDown={onPointerDown} sx={{ ...base, bgcolor: colors.paperSunken }}>
					{resolved?.photoUrl ? (
						<Box component="img" src={resolved.photoUrl} alt="Student" sx={{ width: "100%", height: "100%", objectFit: "cover" }} />
					) : (
						<AddPhotoAlternateIcon sx={{ color: colors.inkFaint }} fontSize="small" />
					)}
				</Box>
			);
		case "QR_CODE":
			return (
				<Box onPointerDown={onPointerDown} sx={{ ...base, bgcolor: colors.ink, flexDirection: "column", gap: 0.25 }}>
					<QrCode2Icon sx={{ color: colors.paper }} fontSize="small" />
					{resolved && (
						<Typography sx={{ fontSize: "7px", color: colors.paper }} noWrap>
							{resolved.value}
						</Typography>
					)}
				</Box>
			);
		case "HEADER_BAND":
			return <Box onPointerDown={onPointerDown} sx={{ ...base, bgcolor: colors.accentSoft }} />;
		case "DIVIDER":
			return <Box onPointerDown={onPointerDown} sx={{ ...base, bgcolor: colors.rule, outline: selected ? base.outline : "none" }} />;
	}
}
