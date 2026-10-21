import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Accordion from "@mui/material/Accordion";
import AccordionDetails from "@mui/material/AccordionDetails";
import AccordionSummary from "@mui/material/AccordionSummary";
import Alert from "@mui/material/Alert";
import Autocomplete from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
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
import {
	applySchoolTemplate,
	getSchoolTemplatePreview,
	getStarterPackNeeds,
	type SchoolTemplateApplyResponse,
	type SchoolTemplatePreviewResponse,
	type StarterPackAnswers,
	type StarterPackNeeds,
} from "../../api/schoolTemplate";
import { useAuth } from "../../auth/AuthContext";
import { StarterPackDialog } from "./StarterPackDialog";
import {
	createSchoolTemplateItem,
	deleteSchoolTemplateItem,
	listSchoolTemplateItems,
	setSchoolTemplateCategoryIncluded,
	setSchoolTemplateItemIncluded,
	updateSchoolTemplateItem,
	type SaveSchoolTemplateItemRequest,
	type SchoolTemplateItemCategory,
	type SchoolTemplateItemResponse,
} from "../../api/schoolTemplateItems";

interface CategoryConfig {
	key: SchoolTemplateItemCategory;
	label: string;
	fields: Array<"code" | "description" | "sequenceOrder" | "stage" | "categoryType" | "minPercentage" | "remark" | "permissionCodes" | "unit" | "itemCategoryCode">;
	extraColumn?: (item: SchoolTemplateItemResponse) => string;
	/** Extra guidance shown above the rows. */
	hint?: string;
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
		key: "ITEM",
		label: "Inventory items",
		fields: ["code", "description", "unit", "itemCategoryCode"],
		extraColumn: (item) => [item.itemCategoryCode, item.unit].filter(Boolean).join(" · ") || "—",
	},
	{
		key: "ASSET_TYPE",
		label: "Asset types",
		fields: ["code", "description", "unit", "itemCategoryCode"],
		extraColumn: (item) => [item.itemCategoryCode, item.unit].filter(Boolean).join(" · ") || "—",
	},
	{
		key: "CLASS",
		label: "Classes",
		fields: [],
		hint: "A class is only created if the grade of the same name is also ticked under Grades.",
	},
	{ key: "SECTION", label: "Sections", fields: [] },
	{ key: "SUBJECT", label: "Subjects", fields: ["code"], extraColumn: (item) => item.code ?? "—" },
	{ key: "LEAVE_TYPE", label: "Leave types", fields: ["code"], extraColumn: (item) => item.code ?? "—" },
	{
		key: "GRADING_BAND",
		label: "Grading scale bands",
		fields: ["sequenceOrder", "minPercentage", "remark"],
		extraColumn: (item) => (item.minPercentage != null ? `${item.minPercentage}%+` : "—"),
	},
	{
		key: "CERTIFICATE_TYPE",
		label: "Certificate types",
		fields: ["code", "description"],
		extraColumn: (item) => item.code ?? "—",
		hint: "A row whose code matches a preset (TC, BONAFIDE, CHARACTER, LEAVING) starts with that preset's wording and rules; any other code starts with generic wording. Edit wording later under Settings > Certificate types.",
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
	unit: "PCS",
	itemCategoryCode: "",
};

function TemplateCategorySection({ config, onChanged }: { config: CategoryConfig; onChanged?: () => void }) {
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

	const includedCount = items.filter((item) => item.included).length;

	async function handleToggleIncluded(item: SchoolTemplateItemResponse) {
		try {
			const updated = await setSchoolTemplateItemIncluded(item.id, !item.included);
			setItems((previous) => previous.map((row) => (row.id === updated.id ? updated : row)));
			onChanged?.();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update item");
		}
	}

	async function handleToggleAll(included: boolean) {
		try {
			await setSchoolTemplateCategoryIncluded(config.key, included);
			refresh();
			onChanged?.();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update items");
		}
	}

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
			unit: item.unit ?? "PCS",
			itemCategoryCode: item.itemCategoryCode ?? "",
		});
		setDialogOpen(true);
	}

	async function handleDelete(item: SchoolTemplateItemResponse) {
		try {
			await deleteSchoolTemplateItem(item.id);
			refresh();
			onChanged?.();
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
				unit: config.fields.includes("unit") ? form.unit || null : null,
				itemCategoryCode: config.fields.includes("itemCategoryCode") ? form.itemCategoryCode || null : null,
			};
			if (editingId != null) {
				await updateSchoolTemplateItem(editingId, request);
			} else {
				await createSchoolTemplateItem(config.key, request);
			}
			setDialogOpen(false);
			refresh();
			onChanged?.();
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
					<Chip size="small" label={includedCount === items.length ? items.length : `${includedCount} of ${items.length} included`} />
				</Stack>
			</AccordionSummary>
			<AccordionDetails>
				<Stack spacing={1.5}>
					{error && <Alert severity="error">{error}</Alert>}
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1 }}>
						<Typography variant="caption" color="text.secondary">
							Tick what your school needs - unticked rows are kept here but skipped when you apply.{config.hint ? ` ${config.hint}` : ""}
						</Typography>
						<Button size="small" startIcon={<AddIcon />} onClick={openCreate}>
							Add
						</Button>
					</Box>
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell padding="checkbox">
										<Checkbox
											size="small"
											checked={items.length > 0 && includedCount === items.length}
											indeterminate={includedCount > 0 && includedCount < items.length}
											disabled={items.length === 0}
											onChange={(e) => handleToggleAll(e.target.checked)}
											slotProps={{ input: { "aria-label": `Include all ${config.label.toLowerCase()}` } }}
										/>
									</TableCell>
									<TableCell>Name</TableCell>
									{config.extraColumn && <TableCell />}
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{items.map((item) => (
									<TableRow key={item.id} sx={item.included ? undefined : { opacity: 0.55 }}>
										<TableCell padding="checkbox">
											<Checkbox
												size="small"
												checked={item.included}
												onChange={() => handleToggleIncluded(item)}
												slotProps={{ input: { "aria-label": `${item.included ? "Exclude" : "Include"} ${item.name}` } }}
											/>
										</TableCell>
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
										<TableCell colSpan={config.extraColumn ? 4 : 3}>
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
						{config.fields.includes("itemCategoryCode") && (
							<TextField
								label="Inventory category code"
								value={form.itemCategoryCode}
								onChange={(e) => setForm({ ...form, itemCategoryCode: e.target.value })}
								helperText="Code of an inventory category, e.g. STATIONERY. Skipped on apply if your school has no such category."
								required
								fullWidth
							/>
						)}
						{config.fields.includes("unit") && (
							<TextField
								label="Unit"
								value={form.unit}
								onChange={(e) => setForm({ ...form, unit: e.target.value })}
								helperText="e.g. PCS, BOX, LTR"
								fullWidth
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
	const { refreshModules } = useAuth();
	const [needs, setNeeds] = useState<StarterPackNeeds | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);

	useEffect(() => {
		getStarterPackNeeds()
			.then(setNeeds)
			.catch(() => setNeeds(null));
	}, []);

	function refreshPreview() {
		getSchoolTemplatePreview()
			.then(setPreview)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load school template status"));
	}

	useEffect(refreshPreview, []);

	async function handleApply(answers?: StarterPackAnswers) {
		setApplying(true);
		setError(null);
		try {
			const result = await applySchoolTemplate(answers);
			setApplyResult(result);
			if (answers) {
				setDialogOpen(false);
				setNeeds((previous) => (previous ? { ...previous, canSelectModules: false } : previous));
				await refreshModules();
			}
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
			applyResult.itemsCreated.length +
			applyResult.assetTypesCreated.length +
			applyResult.leaveTypesCreated.length +
			applyResult.classesCreated.length +
			applyResult.sectionsCreated.length +
			applyResult.subjectsCreated.length +
			applyResult.certificateTypesCreated.length +
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
							categories, items, asset types, leave types, subjects, and grading scale bands (a class is created for each ticked grade that also has a ticked class, with every ticked section added to each class) - add, edit, or remove anything below, then apply to create the
							real records. Nothing is applied automatically.
						</Typography>
					</Box>
					<Can anyOf={["ORGANISATION_MANAGE"]}>
						{needs?.canSelectModules ? (
							<Button variant="contained" onClick={() => setDialogOpen(true)} disabled={applying}>
								Set up with starter pack
							</Button>
						) : (
							<Button variant="contained" onClick={() => handleApply()} disabled={applying}>
								Apply school template
							</Button>
						)}
					</Can>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{needs && (
					<StarterPackDialog
						open={dialogOpen}
						questions={needs.questions}
						applying={applying}
						onClose={() => setDialogOpen(false)}
						onApply={handleApply}
					/>
				)}

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
				{applyResult?.classesNote && <Alert severity="warning">{applyResult.classesNote}</Alert>}

				{preview && (
					<Alert severity="info" variant="outlined">
						{preview.gradingScaleExists
							? "A grading scale already exists for your organisation."
							: "No grading scale yet - applying the template will create one from the bands below."}
					</Alert>
				)}

				<Stack spacing={0.5}>
					{CATEGORIES.map((config) => (
						<TemplateCategorySection key={config.key} config={config} onChanged={refreshPreview} />
					))}
				</Stack>
			</Stack>
		</Paper>
	);
}
