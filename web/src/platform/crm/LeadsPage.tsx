import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import {
	changeLeadStage,
	createLead,
	LEAD_SOURCES,
	LEAD_STAGES,
	listCrmAdmins,
	listLeads,
	type CrmAdminResponse,
	type LeadResponse,
	type LeadStage,
} from "../api/crm";
import { PlatformApiError } from "../api/platformClient";
import { STAGE_COLOR, STAGE_LABEL, titleCase } from "./crmFormat";

const EMPTY_FORM = { schoolName: "", source: "INBOUND", estimatedStudentCount: "", ownerId: "", notes: "" };

/** Pipeline board: one column per stage, a card per lead. Stage moves are unrestricted (no
 * transition map, same as OrganisationStatus), so each card carries a stage selector rather
 * than drag-and-drop. */
export function LeadsPage() {
	const navigate = useNavigate();
	const [leads, setLeads] = useState<LeadResponse[] | null>(null);
	const [admins, setAdmins] = useState<CrmAdminResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState(EMPTY_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listLeads()
			.then(setLeads)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load leads"));
	}

	useEffect(() => {
		refresh();
		listCrmAdmins().then(setAdmins).catch(() => {});
	}, []);

	function field(key: keyof typeof form) {
		return (e: React.ChangeEvent<HTMLInputElement>) => setForm((prev) => ({ ...prev, [key]: e.target.value }));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createLead({
				schoolName: form.schoolName,
				source: form.source,
				estimatedStudentCount: form.estimatedStudentCount ? Number(form.estimatedStudentCount) : null,
				ownerId: form.ownerId ? Number(form.ownerId) : null,
				notes: form.notes || null,
			});
			setDialogOpen(false);
			setForm(EMPTY_FORM);
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to create lead");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleStageChange(lead: LeadResponse, stage: LeadStage) {
		try {
			const updated = await changeLeadStage(lead.id, stage);
			setLeads((prev) => prev?.map((l) => (l.id === updated.id ? updated : l)) ?? prev);
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to change stage");
		}
	}

	return (
		<Stack spacing={2}>
			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end" }}>
				<Stack spacing={0.5}>
					<Typography variant="overline" color="text.secondary">
						Sales
					</Typography>
					<Typography variant="h4" component="h1">
						Leads
					</Typography>
				</Stack>
				<Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
					New lead
				</Button>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}
			{leads && leads.length === 0 && <Alert severity="info">No leads yet — add the first prospective school.</Alert>}

			{leads && leads.length > 0 && (
				<Box sx={{ display: "flex", gap: 2, overflowX: "auto", pb: 1, alignItems: "flex-start" }}>
					{LEAD_STAGES.map((stage) => {
						const inStage = leads.filter((lead) => lead.stage === stage);
						return (
							<Box key={stage} sx={{ flex: "1 0 230px", minWidth: 230 }}>
								<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 1 }}>
									<Typography variant="subtitle2">{STAGE_LABEL[stage]}</Typography>
									<Chip label={inStage.length} size="small" color={STAGE_COLOR[stage]} />
								</Stack>
								<Stack spacing={1}>
									{inStage.map((lead) => (
										<Paper
											key={lead.id}
											variant="outlined"
											sx={{ p: 1.5, cursor: "pointer" }}
											onClick={() => navigate(`/platform/crm/leads/${lead.id}`)}
										>
											<Stack spacing={1}>
												<Typography variant="body2" sx={{ fontWeight: 600 }}>
													{lead.schoolName}
												</Typography>
												<Stack direction="row" spacing={0.5} sx={{ flexWrap: "wrap", gap: 0.5 }}>
													<Chip label={titleCase(lead.source)} size="small" variant="outlined" />
													{lead.estimatedStudentCount != null && (
														<Chip label={`${lead.estimatedStudentCount.toLocaleString("en-IN")} students`} size="small" variant="outlined" />
													)}
													{lead.convertedOrganisationId != null && <Chip label="Converted" size="small" color="success" />}
												</Stack>
												<Typography variant="caption" color="text.secondary">
													{lead.ownerName ? `Owner: ${lead.ownerName}` : "Unowned"}
												</Typography>
												<TextField
													select
													size="small"
													label="Stage"
													value={lead.stage}
													onChange={(e) => handleStageChange(lead, e.target.value as LeadStage)}
													onClick={(e) => e.stopPropagation()}
												>
													{LEAD_STAGES.map((s) => (
														<MenuItem key={s} value={s}>
															{STAGE_LABEL[s]}
														</MenuItem>
													))}
												</TextField>
											</Stack>
										</Paper>
									))}
								</Stack>
							</Box>
						);
					})}
				</Box>
			)}

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>New lead</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="School name" value={form.schoolName} onChange={field("schoolName")} required fullWidth />
						<TextField select label="Source" value={form.source} onChange={field("source")} required fullWidth>
							{LEAD_SOURCES.map((source) => (
								<MenuItem key={source} value={source}>
									{titleCase(source)}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Estimated students"
							type="number"
							value={form.estimatedStudentCount}
							onChange={field("estimatedStudentCount")}
							slotProps={{ htmlInput: { min: 0 } }}
							fullWidth
						/>
						<TextField select label="Owner" value={form.ownerId} onChange={field("ownerId")} helperText="Defaults to you" fullWidth>
							<MenuItem value="">Me</MenuItem>
							{admins.map((admin) => (
								<MenuItem key={admin.id} value={admin.id}>
									{admin.name}
								</MenuItem>
							))}
						</TextField>
						<TextField label="Notes" value={form.notes} onChange={field("notes")} multiline minRows={2} fullWidth />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !form.schoolName.trim()}>
						Create
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
