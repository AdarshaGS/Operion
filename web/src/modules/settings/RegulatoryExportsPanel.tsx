import { useEffect, useState, type FormEvent } from "react";
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
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import DeleteOutlinedIcon from "@mui/icons-material/DeleteOutlined";
import { ApiError } from "../../api/client";
import {
	changeRegulatoryExportStatus,
	createRegulatoryExportSchema,
	downloadRegulatoryExport,
	listRegulatoryExportSchemas,
	listRegulatoryExportSourceFields,
	replaceRegulatoryExportFields,
	type RegulatoryEntityType,
	type RegulatoryExportSchemaResponse,
	type RegulatoryExportSourceFieldResponse,
} from "../../api/regulatoryExports";

const ENTITY_TYPES: { value: RegulatoryEntityType; label: string }[] = [
	{ value: "STUDENT", label: "Students" },
	{ value: "STAFF", label: "Staff" },
];

interface ColumnRow {
	columnHeader: string;
	sourceField: string;
	defaultValue: string;
}

function message(err: unknown, fallback: string): string {
	return err instanceof ApiError ? err.message : fallback;
}

/** Org-defined export layouts (#233): each schema maps Operion's own fields to the columns an
 * external return or template wants. Nothing is pre-built - a layout for any region or funder is
 * assembled here from the field catalog, so no country's format lives in the product itself. */
export function RegulatoryExportsPanel() {
	const [schemas, setSchemas] = useState<RegulatoryExportSchemaResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [createOpen, setCreateOpen] = useState(false);
	const [editing, setEditing] = useState<RegulatoryExportSchemaResponse | null>(null);

	function refresh() {
		listRegulatoryExportSchemas()
			.then(setSchemas)
			.catch((err) => setError(message(err, "Failed to load export schemas")));
	}

	useEffect(refresh, []);

	async function handleDownload(schema: RegulatoryExportSchemaResponse) {
		setError(null);
		try {
			await downloadRegulatoryExport(schema);
		} catch (err) {
			setError(message(err, "Failed to export"));
		}
	}

	async function handleToggleStatus(schema: RegulatoryExportSchemaResponse) {
		try {
			await changeRegulatoryExportStatus(schema.id, schema.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(message(err, "Failed to update status"));
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Regulatory export layouts</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
						New layout
					</Button>
				</Box>
				<Typography variant="body2" color="text.secondary">
					Define how student or staff records map onto an external reporting layout, then download it as CSV. Only
					students who are admitted or active, and staff who are active or on leave, are included.
				</Typography>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Code</TableCell>
								<TableCell>Name</TableCell>
								<TableCell>Records</TableCell>
								<TableCell>Columns</TableCell>
								<TableCell>Status</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{schemas.map((schema) => (
								<TableRow key={schema.id}>
									<TableCell>
										<code>{schema.code}</code>
									</TableCell>
									<TableCell>{schema.name}</TableCell>
									<TableCell>{ENTITY_TYPES.find((t) => t.value === schema.entityType)?.label}</TableCell>
									<TableCell>{schema.fields.length}</TableCell>
									<TableCell>
										<Chip label={schema.status} size="small" color={schema.status === "ACTIVE" ? "success" : "default"} />
									</TableCell>
									<TableCell align="right">
										<Button size="small" onClick={() => setEditing(schema)}>
											Columns
										</Button>
										<Button
											size="small"
											disabled={schema.status !== "ACTIVE" || schema.fields.length === 0}
											onClick={() => handleDownload(schema)}
										>
											Download CSV
										</Button>
										<Button size="small" onClick={() => handleToggleStatus(schema)}>
											{schema.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
										</Button>
									</TableCell>
								</TableRow>
							))}
							{schemas.length === 0 && (
								<TableRow>
									<TableCell colSpan={6}>
										<Typography variant="body2" color="text.secondary">
											No export layouts defined yet.
										</Typography>
									</TableCell>
								</TableRow>
							)}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			{createOpen && (
				<CreateSchemaDialog
					onClose={() => setCreateOpen(false)}
					onCreated={(schema) => {
						setCreateOpen(false);
						refresh();
						setEditing(schema);
					}}
				/>
			)}
			{editing && (
				<ColumnsDialog
					schema={editing}
					onClose={() => setEditing(null)}
					onSaved={() => {
						setEditing(null);
						refresh();
					}}
				/>
			)}
		</Paper>
	);
}

function CreateSchemaDialog({
	onClose,
	onCreated,
}: {
	onClose: () => void;
	onCreated: (schema: RegulatoryExportSchemaResponse) => void;
}) {
	const [code, setCode] = useState("");
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [entityType, setEntityType] = useState<RegulatoryEntityType>("STUDENT");
	const [error, setError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			onCreated(
				await createRegulatoryExportSchema({
					code: code.trim(),
					name: name.trim(),
					description: description.trim() || null,
					entityType,
				}),
			);
		} catch (err) {
			setError(message(err, "Failed to create layout"));
			setSubmitting(false);
		}
	}

	return (
		<Dialog open onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
			<DialogTitle>New export layout</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
					<TextField
						label="Code"
						helperText="Short stable identifier, used as the download file name - cannot be changed later"
						value={code}
						onChange={(e) => setCode(e.target.value)}
						required
						fullWidth
					/>
					<TextField select label="Records" value={entityType} onChange={(e) => setEntityType(e.target.value as RegulatoryEntityType)} fullWidth>
						{ENTITY_TYPES.map((type) => (
							<MenuItem key={type.value} value={type.value}>
								{type.label}
							</MenuItem>
						))}
					</TextField>
					<TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} multiline fullWidth />
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" disabled={submitting}>
					Create
				</Button>
			</DialogActions>
		</Dialog>
	);
}

function ColumnsDialog({
	schema,
	onClose,
	onSaved,
}: {
	schema: RegulatoryExportSchemaResponse;
	onClose: () => void;
	onSaved: () => void;
}) {
	const [sources, setSources] = useState<RegulatoryExportSourceFieldResponse[]>([]);
	const [rows, setRows] = useState<ColumnRow[]>(
		schema.fields.map((f) => ({ columnHeader: f.columnHeader, sourceField: f.sourceField, defaultValue: f.defaultValue ?? "" })),
	);
	const [error, setError] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		listRegulatoryExportSourceFields(schema.entityType)
			.then(setSources)
			.catch((err) => setError(message(err, "Failed to load the field catalog")));
	}, [schema.entityType]);

	function update(index: number, patch: Partial<ColumnRow>) {
		setRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
	}

	function move(index: number, delta: number) {
		setRows((prev) => {
			const next = [...prev];
			const target = index + delta;
			if (target < 0 || target >= next.length) return prev;
			[next[index], next[target]] = [next[target], next[index]];
			return next;
		});
	}

	async function handleSave() {
		setSaving(true);
		setError(null);
		try {
			await replaceRegulatoryExportFields(
				schema.id,
				rows.map((row) => ({
					columnHeader: row.columnHeader.trim(),
					sourceField: row.sourceField.trim(),
					defaultValue: row.defaultValue.trim() || null,
				})),
			);
			onSaved();
		} catch (err) {
			setError(message(err, "Failed to save columns"));
			setSaving(false);
		}
	}

	return (
		<Dialog open onClose={onClose} fullWidth maxWidth="md">
			<DialogTitle>Columns - {schema.name}</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<Typography variant="body2" color="text.secondary">
						Each row is one output column, in order. Pick a source field
						{schema.entityType === "STUDENT" ? ", or type custom:<field key> to read one of your custom fields" : ""}. The
						default value is written when the source is blank.
					</Typography>
					{rows.map((row, index) => (
						<Stack key={index} direction="row" spacing={1} sx={{ alignItems: "flex-start" }}>
							<TextField
								label="Column header"
								value={row.columnHeader}
								onChange={(e) => update(index, { columnHeader: e.target.value })}
								size="small"
								sx={{ flex: 1 }}
							/>
							<Autocomplete
								freeSolo
								options={sources.map((s) => s.key)}
								getOptionLabel={(key) => key}
								renderOption={(props, key) => (
									<li {...props} key={key}>
										{sources.find((s) => s.key === key)?.label ?? key} ({key})
									</li>
								)}
								value={row.sourceField}
								onInputChange={(_e, value) => update(index, { sourceField: value })}
								size="small"
								sx={{ flex: 1 }}
								renderInput={(params) => <TextField {...params} label="Source field" />}
							/>
							<TextField
								label="Default"
								value={row.defaultValue}
								onChange={(e) => update(index, { defaultValue: e.target.value })}
								size="small"
								sx={{ width: 140 }}
							/>
							<IconButton size="small" aria-label="Move up" disabled={index === 0} onClick={() => move(index, -1)}>
								<ArrowUpwardIcon fontSize="small" />
							</IconButton>
							<IconButton size="small" aria-label="Move down" disabled={index === rows.length - 1} onClick={() => move(index, 1)}>
								<ArrowDownwardIcon fontSize="small" />
							</IconButton>
							<IconButton size="small" aria-label="Remove column" onClick={() => setRows((prev) => prev.filter((_, i) => i !== index))}>
								<DeleteOutlinedIcon fontSize="small" />
							</IconButton>
						</Stack>
					))}
					<Box>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setRows((prev) => [...prev, { columnHeader: "", sourceField: "", defaultValue: "" }])}>
							Add column
						</Button>
					</Box>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button variant="contained" onClick={handleSave} disabled={saving || rows.length === 0}>
					Save columns
				</Button>
			</DialogActions>
		</Dialog>
	);
}
