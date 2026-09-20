import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Accordion from "@mui/material/Accordion";
import AccordionDetails from "@mui/material/AccordionDetails";
import AccordionSummary from "@mui/material/AccordionSummary";
import Alert from "@mui/material/Alert";
import Autocomplete from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import IconButton from "@mui/material/IconButton";
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
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import { listPermissions, type PermissionResponse } from "../../api/permissions";
import { applySchoolTemplate, getSchoolTemplatePreview, type SchoolTemplateApplyResponse, type SchoolTemplatePreviewResponse } from "../../api/schoolTemplate";
import {
	createSchoolTemplateItem,
	deleteSchoolTemplateItem,
	listSchoolTemplateItems,
	updateSchoolTemplateItem,
	type SaveSchoolTemplateItemRequest,
	type SchoolTemplateItemCategory,
	type SchoolTemplateItemResponse,
} from "../../api/schoolTemplateItems";

interface CategoryConfig {
	key: SchoolTemplateItemCategory;
	label: string;
	fields: Array<"code" | "description" | "sequenceOrder" | "stage" | "categoryType" | "minPercentage" | "remark" | "permissionCodes">;
	extraColumn?: (item: SchoolTemplateItemResponse) => string;
}

const CATEGORIES: CategoryConfig[] = [
	{ key: "GRADE", label: "Grades", fields: ["stage", "sequenceOrder"], extraColumn: (item) => item.stage ?? "—" },
	{ key: "DEPARTMENT", label: "Departments", fields: [] },
	{ key: "DESIGNATION", label: "Designations", fields: [] },
	{ key: "ROLE", label: "Roles", fields: ["description", "permissionCodes"], extraColumn: (item) => `${item.permissionCodes.length} permissions` },
	{
		key: "FEE_CATEGORY",
		label: "Fee categories",
		fields: ["code", "description", "categoryType"],
		extraColumn: (item) => item.code ?? "—",
	},
	{ key: "ITEM_CATEGORY", label: "Inventory categories", fields: ["code", "description"], extraColumn: (item) => item.code ?? "—" },
	{
		key: "GRADING_BAND",
		label: "Grading scale bands",
		fields: ["sequenceOrder", "minPercentage", "remark"],
		extraColumn: (item) => (item.minPercentage != null ? `${item.minPercentage}%+` : "—"),
	},
];

const EMPTY_FORM = {
	name: "",
	code: "",
	description: "",
	sequenceOrder: "",
	stage: "",
	categoryType: "GENERAL",
	minPercentage: "",
	remark: "",
	permissionCodes: [] as string[],
};

function TemplateCategorySection({ config }: { config: CategoryConfig }) {
	const [items, setItems] = useState<SchoolTemplateItemResponse[]>([]);
	const [permissions, setPermissions] = useState<PermissionResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [editingId, setEditingId] = useState<number | null>(null);
	const [form, setForm] = useState(EMPTY_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listSchoolTemplateItems(config.key)
			.then(setItems)
			.catch((err) => setError(err instanceof ApiError ? err.message : `Failed to load ${config.label.toLowerCase()}`));
	}

	useEffect(refresh, [config.key]);

	useEffect(() => {
		if (config.fields.includes("permissionCodes")) {
			listPermissions().then(setPermissions).catch(() => setPermissions([]));
		}
	}, [config.fields]);

	function openCreate() {
		setEditingId(null);
		setForm(EMPTY_FORM);
		setDialogOpen(true);
	}

	function openEdit(item: SchoolTemplateItemResponse) {
		setEditingId(item.id);
		setForm({
			name: item.name,
			code: item.code ?? "",
			description: item.description ?? "",
			sequenceOrder: item.sequenceOrder != null ? String(item.sequenceOrder) : "",
			stage: item.stage ?? "",
			categoryType: item.categoryType ?? "GENERAL",
			minPercentage: item.minPercentage != null ? String(item.minPercentage) : "",
			remark: item.remark ?? "",
			permissionCodes: item.permissionCodes,
		});
		setDialogOpen(true);
	}

	async function handleDelete(item: SchoolTemplateItemResponse) {
		try {
			await deleteSchoolTemplateItem(item.id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to remove item");
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			const request: SaveSchoolTemplateItemRequest = {
				name: form.name,
				code: config.fields.includes("code") ? form.code || null : null,
				description: config.fields.includes("description") ? form.description || null : null,
				sequenceOrder: config.fields.includes("sequenceOrder") && form.sequenceOrder !== "" ? Number(form.sequenceOrder) : null,
				stage: config.fields.includes("stage") ? form.stage || null : null,
				categoryType: config.fields.includes("categoryType") ? form.categoryType : null,
				minPercentage: config.fields.includes("minPercentage") && form.minPercentage !== "" ? Number(form.minPercentage) : null,
				remark: config.fields.includes("remark") ? form.remark || null : null,
				permissionCodes: config.fields.includes("permissionCodes") ? form.permissionCodes : undefined,
			};
			if (editingId != null) {
				await updateSchoolTemplateItem(editingId, request);
			} else {
				await createSchoolTemplateItem(config.key, request);
			}
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save item");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Accordion disableGutters>
			<AccordionSummary expandIcon={<ExpandMoreIcon />}>
				<Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
					<Typography variant="body2" sx={{ fontWeight: 600 }}>
						{config.label}
					</Typography>
					<Chip size="small" label={items.length} />
				</Stack>
			</AccordionSummary>
			<AccordionDetails>
				<Stack spacing={1.5}>
					{error && <Alert severity="error">{error}</Alert>}
					<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
						<Button size="small" startIcon={<AddIcon />} onClick={openCreate}>
							Add
						</Button>
					</Box>
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Name</TableCell>
									{config.extraColumn && <TableCell />}
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{items.map((item) => (
									<TableRow key={item.id}>
										<TableCell>{item.name}</TableCell>
										{config.extraColumn && <TableCell>{config.extraColumn(item)}</TableCell>}
										<TableCell align="right">
											<IconButton size="small" onClick={() => openEdit(item)} aria-label={`Edit ${item.name}`}>
												<EditIcon fontSize="small" />
											</IconButton>
											<IconButton size="small" onClick={() => handleDelete(item)} aria-label={`Remove ${item.name}`}>
												<DeleteIcon fontSize="small" />
											</IconButton>
										</TableCell>
									</TableRow>
								))}
								{items.length === 0 && (
									<TableRow>
										<TableCell colSpan={config.extraColumn ? 3 : 2}>
											<Typography variant="body2" color="text.secondary">
												Nothing here - add one, or leave it empty to skip this category entirely.
											</Typography>
										</TableCell>
									</TableRow>
								)}
							</TableBody>
						</Table>
					</TableContainer>
				</Stack>
			</AccordionDetails>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>
					{editingId != null ? "Edit" : "Add"} {config.label.toLowerCase().replace(/s$/, "")}
				</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField
							label="Name"
							value={form.name}
							onChange={(e) => setForm({ ...form, name: e.target.value })}
							required
							autoFocus
							fullWidth
						/>
						{config.fields.includes("code") && (
							<TextField label="Code" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} fullWidth />
						)}
						{config.fields.includes("description") && (
							<TextField
								label="Description"
								value={form.description}
								onChange={(e) => setForm({ ...form, description: e.target.value })}
								fullWidth
								multiline
							/>
						)}
						{config.fields.includes("stage") && (
							<TextField label="Stage (optional)" value={form.stage} onChange={(e) => setForm({ ...form, stage: e.target.value })} fullWidth />
						)}
						{config.fields.includes("sequenceOrder") && (
							<TextField
								label="Position"
								type="number"
								value={form.sequenceOrder}
								onChange={(e) => setForm({ ...form, sequenceOrder: e.target.value })}
								helperText="Controls display/apply order relative to other items in this category"
								fullWidth
							/>
						)}
						{config.fields.includes("categoryType") && (
							<TextField
								select
								label="Type"
								value={form.categoryType}
								onChange={(e) => setForm({ ...form, categoryType: e.target.value })}
								fullWidth
							>
								<MenuItem value="GENERAL">General</MenuItem>
								<MenuItem value="TRANSPORT">Transport</MenuItem>
							</TextField>
						)}
						{config.fields.includes("minPercentage") && (
							<TextField
								label="Minimum percentage"
								type="number"
								value={form.minPercentage}
								onChange={(e) => setForm({ ...form, minPercentage: e.target.value })}
								required
								fullWidth
							/>
						)}
						{config.fields.includes("remark") && (
							<TextField label="Remark" value={form.remark} onChange={(e) => setForm({ ...form, remark: e.target.value })} fullWidth />
						)}
						{config.fields.includes("permissionCodes") && (
							<Autocomplete
								multiple
								options={permissions}
								groupBy={(option) => option.module}
								getOptionLabel={(option) => `${option.code} — ${option.description}`}
								isOptionEqualToValue={(option, value) => option.code === value.code}
								value={permissions.filter((permission) => form.permissionCodes.includes(permission.code))}
								onChange={(_event, value) => setForm({ ...form, permissionCodes: value.map((v) => v.code) })}
								renderInput={(params) => <TextField {...params} label="Permissions" placeholder="Add permission" />}
							/>
						)}
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Accordion>
	);
}

/** Opt-in "School setup template" - never applied automatically at provisioning (see
 * SchoolTemplateService's Javadoc), so this panel is the only place it runs from. The
 * catalog below is per-organisation and fully editable (add/edit/remove any item in any
 * category) - nothing here is a fixed list baked into the code, so admins can tailor it
 * to their own school without asking for a code change. Skip-if-exists on the backend
 * means clicking Apply is always safe to repeat. */
export function SchoolTemplatePanel() {
	const navigate = useNavigate();
	const [preview, setPreview] = useState<SchoolTemplatePreviewResponse | null>(null);
	const [applyResult, setApplyResult] = useState<SchoolTemplateApplyResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [applying, setApplying] = useState(false);

	function refreshPreview() {
		getSchoolTemplatePreview()
			.then(setPreview)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load school template status"));
	}

	useEffect(refreshPreview, []);

	async function handleApply() {
		setApplying(true);
		setError(null);
		try {
			const result = await applySchoolTemplate();
			setApplyResult(result);
			refreshPreview();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to apply the school setup template");
		} finally {
			setApplying(false);
		}
	}

	const totalCreated = applyResult
		? applyResult.gradesCreated.length +
			applyResult.departmentsCreated.length +
			applyResult.designationsCreated.length +
			applyResult.rolesCreated.length +
			applyResult.feeCategoriesCreated.length +
			applyResult.itemCategoriesCreated.length +
			(applyResult.gradingScaleCreated ? 1 : 0)
		: 0;

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
					<Box>
						<Typography variant="h6">School setup template</Typography>
						<Typography variant="body2" color="text.secondary">
							Your own editable catalog of grades, departments, designations, roles, fee categories, inventory
							categories, and grading scale bands - add, edit, or remove anything below, then apply to create the
							real records. Nothing is applied automatically.
						</Typography>
					</Box>
					<Can anyOf={["ORGANISATION_MANAGE"]}>
						<Button variant="contained" onClick={handleApply} disabled={applying}>
							Apply school template
						</Button>
					</Can>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{applyResult && (
					<Alert severity="success" onClose={() => setApplyResult(null)}>
						{totalCreated === 0
							? "Everything was already in place - nothing new to add."
							: `Added ${totalCreated} new item${totalCreated === 1 ? "" : "s"}. Everything else already existed and was left as-is.`}{" "}
						<Button size="small" onClick={() => navigate("/setup/review")}>
							Review school setup
						</Button>
					</Alert>
				)}

				{preview && (
					<Alert severity="info" variant="outlined">
						{preview.gradingScaleExists
							? "A grading scale already exists for your organisation."
							: "No grading scale yet - applying the template will create one from the bands below."}
					</Alert>
				)}

				<Stack spacing={0.5}>
					{CATEGORIES.map((config) => (
						<TemplateCategorySection key={config.key} config={config} />
					))}
				</Stack>
			</Stack>
		</Paper>
	);
}
