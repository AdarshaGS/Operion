import { useEffect, useState, type FormEvent } from "react";
import Accordion from "@mui/material/Accordion";
import AccordionDetails from "@mui/material/AccordionDetails";
import AccordionSummary from "@mui/material/AccordionSummary";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grid from "@mui/material/Grid";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { ApiError } from "../../api/client";
import {
	createCertificateType,
	getCertificateMergeFields,
	listCertificatePresets,
	listCertificateTypes,
	updateCertificateType,
	type CertificateTypeResponse,
	type UpdateCertificateTypeRequest,
} from "../../api/certificates";

type Flag = "oncePerStudent" | "requiresExit" | "requiresClearance" | "approvalRequired" | "guardianRequestable" | "active";

const FLAGS: { key: Flag; label: string; help: string }[] = [
	{ key: "approvalRequired", label: "Needs approval", help: "Someone other than the requester must approve before it can be issued" },
	{ key: "requiresClearance", label: "Check dues before issue", help: "Fees, library books/fines and transport must be clear, or overridden with a reason" },
	{ key: "requiresExit", label: "Only after the student's exit", help: "Can't be requested until an exit is recorded" },
	{ key: "oncePerStudent", label: "Once per student", help: "A second one needs the first cancelled" },
	{ key: "guardianRequestable", label: "Guardians can request it", help: "Shown in the guardian portal" },
	{ key: "active", label: "Active", help: "Switch off to stop new requests" },
];

function toForm(type: CertificateTypeResponse): UpdateCertificateTypeRequest {
	return {
		name: type.name,
		title: type.title,
		bodyTemplate: type.bodyTemplate,
		particularsTemplate: type.particularsTemplate ?? "",
		manualFields: type.manualFields ?? "",
		numberFormat: type.numberFormat,
		pageSize: type.pageSize,
		oncePerStudent: type.oncePerStudent,
		requiresExit: type.requiresExit,
		requiresClearance: type.requiresClearance,
		approvalRequired: type.approvalRequired,
		guardianRequestable: type.guardianRequestable,
		active: type.active,
	};
}

/** Settings > Certificate types: the school's own certificate wording and rules (presets are copied in by School setup). */
export function CertificateTypesPanel() {
	const [types, setTypes] = useState<CertificateTypeResponse[]>([]);
	const [selectedId, setSelectedId] = useState<number | null>(null);
	const [form, setForm] = useState<UpdateCertificateTypeRequest | null>(null);
	const [mergeFields, setMergeFields] = useState<Record<string, string>>({});
	const [presets, setPresets] = useState<{ code: string; name: string }[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);
	const [saving, setSaving] = useState(false);
	const [addOpen, setAddOpen] = useState(false);
	const [newCode, setNewCode] = useState("");
	const [newName, setNewName] = useState("");

	function load(selectId?: number) {
		listCertificateTypes()
			.then((rows) => {
				setTypes(rows);
				const pick = rows.find((row) => row.id === (selectId ?? selectedId)) ?? rows[0];
				if (pick) {
					setSelectedId(pick.id);
					setForm(toForm(pick));
				}
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load certificate types"));
	}

	useEffect(() => {
		load();
		getCertificateMergeFields().then(setMergeFields).catch(() => {});
		listCertificatePresets().then(setPresets).catch(() => {});
	}, []);

	function select(type: CertificateTypeResponse) {
		setSelectedId(type.id);
		setForm(toForm(type));
		setSaved(false);
		setError(null);
	}

	async function handleSave(event: FormEvent) {
		event.preventDefault();
		if (!form || selectedId === null) return;
		setSaving(true);
		setError(null);
		setSaved(false);
		try {
			await updateCertificateType(selectedId, form);
			setSaved(true);
			load(selectedId);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save");
		} finally {
			setSaving(false);
		}
	}

	async function handleAdd(event: FormEvent) {
		event.preventDefault();
		setError(null);
		try {
			const created = await createCertificateType(newCode, newName);
			setAddOpen(false);
			setNewCode("");
			setNewName("");
			load(created.id);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to add certificate type");
		}
	}

	const selected = types.find((type) => type.id === selectedId);

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error">{error}</Alert>}
			<Grid container spacing={2}>
				<Grid size={{ xs: 12, md: 3 }}>
					<Paper sx={{ p: 1 }}>
						<List dense>
							{types.map((type) => (
								<ListItemButton key={type.id} selected={type.id === selectedId} onClick={() => select(type)}>
									<ListItemText primary={type.name} secondary={type.active ? type.code : `${type.code} · off`} />
								</ListItemButton>
							))}
						</List>
						{types.length === 0 && (
							<Typography variant="body2" color="text.secondary" sx={{ p: 1 }}>
								No certificate types yet. Apply School setup, or add one.
							</Typography>
						)}
						<Button startIcon={<AddIcon />} size="small" onClick={() => setAddOpen(true)} sx={{ m: 1 }}>
							Add type
						</Button>
					</Paper>
				</Grid>
				<Grid size={{ xs: 12, md: 9 }}>
					{form && selected && (
						<Paper sx={{ p: 3 }} component="form" onSubmit={handleSave}>
							<Stack spacing={2}>
								<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
									<Typography variant="h6">{selected.name}</Typography>
									<Chip size="small" label={`Next number: ${selected.nextSequence}`} />
								</Box>
								{saved && <Alert severity="success">Saved. Certificates already issued keep the wording they were issued with.</Alert>}
								<Grid container spacing={2}>
									<Grid size={{ xs: 12, sm: 6 }}>
										<TextField label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required fullWidth />
									</Grid>
									<Grid size={{ xs: 12, sm: 6 }}>
										<TextField label="Printed title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required fullWidth />
									</Grid>
									<Grid size={{ xs: 12, sm: 6 }}>
										<TextField
											label="Number format"
											helperText="{SEQ:4} = zero-padded counter, {YYYY} = year, {AY} = academic year"
											value={form.numberFormat}
											onChange={(e) => setForm({ ...form, numberFormat: e.target.value })}
											required
											fullWidth
										/>
									</Grid>
									<Grid size={{ xs: 12, sm: 6 }}>
										<TextField select label="Page size" value={form.pageSize} onChange={(e) => setForm({ ...form, pageSize: e.target.value })} fullWidth>
											{["A4", "A5", "Letter"].map((size) => (
												<MenuItem key={size} value={size}>
													{size}
												</MenuItem>
											))}
										</TextField>
									</Grid>
								</Grid>
								<TextField
									label="Particulars (optional table)"
									helperText='One row per line: "Label | value". Values can use {{tokens}}.'
									value={form.particularsTemplate ?? ""}
									onChange={(e) => setForm({ ...form, particularsTemplate: e.target.value })}
									multiline
									minRows={3}
									fullWidth
									slotProps={{ htmlInput: { style: { fontFamily: "monospace", fontSize: 13 } } }}
								/>
								<TextField
									label="Wording"
									helperText="Plain text; leave a blank line between paragraphs. Use {{tokens}} for student details."
									value={form.bodyTemplate}
									onChange={(e) => setForm({ ...form, bodyTemplate: e.target.value })}
									required
									multiline
									minRows={4}
									fullWidth
								/>
								<TextField
									label="Fields typed in per request (optional)"
									helperText='One per line: "key | Label", then use {{manual.key}} in the wording.'
									value={form.manualFields ?? ""}
									onChange={(e) => setForm({ ...form, manualFields: e.target.value })}
									multiline
									minRows={2}
									fullWidth
									slotProps={{ htmlInput: { style: { fontFamily: "monospace", fontSize: 13 } } }}
								/>
								<Accordion disableGutters variant="outlined">
									<AccordionSummary expandIcon={<ExpandMoreIcon />}>
										<Typography variant="body2">Available tokens</Typography>
									</AccordionSummary>
									<AccordionDetails>
										<Stack spacing={0.5}>
											{Object.entries(mergeFields).map(([token, description]) => (
												<Typography key={token} variant="caption">
													<code>{`{{${token}}}`}</code> — {description}
												</Typography>
											))}
											<Typography variant="caption" color="text.secondary">
												A token with no value prints as a blank line to fill in by hand.
											</Typography>
										</Stack>
									</AccordionDetails>
								</Accordion>
								<Grid container spacing={1}>
									{FLAGS.map((flag) => (
										<Grid key={flag.key} size={{ xs: 12, sm: 6 }}>
											<FormControlLabel
												control={<Switch checked={form[flag.key]} onChange={(e) => setForm({ ...form, [flag.key]: e.target.checked })} />}
												label={
													<Box>
														<Typography variant="body2">{flag.label}</Typography>
														<Typography variant="caption" color="text.secondary">
															{flag.help}
														</Typography>
													</Box>
												}
											/>
										</Grid>
									))}
								</Grid>
								<Box>
									<Button type="submit" variant="contained" disabled={saving}>
										Save
									</Button>
								</Box>
							</Stack>
						</Paper>
					)}
				</Grid>
			</Grid>

			<Dialog open={addOpen} onClose={() => setAddOpen(false)} component="form" onSubmit={handleAdd} fullWidth maxWidth="xs">
				<DialogTitle>Add certificate type</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{presets.length > 0 && (
							<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", gap: 1 }}>
								{presets.map((preset) => (
									<Chip
										key={preset.code}
										label={preset.name}
										onClick={() => {
											setNewCode(preset.code);
											setNewName(preset.name);
										}}
										variant={newCode === preset.code ? "filled" : "outlined"}
									/>
								))}
							</Stack>
						)}
						<TextField
							label="Code"
							helperText="A preset's code starts from its wording; any other code starts from a short generic one."
							value={newCode}
							onChange={(e) => setNewCode(e.target.value.toUpperCase())}
							required
							fullWidth
						/>
						<TextField label="Name" value={newName} onChange={(e) => setNewName(e.target.value)} required fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAddOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={!newCode.trim() || !newName.trim()}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
