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
import { createOrganisation, listOrganisations, type OrganisationResponse } from "./api/organisations";
import { PlatformApiError } from "./api/platformClient";

const STATUS_COLOR: Record<string, "success" | "warning" | "error" | "default"> = {
	TRIAL: "warning",
	ACTIVE: "success",
	SUSPENDED: "error",
	ARCHIVED: "default",
};

const EMPTY_FORM = { name: "", legalName: "", slug: "", adminEmail: "", adminPassword: "", adminFirstName: "", adminLastName: "" };

export function OrganisationsPage() {
	const navigate = useNavigate();
	const [organisations, setOrganisations] = useState<OrganisationResponse[] | null>(null);
	const [error, setError] = useState<string | null>(null);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [form, setForm] = useState(EMPTY_FORM);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listOrganisations()
			.then(setOrganisations)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load organisations"));
	}

	useEffect(refresh, []);

	function field(key: keyof typeof form) {
		return (e: React.ChangeEvent<HTMLInputElement>) => setForm((prev) => ({ ...prev, [key]: e.target.value }));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createOrganisation(form);
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
								<TableCell>Status</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{organisations.map((org) => (
								<TableRow key={org.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/platform/organisations/${org.id}`)}>
									<TableCell>{org.name}</TableCell>
									<TableCell>{org.slug}</TableCell>
									<TableCell>{org.legalName}</TableCell>
									<TableCell>
										<Chip label={org.status} size="small" color={STATUS_COLOR[org.status] ?? "default"} />
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			)}

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
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
						<TextField label="Admin first name" value={form.adminFirstName} onChange={field("adminFirstName")} required fullWidth />
						<TextField label="Admin last name" value={form.adminLastName} onChange={field("adminLastName")} required fullWidth />
						<TextField label="Admin email" type="email" value={form.adminEmail} onChange={field("adminEmail")} required fullWidth />
						<TextField
							label="Admin password"
							type="password"
							value={form.adminPassword}
							onChange={field("adminPassword")}
							required
							fullWidth
						/>
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
