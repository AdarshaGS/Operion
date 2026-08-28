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
import Divider from "@mui/material/Divider";
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
import { createOrganisation, listOrganisations, type CreateOrganisationRequest, type OrganisationResponse } from "./api/organisations";
import { listPlans, type PlanResponse } from "./api/plans";
import { PlatformApiError } from "./api/platformClient";

const STATUS_COLOR: Record<string, "success" | "warning" | "error" | "default"> = {
	TRIAL: "warning",
	ACTIVE: "success",
	SUSPENDED: "error",
	ARCHIVED: "default",
};

const ORGANISATION_TYPES = ["SCHOOL", "GROUP_OF_SCHOOLS"];
const SCHOOL_BOARDS = ["CBSE", "ICSE", "STATE_BOARD", "IB", "CAMBRIDGE", "OTHER"];

const EMPTY_FORM = {
	name: "",
	legalName: "",
	slug: "",
	adminEmail: "",
	adminPassword: "",
	adminFirstName: "",
	adminLastName: "",
	organisationType: "SCHOOL",
	board: "",
	schoolCode: "",
	primaryContactName: "",
	primaryContactEmail: "",
	primaryContactPhone: "",
	addressLine1: "",
	addressLine2: "",
	city: "",
	state: "",
	country: "",
	pincode: "",
	timezone: "",
	academicYearName: "",
	academicYearStartDate: "",
	academicYearEndDate: "",
	planId: "",
	planStartDate: "",
};

function buildRequest(form: typeof EMPTY_FORM): CreateOrganisationRequest {
	return {
		name: form.name,
		legalName: form.legalName,
		slug: form.slug,
		adminEmail: form.adminEmail,
		adminPassword: form.adminPassword,
		adminFirstName: form.adminFirstName,
		adminLastName: form.adminLastName,
		organisationType: form.organisationType || null,
		board: form.board || null,
		schoolCode: form.schoolCode || null,
		primaryContactName: form.primaryContactName || null,
		primaryContactEmail: form.primaryContactEmail || null,
		primaryContactPhone: form.primaryContactPhone || null,
		addressLine1: form.addressLine1 || null,
		addressLine2: form.addressLine2 || null,
		city: form.city || null,
		state: form.state || null,
		country: form.country || null,
		pincode: form.pincode || null,
		timezone: form.timezone || null,
		academicYearName: form.academicYearName || null,
		academicYearStartDate: form.academicYearStartDate || null,
		academicYearEndDate: form.academicYearEndDate || null,
		planId: form.planId ? Number(form.planId) : null,
		planStartDate: form.planStartDate || null,
	};
}

export function OrganisationsPage() {
	const navigate = useNavigate();
	const [organisations, setOrganisations] = useState<OrganisationResponse[] | null>(null);
	const [plans, setPlans] = useState<PlanResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState(EMPTY_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listOrganisations()
			.then(setOrganisations)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load organisations"));
	}

	useEffect(() => {
		refresh();
		listPlans().then(setPlans).catch(() => {});
	}, []);

	function field(key: keyof typeof form) {
		return (e: React.ChangeEvent<HTMLInputElement>) => setForm((prev) => ({ ...prev, [key]: e.target.value }));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createOrganisation(buildRequest(form));
			setDialogOpen(false);
			setForm(EMPTY_FORM);
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to create organisation");
		} finally {
			setSubmitting(false);
		}
	}

	const canSubmit =
		form.name && form.legalName && form.slug && form.adminEmail && form.adminPassword && form.adminFirstName && form.adminLastName;

	return (
		<Stack spacing={2}>
			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end" }}>
				<Stack spacing={0.5}>
					<Typography variant="overline" color="text.secondary">
						Every school on the platform
					</Typography>
					<Typography variant="h4" component="h1">
						Organisations
					</Typography>
				</Stack>
				<Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
					Add organisation
				</Button>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			{organisations && organisations.length === 0 && <Alert severity="info">No organisations provisioned yet.</Alert>}

			{organisations && organisations.length > 0 && (
				<TableContainer component={Paper}>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Name</TableCell>
								<TableCell>Slug</TableCell>
								<TableCell>Legal name</TableCell>
								<TableCell>Type</TableCell>
								<TableCell>Status</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{organisations.map((org) => (
								<TableRow key={org.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/platform/organisations/${org.id}`)}>
									<TableCell>{org.name}</TableCell>
									<TableCell>{org.slug}</TableCell>
									<TableCell>{org.legalName}</TableCell>
									<TableCell>{org.organisationType}</TableCell>
									<TableCell>
										<Chip label={org.status} size="small" color={STATUS_COLOR[org.status] ?? "default"} />
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			)}

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>Add organisation</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="School name" value={form.name} onChange={field("name")} required fullWidth />
						<TextField label="Legal name" value={form.legalName} onChange={field("legalName")} required fullWidth />
						<TextField
							label="Slug"
							value={form.slug}
							onChange={field("slug")}
							required
							fullWidth
							helperText="Used in login URLs, e.g. acme-school"
						/>
						<Divider textAlign="left">
							<Typography variant="caption" color="text.secondary">
								First admin login
							</Typography>
						</Divider>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField label="Admin first name" value={form.adminFirstName} onChange={field("adminFirstName")} required fullWidth />
							<TextField label="Admin last name" value={form.adminLastName} onChange={field("adminLastName")} required fullWidth />
						</Box>
						<TextField label="Admin email" type="email" value={form.adminEmail} onChange={field("adminEmail")} required fullWidth />
						<TextField
							label="Admin password"
							type="password"
							value={form.adminPassword}
							onChange={field("adminPassword")}
							required
							fullWidth
						/>

						<Divider textAlign="left">
							<Typography variant="caption" color="text.secondary">
								School details (optional)
							</Typography>
						</Divider>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField select label="Organisation type" value={form.organisationType} onChange={field("organisationType")} fullWidth>
								{ORGANISATION_TYPES.map((type) => (
									<MenuItem key={type} value={type}>
										{type}
									</MenuItem>
								))}
							</TextField>
							<TextField select label="Board" value={form.board} onChange={field("board")} fullWidth>
								<MenuItem value="">—</MenuItem>
								{SCHOOL_BOARDS.map((board) => (
									<MenuItem key={board} value={board}>
										{board}
									</MenuItem>
								))}
							</TextField>
						</Box>
						<TextField
							label="School code / affiliation number"
							value={form.schoolCode}
							onChange={field("schoolCode")}
							fullWidth
						/>

						<Divider textAlign="left">
							<Typography variant="caption" color="text.secondary">
								Primary contact & address (optional)
							</Typography>
						</Divider>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField label="Contact name" value={form.primaryContactName} onChange={field("primaryContactName")} fullWidth />
							<TextField
								label="Contact email"
								type="email"
								value={form.primaryContactEmail}
								onChange={field("primaryContactEmail")}
								fullWidth
							/>
							<TextField label="Contact phone" value={form.primaryContactPhone} onChange={field("primaryContactPhone")} fullWidth />
						</Box>
						<TextField label="Address line 1" value={form.addressLine1} onChange={field("addressLine1")} fullWidth />
						<TextField label="Address line 2" value={form.addressLine2} onChange={field("addressLine2")} fullWidth />
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField label="City" value={form.city} onChange={field("city")} fullWidth />
							<TextField label="State" value={form.state} onChange={field("state")} fullWidth />
							<TextField label="Country" value={form.country} onChange={field("country")} fullWidth />
							<TextField label="Pincode" value={form.pincode} onChange={field("pincode")} fullWidth />
						</Box>
						<TextField
							label="Time zone"
							value={form.timezone}
							onChange={field("timezone")}
							fullWidth
							helperText='e.g. Asia/Kolkata - defaults to Asia/Kolkata if left blank'
						/>

						<Divider textAlign="left">
							<Typography variant="caption" color="text.secondary">
								First academic year (optional)
							</Typography>
						</Divider>
						<TextField label="Academic year name" value={form.academicYearName} onChange={field("academicYearName")} fullWidth />
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField
								label="Start date"
								type="date"
								value={form.academicYearStartDate}
								onChange={field("academicYearStartDate")}
								fullWidth
								disabled={!form.academicYearName}
								required={!!form.academicYearName}
								slotProps={{ inputLabel: { shrink: true } }}
							/>
							<TextField
								label="End date"
								type="date"
								value={form.academicYearEndDate}
								onChange={field("academicYearEndDate")}
								fullWidth
								disabled={!form.academicYearName}
								required={!!form.academicYearName}
								slotProps={{ inputLabel: { shrink: true } }}
							/>
						</Box>

						<Divider textAlign="left">
							<Typography variant="caption" color="text.secondary">
								Subscription plan (optional)
							</Typography>
						</Divider>
						<Box sx={{ display: "flex", gap: 2 }}>
							<TextField select label="Plan" value={form.planId} onChange={field("planId")} fullWidth>
								<MenuItem value="">— None —</MenuItem>
								{plans.map((plan) => (
									<MenuItem key={plan.id} value={plan.id}>
										{plan.name}
									</MenuItem>
								))}
							</TextField>
							<TextField
								label="Plan start date"
								type="date"
								value={form.planStartDate}
								onChange={field("planStartDate")}
								fullWidth
								disabled={!form.planId}
								slotProps={{ inputLabel: { shrink: true } }}
							/>
						</Box>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !canSubmit}>
						Create
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
