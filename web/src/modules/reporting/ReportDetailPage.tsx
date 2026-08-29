import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
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
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import DeleteIcon from "@mui/icons-material/Delete";
import { ApiError } from "../../api/client";
import {
	archiveReport,
	duplicateReport,
	exportReport,
	getReport,
	publishReport,
	runReport,
	shareReport,
	updateReport,
	type ReportColumn,
	type ReportParameter,
	type ReportResultResponse,
	type SavedReportResponse,
} from "../../api/reports";
import { downloadCsvFile, toCsv } from "../../utils/csv";
import { REPORT_STATUS_COLOR } from "./ReportsPanel";

function downloadCsv(filename: string, result: ReportResultResponse) {
	downloadCsvFile(filename, toCsv(result.columns, result.rows));
}

export function ReportDetailPage() {
	const { reportId } = useParams<{ reportId: string }>();
	const navigate = useNavigate();

	const [report, setReport] = useState<SavedReportResponse | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const [actionSubmitting, setActionSubmitting] = useState(false);

	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [sqlQuery, setSqlQuery] = useState("");
	const [parameters, setParameters] = useState<ReportParameter[]>([]);
	const [columns, setColumns] = useState<ReportColumn[]>([]);

	const [paramValues, setParamValues] = useState<Record<string, string>>({});
	const [result, setResult] = useState<ReportResultResponse | null>(null);
	const [running, setRunning] = useState(false);

	const [shareDialogOpen, setShareDialogOpen] = useState(false);
	const [sharePrincipalType, setSharePrincipalType] = useState<"USER" | "ROLE">("USER");
	const [sharePrincipalId, setSharePrincipalId] = useState("");
	const [shareCanRun, setShareCanRun] = useState(true);
	const [shareCanEdit, setShareCanEdit] = useState(false);

	function refresh() {
		if (!reportId) return;
		getReport(Number(reportId))
			.then((loaded) => {
				setReport(loaded);
				setName(loaded.name);
				setDescription(loaded.description ?? "");
				setSqlQuery(loaded.sqlQuery);
				setParameters(loaded.parameters);
				setColumns(loaded.columns);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load report"))
			.finally(() => setLoading(false));
	}

	useEffect(refresh, [reportId]);

	async function runAction(action: () => Promise<unknown>, failureMessage: string) {
		setActionSubmitting(true);
		try {
			await action();
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : failureMessage);
		} finally {
			setActionSubmitting(false);
		}
	}

	async function handleSaveDefinition(event: FormEvent) {
		event.preventDefault();
		if (!report) return;
		setActionSubmitting(true);
		try {
			await updateReport(report.id, {
				name,
				description: description || null,
				sqlQuery,
				parameters: parameters.map((p, i) => ({ ...p, sortOrder: i })),
				columns: columns.map((c, i) => ({ ...c, sortOrder: i })),
			});
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save report");
		} finally {
			setActionSubmitting(false);
		}
	}

	function parameterValue(name: string): unknown {
		const parameter = parameters.find((p) => p.name === name);
		const raw = paramValues[name] ?? "";
		if (parameter?.type === "NUMBER") return raw === "" ? null : Number(raw);
		if (parameter?.type === "BOOLEAN") return raw === "true";
		return raw;
	}

	async function handleRun() {
		if (!report) return;
		setRunning(true);
		setError(null);
		try {
			const values = Object.fromEntries(parameters.map((p) => [p.name, parameterValue(p.name)]));
			setResult(await runReport(report.id, values));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to run report");
		} finally {
			setRunning(false);
		}
	}

	async function handleExport() {
		if (!report) return;
		setRunning(true);
		setError(null);
		try {
			const values = Object.fromEntries(parameters.map((p) => [p.name, parameterValue(p.name)]));
			const exported = await exportReport(report.id, values);
			setResult(exported);
			downloadCsv(`${report.name}.csv`, exported);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to export report");
		} finally {
			setRunning(false);
		}
	}

	async function handleShareSubmit(event: FormEvent) {
		event.preventDefault();
		if (!report || sharePrincipalId === "") return;
		setActionSubmitting(true);
		try {
			await shareReport(report.id, {
				principalType: sharePrincipalType,
				principalId: Number(sharePrincipalId),
				canRun: shareCanRun,
				canEdit: shareCanEdit,
			});
			setShareDialogOpen(false);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to share report");
		} finally {
			setActionSubmitting(false);
		}
	}

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	if (!report) {
		return <Alert severity="error">{error ?? "Report not found"}</Alert>;
	}

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/reports")}>
					Back to reports
				</Button>
			</Box>

			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
				<Typography variant="h4" component="h1">
					{report.name}
				</Typography>
				<Chip label={report.status} color={REPORT_STATUS_COLOR[report.status] ?? "default"} />
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
				{report.status === "DRAFT" && (
					<Button size="small" variant="outlined" disabled={actionSubmitting} onClick={() => runAction(() => publishReport(report.id), "Failed to publish report")}>
						Publish
					</Button>
				)}
				{report.status !== "ARCHIVED" && (
					<Button size="small" color="error" disabled={actionSubmitting} onClick={() => runAction(() => archiveReport(report.id), "Failed to archive report")}>
						Archive
					</Button>
				)}
				<Button
					size="small"
					disabled={actionSubmitting}
					onClick={() =>
						runAction(async () => {
							const copy = await duplicateReport(report.id);
							navigate(`/reports/${copy.id}`);
						}, "Failed to duplicate report")
					}
				>
					Duplicate
				</Button>
				<Button size="small" onClick={() => setShareDialogOpen(true)}>
					Share
				</Button>
			</Stack>

			<Paper sx={{ p: 3 }} component="form" onSubmit={handleSaveDefinition}>
				<Stack spacing={2}>
					<Typography variant="h6">Definition</Typography>
					<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
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
						helperText="A single SELECT statement against the reporting_* views only - reporting_students, reporting_attendance_daily, reporting_fee_invoices, reporting_fee_payments, reporting_exam_results, reporting_staff, reporting_sales, reporting_inventory_stock, reporting_purchase_orders, reporting_organisation (never the application's own tables)."
					/>

					<Typography variant="subtitle2">Filter parameters</Typography>
					{parameters.map((parameter, index) => (
						<Stack key={index} direction="row" spacing={1} sx={{ alignItems: "center" }}>
							<TextField
								label="Name (:name)"
								value={parameter.name}
								onChange={(e) => setParameters((prev) => prev.map((p, i) => (i === index ? { ...p, name: e.target.value } : p)))}
								required
								sx={{ flex: 1 }}
							/>
							<TextField
								select
								label="Type"
								value={parameter.type}
								onChange={(e) => setParameters((prev) => prev.map((p, i) => (i === index ? { ...p, type: e.target.value } : p)))}
								required
								sx={{ flex: 1 }}
							>
								{["STRING", "NUMBER", "DATE", "BOOLEAN"].map((type) => (
									<MenuItem key={type} value={type}>
										{type}
									</MenuItem>
								))}
							</TextField>
							<TextField
								label="Label"
								value={parameter.label}
								onChange={(e) => setParameters((prev) => prev.map((p, i) => (i === index ? { ...p, label: e.target.value } : p)))}
								required
								sx={{ flex: 1 }}
							/>
							<IconButton size="small" onClick={() => setParameters((prev) => prev.filter((_, i) => i !== index))} aria-label="Remove parameter">
								<DeleteIcon fontSize="small" />
							</IconButton>
						</Stack>
					))}
					<Button
						size="small"
						startIcon={<AddIcon />}
						onClick={() => setParameters((prev) => [...prev, { name: "", type: "STRING", label: "", sortOrder: prev.length }])}
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
								onChange={(e) => setColumns((prev) => prev.map((c, i) => (i === index ? { ...c, sourceColumn: e.target.value } : c)))}
								required
								sx={{ flex: 1 }}
							/>
							<TextField
								label="Display label"
								value={column.label}
								onChange={(e) => setColumns((prev) => prev.map((c, i) => (i === index ? { ...c, label: e.target.value } : c)))}
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
						Optional - only needed to relabel a column for display. Leave empty and the Run results below just show whatever the query
						returns.
					</Typography>
					<Button
						size="small"
						startIcon={<AddIcon />}
						onClick={() => setColumns((prev) => [...prev, { sourceColumn: "", label: "", sortOrder: prev.length }])}
						sx={{ alignSelf: "start" }}
					>
						Add column
					</Button>

					<Box>
						<Button type="submit" variant="contained" disabled={actionSubmitting}>
							Save changes
						</Button>
					</Box>
				</Stack>
			</Paper>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Run</Typography>
					<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
						{parameters.map((parameter) =>
							parameter.type === "BOOLEAN" ? (
								<TextField
									key={parameter.name}
									select
									label={parameter.label}
									value={paramValues[parameter.name] ?? "false"}
									onChange={(e) => setParamValues((prev) => ({ ...prev, [parameter.name]: e.target.value }))}
									sx={{ minWidth: 160 }}
								>
									<MenuItem value="true">True</MenuItem>
									<MenuItem value="false">False</MenuItem>
								</TextField>
							) : (
								<TextField
									key={parameter.name}
									label={parameter.label}
									type={parameter.type === "DATE" ? "date" : parameter.type === "NUMBER" ? "number" : "text"}
									value={paramValues[parameter.name] ?? ""}
									onChange={(e) => setParamValues((prev) => ({ ...prev, [parameter.name]: e.target.value }))}
									slotProps={parameter.type === "DATE" ? { inputLabel: { shrink: true } } : undefined}
									sx={{ minWidth: 160 }}
								/>
							),
						)}
					</Stack>
					<Stack direction="row" spacing={1}>
						<Button variant="contained" disabled={running} onClick={handleRun}>
							Run
						</Button>
						<Button variant="outlined" disabled={running} onClick={handleExport}>
							Export as CSV
						</Button>
					</Stack>

					{result && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										{result.columns.map((column) => (
											<TableCell key={column}>{column}</TableCell>
										))}
									</TableRow>
								</TableHead>
								<TableBody>
									{result.rows.map((row, index) => (
										<TableRow key={index}>
											{result.columns.map((column) => (
												<TableCell key={column}>{String(row[column] ?? "")}</TableCell>
											))}
										</TableRow>
									))}
								</TableBody>
							</Table>
							{result.rows.length === 0 && <Alert severity="info">No rows returned.</Alert>}
						</TableContainer>
					)}
				</Stack>
			</Paper>

			<Dialog open={shareDialogOpen} onClose={() => setShareDialogOpen(false)} component="form" onSubmit={handleShareSubmit} fullWidth maxWidth="xs">
				<DialogTitle>Share report</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField select label="Share with" value={sharePrincipalType} onChange={(e) => setSharePrincipalType(e.target.value as "USER" | "ROLE")} fullWidth>
							<MenuItem value="USER">A specific user</MenuItem>
							<MenuItem value="ROLE">Everyone with a role</MenuItem>
						</TextField>
						<TextField
							label={sharePrincipalType === "USER" ? "User ID" : "Role ID"}
							type="number"
							value={sharePrincipalId}
							onChange={(e) => setSharePrincipalId(e.target.value)}
							required
							autoFocus
							fullWidth
						/>
						<FormControlLabel control={<Checkbox checked={shareCanRun} onChange={(e) => setShareCanRun(e.target.checked)} />} label="Can run" />
						<FormControlLabel control={<Checkbox checked={shareCanEdit} onChange={(e) => setShareCanEdit(e.target.checked)} />} label="Can edit" />
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setShareDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={actionSubmitting || sharePrincipalId === ""}>
						Share
					</Button>
				</DialogActions>
			</Dialog>
		</Stack>
	);
}
