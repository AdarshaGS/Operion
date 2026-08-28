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
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import { useAuth } from "../../auth/AuthContext";
import { ApiError } from "../../api/client";
import { createReport, listReports, seedStandardReports, type SavedReportResponse } from "../../api/reports";

export const REPORT_STATUS_COLOR: Record<string, "default" | "success"> = {
	DRAFT: "default",
	PUBLISHED: "success",
	ARCHIVED: "default",
};

const PARAMETER_TYPES = ["STRING", "NUMBER", "DATE", "BOOLEAN"];

interface DraftParameter {
	name: string;
	type: string;
	label: string;
}

interface DraftColumn {
	sourceColumn: string;
	label: string;
}

function emptyParameter(): DraftParameter {
	return { name: "", type: "STRING", label: "" };
}

function emptyColumn(): DraftColumn {
	return { sourceColumn: "", label: "" };
}

export function ReportsPanel() {
	const navigate = useNavigate();
	const { session, hasAnyPermission } = useAuth();
	const [reports, setReports] = useState<SavedReportResponse[]>([]);
	const [tab, setTab] = useState<"mine" | "shared">("mine");
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [sqlQuery, setSqlQuery] = useState("");
	const [parameters, setParameters] = useState<DraftParameter[]>([]);
	const [columns, setColumns] = useState<DraftColumn[]>([]);
	const [submitting, setSubmitting] = useState(false);
	const [seeding, setSeeding] = useState(false);

	function refresh() {
		listReports()
			.then(setReports)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load reports"));
	}

	useEffect(refresh, []);

	function openDialog() {
		setName("");
		setDescription("");
		setSqlQuery("");
		setParameters([]);
		setColumns([]);
		setDialogOpen(true);
	}

	function updateParameter(index: number, patch: Partial<DraftParameter>) {
		setParameters((prev) => prev.map((p, i) => (i === index ? { ...p, ...patch } : p)));
	}

	function updateColumn(index: number, patch: Partial<DraftColumn>) {
		setColumns((prev) => prev.map((c, i) => (i === index ? { ...c, ...patch } : c)));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createReport({
				name,
				description: description || null,
				sqlQuery,
				parameters: parameters.map((p, i) => ({ ...p, sortOrder: i })),
				columns: columns.map((c, i) => ({ ...c, sortOrder: i })),
			});
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create report");
		} finally {
			setSubmitting(false);
		}
	}

	async function handleSeedStandard() {
		setSeeding(true);
		try {
			await seedStandardReports();
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to seed standard reports");
		} finally {
			setSeeding(false);
		}
	}

	const visibleReports = reports.filter((r) => (tab === "mine" ? r.createdBy === session?.userId : r.createdBy !== session?.userId));

	const canSubmit = name !== "" && sqlQuery !== "" && columns.every((c) => c.sourceColumn !== "" && c.label !== "");

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
					<Typography variant="h6">Reports</Typography>
					<Stack direction="row" spacing={1}>
						{hasAnyPermission(["REPORT_MANAGE"]) && (
							<Button size="small" variant="outlined" disabled={seeding} onClick={handleSeedStandard}>
								Seed standard reports
							</Button>
						)}
						<Button size="small" startIcon={<AddIcon />} onClick={openDialog}>
							Create report
						</Button>
					</Stack>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<Tabs value={tab} onChange={(_, value) => setTab(value)}>
					<Tab label="My reports" value="mine" />
					<Tab label="Shared reports" value="shared" />
				</Tabs>

				{visibleReports.length === 0 && <Alert severity="info">No reports here yet.</Alert>}

				{visibleReports.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Name</TableCell>
									<TableCell>Description</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{visibleReports.map((report) => (
									<TableRow key={report.id} hover onClick={() => navigate(`/reports/${report.id}`)} sx={{ cursor: "pointer" }}>
										<TableCell>{report.name}</TableCell>
										<TableCell>{report.description}</TableCell>
										<TableCell>
											<Chip label={report.status} size="small" color={REPORT_STATUS_COLOR[report.status] ?? "default"} />
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="md">
				<DialogTitle>Create report</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
						<TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} fullWidth />
						<TextField
							label="SQL query"
							value={sqlQuery}
							onChange={(e) => setSqlQuery(e.target.value)}
							required
							fullWidth
							multiline
							minRows={3}
							slotProps={{ htmlInput: { style: { fontFamily: "monospace" } } }}
							helperText="A single SELECT statement against the reporting_* views only - reporting_students, reporting_attendance_daily, reporting_fee_invoices, reporting_fee_payments, reporting_exam_results, reporting_staff, reporting_sales, reporting_inventory_stock, reporting_purchase_orders, reporting_organisation (never the application's own tables). Use :name for a filter parameter, e.g. WHERE sale_date BETWEEN :fromDate AND :toDate"
						/>

						<Typography variant="subtitle2">Filter parameters</Typography>
						{parameters.map((parameter, index) => (
							<Stack key={index} direction="row" spacing={1} sx={{ alignItems: "center" }}>
								<TextField
									label="Name (:name)"
									value={parameter.name}
									onChange={(e) => updateParameter(index, { name: e.target.value })}
									required
									sx={{ flex: 1 }}
								/>
								<TextField
									select
									label="Type"
									value={parameter.type}
									onChange={(e) => updateParameter(index, { type: e.target.value })}
									required
									sx={{ flex: 1 }}
								>
									{PARAMETER_TYPES.map((type) => (
										<MenuItem key={type} value={type}>
											{type}
										</MenuItem>
									))}
								</TextField>
								<TextField
									label="Label"
									value={parameter.label}
									onChange={(e) => updateParameter(index, { label: e.target.value })}
									required
									sx={{ flex: 1 }}
								/>
								<IconButton
									size="small"
									onClick={() => setParameters((prev) => prev.filter((_, i) => i !== index))}
									aria-label="Remove parameter"
								>
									<DeleteIcon fontSize="small" />
								</IconButton>
							</Stack>
						))}
						<Button
							size="small"
							startIcon={<AddIcon />}
							onClick={() => setParameters((prev) => [...prev, emptyParameter()])}
							sx={{ alignSelf: "start" }}
						>
							Add parameter
						</Button>

						<Typography variant="subtitle2">Result columns</Typography>
						{columns.map((column, index) => (
							<Stack key={index} direction="row" spacing={1} sx={{ alignItems: "center" }}>
								<TextField
									label="Source column"
									value={column.sourceColumn}
									onChange={(e) => updateColumn(index, { sourceColumn: e.target.value })}
									required
									sx={{ flex: 1 }}
								/>
								<TextField
									label="Display label"
									value={column.label}
									onChange={(e) => updateColumn(index, { label: e.target.value })}
									required
									sx={{ flex: 1 }}
								/>
								<IconButton
									size="small"
									onClick={() => setColumns((prev) => prev.filter((_, i) => i !== index))}
									aria-label="Remove column"
								>
									<DeleteIcon fontSize="small" />
								</IconButton>
							</Stack>
						))}
						<Typography variant="caption" color="text.secondary">
							Optional - only needed to relabel a column for display. Leave empty and the Run results below just show whatever the
							query returns.
						</Typography>
						<Button
							size="small"
							startIcon={<AddIcon />}
							onClick={() => setColumns((prev) => [...prev, emptyColumn()])}
							sx={{ alignSelf: "start" }}
						>
							Add column
						</Button>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting || !canSubmit}>
						Save as draft
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
