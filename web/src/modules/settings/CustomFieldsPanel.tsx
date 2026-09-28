import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
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
	changeCustomFieldDefinitionStatus,
	createCustomFieldDefinition,
	listCustomFieldDefinitions,
	type CustomFieldDataType,
	type CustomFieldDefinitionResponse,
} from "../../api/customFieldDefinitions";

/** The one entity type opted into custom fields so far (see Student.customFields'
 * class comment, #146) - entityType itself is a plain string on the backend so a
 * future vertical/entity can add its own without a schema change; this picker just
 * hasn't needed a second option yet. */
const ENTITY_TYPES = [{ value: "STUDENT", label: "Student" }];

const DATA_TYPES: { value: CustomFieldDataType; label: string }[] = [
	{ value: "TEXT", label: "Text" },
	{ value: "NUMBER", label: "Number" },
	{ value: "DATE", label: "Date" },
	{ value: "BOOLEAN", label: "Yes/No" },
	{ value: "SELECT", label: "Choice list" },
];

interface FormState {
	entityType: string;
	fieldKey: string;
	label: string;
	dataType: CustomFieldDataType;
	options: string;
	required: boolean;
}

const EMPTY_FORM: FormState = { entityType: "STUDENT", fieldKey: "", label: "", dataType: "TEXT", options: "", required: false };

/** Org-defined extra fields on a record (e.g. Student's "Blood group", "Transport
 * route") with no code change (#146) - this panel defines them; CustomFieldsFormSection
 * renders them on the opted-in entity's own create/edit form. */
export function CustomFieldsPanel() {
	const [definitions, setDefinitions] = useState<CustomFieldDefinitionResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState<FormState>(EMPTY_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listCustomFieldDefinitions()
			.then(setDefinitions)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load custom fields"));
	}

	useEffect(refresh, []);

	function set<K extends keyof FormState>(key: K) {
		return (event: React.ChangeEvent<HTMLInputElement>) => setForm((prev) => ({ ...prev, [key]: event.target.value }));
	}

	async function handleToggleStatus(definition: CustomFieldDefinitionResponse) {
		try {
			await changeCustomFieldDefinitionStatus(definition.id, definition.status === "ACTIVE" ? "ARCHIVED" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update field status");
		}
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createCustomFieldDefinition({
				entityType: form.entityType,
				fieldKey: form.fieldKey.trim(),
				label: form.label.trim(),
				dataType: form.dataType,
				options: form.dataType === "SELECT" ? form.options : null,
				required: form.required,
			});
			setForm(EMPTY_FORM);
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create custom field");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Custom fields</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
						Add custom field
					</Button>
				</Box>
				<Typography variant="body2" color="text.secondary">
					Add extra fields your organisation needs (e.g. blood group, transport route) to a record's create/edit
					form, without waiting on a code change.
				</Typography>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Applies to</TableCell>
								<TableCell>Label</TableCell>
								<TableCell>Field key</TableCell>
								<TableCell>Type</TableCell>
								<TableCell>Required</TableCell>
								<TableCell>Status</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{definitions.map((definition) => (
								<TableRow key={definition.id}>
									<TableCell>{ENTITY_TYPES.find((t) => t.value === definition.entityType)?.label ?? definition.entityType}</TableCell>
									<TableCell>{definition.label}</TableCell>
									<TableCell>
										<code>{definition.fieldKey}</code>
									</TableCell>
									<TableCell>{DATA_TYPES.find((t) => t.value === definition.dataType)?.label ?? definition.dataType}</TableCell>
									<TableCell>{definition.required ? "Yes" : "No"}</TableCell>
									<TableCell>
										<Chip label={definition.status} size="small" color={definition.status === "ACTIVE" ? "success" : "default"} />
									</TableCell>
									<TableCell>
										<Button size="small" onClick={() => handleToggleStatus(definition)}>
											{definition.status === "ACTIVE" ? "Archive" : "Reactivate"}
										</Button>
									</TableCell>
								</TableRow>
							))}
							{definitions.length === 0 && (
								<TableRow>
									<TableCell colSpan={7}>
										<Typography variant="body2" color="text.secondary">
											No custom fields defined yet.
										</Typography>
									</TableCell>
								</TableRow>
							)}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Add custom field</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Applies to" value={form.entityType} onChange={set("entityType")} fullWidth>
							{ENTITY_TYPES.map((type) => (
								<MenuItem key={type.value} value={type.value}>
									{type.label}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Label" placeholder="Blood group" value={form.label} onChange={set("label")} required autoFocus fullWidth />
						<TextField
							label="Field key"
							placeholder="blood_group"
							helperText="Stable internal identifier - not shown to users, avoid changing later"
							value={form.fieldKey}
							onChange={set("fieldKey")}
							required
							fullWidth
						/>
						<TextField select label="Type" value={form.dataType} onChange={set("dataType")} fullWidth>
							{DATA_TYPES.map((type) => (
								<MenuItem key={type.value} value={type.value}>
									{type.label}
								</MenuItem>
							))}
						</TextField>
						{form.dataType === "SELECT" && (
							<TextField
								label="Choices"
								placeholder="Bus,Van,Walker"
								helperText="Comma-separated"
								value={form.options}
								onChange={set("options")}
								fullWidth
							/>
						)}
						<FormControlLabel
							control={
								<Switch checked={form.required} onChange={(e) => setForm((prev) => ({ ...prev, required: e.target.checked }))} />
							}
							label="Required"
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !form.label.trim() || !form.fieldKey.trim()}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
