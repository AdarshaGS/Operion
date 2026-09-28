import { useEffect, useState } from "react";
import Box from "@mui/material/Box";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grid from "@mui/material/Grid";
import MenuItem from "@mui/material/MenuItem";
import Switch from "@mui/material/Switch";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { listCustomFieldDefinitions, type CustomFieldDefinitionResponse } from "../api/customFieldDefinitions";

interface CustomFieldsDisplayProps {
	entityType: string;
	values: Record<string, unknown>;
}

/** Read-only counterpart to CustomFieldsFormSection, for a record's own detail/view
 * page - resolves each stored value's label from the same ACTIVE definitions, and
 * renders nothing when the org has defined no fields or none of them have a value. */
export function CustomFieldsDisplay({ entityType, values }: CustomFieldsDisplayProps) {
	const [definitions, setDefinitions] = useState<CustomFieldDefinitionResponse[]>([]);

	useEffect(() => {
		listCustomFieldDefinitions(entityType)
			.then((all) => setDefinitions(all.filter((definition) => definition.status === "ACTIVE")))
			.catch(() => {});
	}, [entityType]);

	const populated = definitions.filter((definition) => {
		const value = values[definition.fieldKey];
		return value !== null && value !== undefined && value !== "";
	});

	if (populated.length === 0) {
		return null;
	}

	return (
		<>
			{populated.map((definition) => (
				<Grid key={definition.id} size={4}>
					<Typography variant="caption" color="text.secondary" component="div">
						{definition.label}
					</Typography>
					<Typography variant="body2">
						{definition.dataType === "BOOLEAN" ? (values[definition.fieldKey] ? "Yes" : "No") : String(values[definition.fieldKey])}
					</Typography>
				</Grid>
			))}
		</>
	);
}

interface CustomFieldsFormSectionProps {
	entityType: string;
	values: Record<string, unknown>;
	onChange: (values: Record<string, unknown>) => void;
}

/** Renders one input per ACTIVE CustomFieldDefinition for the given entityType (#146) -
 * the record-edit-screen half of the mechanism, alongside CustomFieldsPanel (Settings)
 * which defines these fields. Reused by every opted-in entity's create/edit form (only
 * Student for now), so a new field an admin defines shows up here with no frontend
 * change. Renders nothing (and adds nothing to the submitted payload) when the org has
 * defined no fields for this entity type. */
export function CustomFieldsFormSection({ entityType, values, onChange }: CustomFieldsFormSectionProps) {
	const [definitions, setDefinitions] = useState<CustomFieldDefinitionResponse[]>([]);

	useEffect(() => {
		listCustomFieldDefinitions(entityType)
			.then((all) => setDefinitions(all.filter((definition) => definition.status === "ACTIVE")))
			.catch(() => {});
	}, [entityType]);

	if (definitions.length === 0) {
		return null;
	}

	function setValue(fieldKey: string, value: unknown) {
		onChange({ ...values, [fieldKey]: value });
	}

	return (
		<Box sx={{ display: "flex", flexWrap: "wrap", gap: 2 }}>
			{definitions.map((definition) => {
				const value = values[definition.fieldKey];
				if (definition.dataType === "BOOLEAN") {
					return (
						<FormControlLabel
							key={definition.id}
							sx={{ minWidth: 200 }}
							control={<Switch checked={value === true || value === "true"} onChange={(e) => setValue(definition.fieldKey, e.target.checked)} />}
							label={definition.label}
						/>
					);
				}
				if (definition.dataType === "SELECT") {
					const options = (definition.options ?? "").split(",").map((option) => option.trim()).filter(Boolean);
					return (
						<TextField
							key={definition.id}
							select
							label={definition.label}
							value={value ?? ""}
							onChange={(e) => setValue(definition.fieldKey, e.target.value)}
							required={definition.required}
							sx={{ minWidth: 200, flex: 1 }}
						>
							{options.map((option) => (
								<MenuItem key={option} value={option}>
									{option}
								</MenuItem>
							))}
						</TextField>
					);
				}
				return (
					<TextField
						key={definition.id}
						label={definition.label}
						type={definition.dataType === "NUMBER" ? "number" : definition.dataType === "DATE" ? "date" : "text"}
						value={value ?? ""}
						onChange={(e) => setValue(definition.fieldKey, e.target.value)}
						required={definition.required}
						slotProps={definition.dataType === "DATE" ? { inputLabel: { shrink: true } } : undefined}
						sx={{ minWidth: 200, flex: 1 }}
					/>
				);
			})}
		</Box>
	);
}
