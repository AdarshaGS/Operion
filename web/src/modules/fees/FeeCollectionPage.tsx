import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import InputAdornment from "@mui/material/InputAdornment";
import LinearProgress from "@mui/material/LinearProgress";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AccountBalanceWalletIcon from "@mui/icons-material/AccountBalanceWallet";
import AddCardIcon from "@mui/icons-material/AddCard";
import DownloadIcon from "@mui/icons-material/Download";
import NotificationsActiveIcon from "@mui/icons-material/NotificationsActive";
import PaymentsIcon from "@mui/icons-material/Payments";
import PriorityHighIcon from "@mui/icons-material/PriorityHigh";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import SearchIcon from "@mui/icons-material/Search";
import SettingsIcon from "@mui/icons-material/Settings";
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis } from "recharts";
import { listAcademicYears } from "../../api/academicYears";
import { ApiError } from "../../api/client";
import { getFeeOverview, type FeeLedgerRow, type FeeOverviewResponse, type LedgerStatus } from "../../api/feeOverview";
import { Can } from "../../auth/Can";
import { KpiTile, SectionCard } from "../../components/DashboardKit";
import { downloadCsvFile, toCsv } from "../../utils/csv";
import { colors } from "../../theme";
import { CollectFeeDialog, type FeeTarget } from "./CollectFeeDialog";

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

function shortAmount(value: number): string {
	if (value >= 10000000) return `₹${+(value / 10000000).toFixed(1)}Cr`;
	if (value >= 100000) return `₹${+(value / 100000).toFixed(1)}L`;
	if (value >= 1000) return `₹${+(value / 1000).toFixed(1)}k`;
	return `₹${value}`;
}

function shortMonth(month: string): string {
	return new Date(`${month}-01T00:00:00`).toLocaleDateString("en-IN", { month: "short" });
}

function formatDate(iso: string | null): string {
	return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }) : "—";
}

function initials(name: string): string {
	return name.split(" ").filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("");
}

const STATUS_STYLE: Record<LedgerStatus, { label: string; color: string; bg: string }> = {
	PAID: { label: "Paid", color: colors.ok, bg: colors.tintGreen },
	PARTIAL: { label: "Partial", color: colors.warn, bg: colors.tintOrange },
	UNPAID: { label: "Unpaid", color: colors.bad, bg: colors.tintPink },
	OVERDUE: { label: "Overdue", color: colors.bad, bg: colors.tintPink },
};

const TYPE_COLORS = [colors.info, colors.ok, colors.warn, colors.accent, colors.bad];


function EmptyChart({ message }: { message: string }) {
	return (
		<Box sx={{ height: "100%", display: "flex", alignItems: "center", justifyContent: "center" }}>
			<Typography variant="body2" sx={{ color: colors.inkFaint }}>
				{message}
			</Typography>
		</Box>
	);
}

/** Fees & Collections (#203) - the org-wide fee overview: headline totals, a filterable
 * student ledger, and collection charts. Collecting/assigning/correcting fees for one student
 * opens StudentFeesPanel in a dialog; setup (categories/structures) lives in FeesSetupPage. */
export function FeeCollectionPage() {
	const navigate = useNavigate();
	const [overview, setOverview] = useState<FeeOverviewResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [target, setTarget] = useState<FeeTarget | null>(null);

	const [search, setSearch] = useState("");
	const [classLabel, setClassLabel] = useState("");
	const [status, setStatus] = useState("");
	const [dueFrom, setDueFrom] = useState("");
	const [dueTo, setDueTo] = useState("");
	const [page, setPage] = useState(0);
	const [rowsPerPage, setRowsPerPage] = useState(10);
	const [selected, setSelected] = useState<Set<number>>(new Set());

	function load() {
		listAcademicYears()
			.then((years) => years.find((y) => y.current)?.id ?? years[0]?.id)
			.catch(() => undefined)
			.then((yearId) => getFeeOverview(yearId))
			.then((result) => {
				setOverview(result);
				setError(null);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load fee overview"));
	}

	useEffect(load, []);

	function openDialog(row?: FeeLedgerRow) {
		setTarget(row ? { studentEnrollmentId: row.studentEnrollmentId, academicYearId: row.academicYearId, schoolClassId: row.schoolClassId } : null);
		setDialogOpen(true);
	}

	function closeDialog() {
		setDialogOpen(false);
		load(); // payments/adjustments made inside change every figure on this page
	}

	const classOptions = useMemo(() => [...new Set((overview?.ledger ?? []).map((row) => row.classLabel))].sort(), [overview]);

	const filtered = useMemo(() => {
		const needle = search.trim().toLowerCase();
		return (overview?.ledger ?? []).filter((row) => {
			if (needle && !row.studentName.toLowerCase().includes(needle) && !row.admissionNumber.toLowerCase().includes(needle)) return false;
			if (classLabel && row.classLabel !== classLabel) return false;
			if (status && row.status !== status) return false;
			if ((dueFrom || dueTo) && !row.nextDueDate) return false;
			if (dueFrom && row.nextDueDate! < dueFrom) return false;
			if (dueTo && row.nextDueDate! > dueTo) return false;
			return true;
		});
	}, [overview, search, classLabel, status, dueFrom, dueTo]);

	useEffect(() => setPage(0), [search, classLabel, status, dueFrom, dueTo]);

	const pageRows = filtered.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);
	const hasFilters = Boolean(search || classLabel || status || dueFrom || dueTo);
	const typeTotal = (overview?.byType ?? []).reduce((sum, item) => sum + item.amount, 0);

	function clearFilters() {
		setSearch("");
		setClassLabel("");
		setStatus("");
		setDueFrom("");
		setDueTo("");
	}

	function toggle(id: number) {
		setSelected((current) => {
			const next = new Set(current);
			if (!next.delete(id)) next.add(id);
			return next;
		});
	}

	function exportCsv() {
		const columns = ["Student", "Admission no.", "Class", "Total fees", "Paid", "Outstanding", "Next due date", "Status"];
		// Selected rows when any are ticked, otherwise everything the current filters show.
		const source = selected.size > 0 ? filtered.filter((row) => selected.has(row.studentEnrollmentId)) : filtered;
		const rows = source.map((row) => ({
			Student: row.studentName,
			"Admission no.": row.admissionNumber,
			Class: row.classLabel,
			"Total fees": row.totalFees,
			Paid: row.paid,
			Outstanding: row.outstanding,
			"Next due date": row.nextDueDate ?? "",
			Status: STATUS_STYLE[row.status].label,
		}));
		downloadCsvFile("fee-ledger.csv", toCsv(columns, rows));
	}

	const allOnPageSelected = pageRows.length > 0 && pageRows.every((row) => selected.has(row.studentEnrollmentId));

	return (
		<Stack spacing={3}>
			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 2, flexWrap: "wrap" }}>
				<Box>
					<Typography variant="h4" sx={{ fontWeight: 800, color: colors.ink }}>
						Fees &amp; Collections
					</Typography>
					<Typography variant="body1" sx={{ color: colors.inkSoft }}>
						Track student fees, monitor outstanding balances, and manage collections.
					</Typography>
				</Box>
				<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", rowGap: 1 }}>
					<Can anyOf={["FEE_COLLECT"]}>
						<Button variant="contained" startIcon={<AddCardIcon />} onClick={() => openDialog()}>
							Collect Fee
						</Button>
					</Can>
					<Can anyOf={["FEE_STRUCTURE_MANAGE"]}>
						<Button variant="outlined" color="inherit" startIcon={<SettingsIcon />} onClick={() => navigate("/fees/setup")}>
							Fee Structure
						</Button>
					</Can>
					<Can anyOf={["FEE_REMINDER_SEND"]}>
						<Button variant="outlined" color="inherit" startIcon={<NotificationsActiveIcon />} onClick={() => navigate("/fees/demand")}>
							Overdue &amp; Reminders
						</Button>
					</Can>
					<Button variant="outlined" color="inherit" startIcon={<DownloadIcon />} onClick={exportCsv} disabled={!overview || filtered.length === 0}>
						Export
					</Button>
				</Stack>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap", rowGap: 2 }}>
				<KpiTile icon={<PaymentsIcon />} tint="violet" label="Total Fees" value={overview ? currency(overview.totalFees) : "—"} />
				<KpiTile icon={<AccountBalanceWalletIcon />} tint="green" label="Collected" value={overview ? currency(overview.collected) : "—"} />
				<KpiTile icon={<ReceiptLongIcon />} tint="orange" label="Outstanding" value={overview ? currency(overview.outstanding) : "—"} />
				<KpiTile icon={<PriorityHighIcon />} tint="pink" label="Overdue" value={overview ? currency(overview.overdue) : "—"} />
			</Stack>

			<Paper sx={{ p: 2.5 }}>
				<Stack spacing={2}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">Student Fee Ledger</Typography>
						<Typography variant="body2" sx={{ color: colors.inkSoft }}>
							{filtered.length === 0 ? "No students" : `Showing ${page * rowsPerPage + 1}-${Math.min((page + 1) * rowsPerPage, filtered.length)} of ${filtered.length} students`}
						</Typography>
					</Box>

					<Stack direction="row" spacing={1.5} sx={{ flexWrap: "wrap", rowGap: 1.5 }}>
						<TextField
							size="small"
							placeholder="Search by student name or admission no..."
							value={search}
							onChange={(e) => setSearch(e.target.value)}
							sx={{ flex: "1 1 260px", minWidth: 240 }}
							slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
						/>
						<TextField select size="small" label="Class" value={classLabel} onChange={(e) => setClassLabel(e.target.value)} sx={{ minWidth: 140 }}>
							<MenuItem value="">All Classes</MenuItem>
							{classOptions.map((label) => (
								<MenuItem key={label} value={label}>
									{label}
								</MenuItem>
							))}
						</TextField>
						<TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 140 }}>
							<MenuItem value="">All Status</MenuItem>
							{(Object.keys(STATUS_STYLE) as LedgerStatus[]).map((key) => (
								<MenuItem key={key} value={key}>
									{STATUS_STYLE[key].label}
								</MenuItem>
							))}
						</TextField>
						<TextField size="small" type="date" label="Due from" value={dueFrom} onChange={(e) => setDueFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
						<TextField size="small" type="date" label="Due to" value={dueTo} onChange={(e) => setDueTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
						<Button color="inherit" variant="outlined" onClick={clearFilters} disabled={!hasFilters}>
							Clear
						</Button>
					</Stack>

					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell padding="checkbox">
										<Checkbox
											size="small"
											checked={allOnPageSelected}
											indeterminate={!allOnPageSelected && pageRows.some((row) => selected.has(row.studentEnrollmentId))}
											onChange={() =>
												setSelected((current) => {
													const next = new Set(current);
													pageRows.forEach((row) => (allOnPageSelected ? next.delete(row.studentEnrollmentId) : next.add(row.studentEnrollmentId)));
													return next;
												})
											}
										/>
									</TableCell>
									<TableCell>Student</TableCell>
									<TableCell>Adm. No.</TableCell>
									<TableCell>Class</TableCell>
									<TableCell align="right">Total Fees</TableCell>
									<TableCell align="right">Paid</TableCell>
									<TableCell align="right">Outstanding</TableCell>
									<TableCell>Next Due Date</TableCell>
									<TableCell>Status</TableCell>
									<TableCell align="right">Actions</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{pageRows.map((row) => {
									const style = STATUS_STYLE[row.status];
									return (
										<TableRow key={row.studentEnrollmentId} hover>
											<TableCell padding="checkbox">
												<Checkbox size="small" checked={selected.has(row.studentEnrollmentId)} onChange={() => toggle(row.studentEnrollmentId)} />
											</TableCell>
											<TableCell>
												<Stack direction="row" spacing={1.25} sx={{ alignItems: "center" }}>
													<Avatar sx={{ width: 32, height: 32, fontSize: 13, fontWeight: 700, color: colors.accent, bgcolor: colors.accentSoft }}>{initials(row.studentName)}</Avatar>
													<Typography variant="body2" sx={{ fontWeight: 600 }}>
														{row.studentName}
													</Typography>
												</Stack>
											</TableCell>
											<TableCell>{row.admissionNumber}</TableCell>
											<TableCell>{row.classLabel}</TableCell>
											<TableCell align="right" sx={{ fontVariantNumeric: "tabular-nums" }}>{currency(row.totalFees)}</TableCell>
											<TableCell align="right" sx={{ fontVariantNumeric: "tabular-nums" }}>{currency(row.paid)}</TableCell>
											<TableCell align="right" sx={{ fontVariantNumeric: "tabular-nums", fontWeight: 700, color: row.outstanding > 0 ? colors.bad : colors.ink }}>
												{currency(row.outstanding)}
											</TableCell>
											<TableCell>{formatDate(row.nextDueDate)}</TableCell>
											<TableCell>
												<Chip size="small" label={style.label} sx={{ fontWeight: 600, color: style.color, backgroundColor: style.bg }} />
											</TableCell>
											<TableCell align="right">
												<Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
													<Button size="small" variant="outlined" color="inherit" onClick={() => openDialog(row)}>
														View
													</Button>
													{row.outstanding > 0 && (
														<Can anyOf={["FEE_COLLECT"]}>
															<Button size="small" variant="contained" onClick={() => openDialog(row)}>
																Collect
															</Button>
														</Can>
													)}
												</Stack>
											</TableCell>
										</TableRow>
									);
								})}
								{overview && pageRows.length === 0 && (
									<TableRow>
										<TableCell colSpan={10} align="center" sx={{ py: 5, color: colors.inkSoft }}>
											{overview.ledger.length === 0 ? "No invoices generated yet — assign fees to a student to see them here." : "No students match these filters."}
										</TableCell>
									</TableRow>
								)}
							</TableBody>
						</Table>
					</TableContainer>
					<TablePagination
						component="div"
						count={filtered.length}
						page={page}
						onPageChange={(_, next) => setPage(next)}
						rowsPerPage={rowsPerPage}
						onRowsPerPageChange={(e) => {
							setRowsPerPage(Number(e.target.value));
							setPage(0);
						}}
						rowsPerPageOptions={[10, 25, 50]}
					/>
				</Stack>
			</Paper>

			<Box sx={{ display: "grid", gap: 2.5, gridTemplateColumns: { xs: "1fr", lg: "1.2fr 1fr 1fr" } }}>
				<SectionCard title="Fee Collection Trend" icon={<PaymentsIcon fontSize="small" />} tint="violet">
					<Box sx={{ height: 240 }}>
						{overview && overview.trend.some((point) => point.collected > 0 || point.outstanding > 0) ? (
							<ResponsiveContainer width="100%" height="100%">
								<BarChart data={overview.trend} barGap={2}>
									<CartesianGrid strokeDasharray="3 3" vertical={false} stroke={colors.rule} />
									<XAxis dataKey="month" tickFormatter={shortMonth} tickLine={false} axisLine={false} />
									<YAxis tickFormatter={shortAmount} tickLine={false} axisLine={false} width={56} />
									<RechartsTooltip labelFormatter={(label) => shortMonth(String(label))} formatter={(value) => currency(Number(value))} />
									<Legend iconType="circle" />
									<Bar dataKey="collected" name="Collected" fill={colors.ok} radius={[4, 4, 0, 0]} />
									<Bar dataKey="outstanding" name="Outstanding" fill={colors.ruleStrong} radius={[4, 4, 0, 0]} />
								</BarChart>
							</ResponsiveContainer>
						) : (
							<EmptyChart message={overview ? "No invoices falling due in the last 6 months." : "Loading…"} />
						)}
					</Box>
				</SectionCard>

				<SectionCard title="Collection by Class" icon={<AccountBalanceWalletIcon fontSize="small" />} tint="green">
					{overview && overview.byClass.length > 0 ? (
						<Stack spacing={1.75} sx={{ maxHeight: 240, overflowY: "auto", pr: 0.5 }}>
							{overview.byClass.map((item, index) => (
								<Box key={item.schoolClassId}>
									<Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
										<Typography variant="body2">{item.classLabel}</Typography>
										<Typography variant="body2" sx={{ fontWeight: 700 }}>
											{item.percent}%
										</Typography>
									</Box>
									<LinearProgress
										variant="determinate"
										value={item.percent}
										sx={{ height: 8, borderRadius: 4, backgroundColor: colors.paperSunken, "& .MuiLinearProgress-bar": { borderRadius: 4, backgroundColor: TYPE_COLORS[index % TYPE_COLORS.length] } }}
									/>
								</Box>
							))}
						</Stack>
					) : (
						<Box sx={{ height: 240 }}>
							<EmptyChart message={overview ? "No invoices yet." : "Loading…"} />
						</Box>
					)}
				</SectionCard>

				<SectionCard title="Fee Type Breakdown" icon={<ReceiptLongIcon fontSize="small" />} tint="orange">
					{overview && overview.byType.length > 0 ? (
						<Stack direction="row" spacing={2} sx={{ alignItems: "center", height: 240 }}>
							<Box sx={{ position: "relative", width: 170, height: 170, flexShrink: 0 }}>
								<ResponsiveContainer width="100%" height="100%">
									<PieChart>
										<Pie data={overview.byType} dataKey="amount" nameKey="name" innerRadius={52} outerRadius={82} paddingAngle={2} stroke="none">
											{overview.byType.map((item, index) => (
												<Cell key={item.name} fill={TYPE_COLORS[index % TYPE_COLORS.length]} />
											))}
										</Pie>
										<RechartsTooltip formatter={(value) => currency(Number(value))} />
									</PieChart>
								</ResponsiveContainer>
								<Box sx={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", pointerEvents: "none" }}>
									<Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.1 }}>
										{shortAmount(typeTotal)}
									</Typography>
									<Typography variant="caption" sx={{ color: colors.inkSoft }}>
										Total Fees
									</Typography>
								</Box>
							</Box>
							<Stack spacing={1} sx={{ minWidth: 0, flexGrow: 1 }}>
								{overview.byType.map((item, index) => (
									<Box key={item.name} sx={{ display: "flex", alignItems: "center", gap: 1 }}>
										<Box sx={{ width: 10, height: 10, borderRadius: "50%", flexShrink: 0, backgroundColor: TYPE_COLORS[index % TYPE_COLORS.length] }} />
										<Typography variant="body2" noWrap sx={{ flexGrow: 1 }}>
											{item.name}
										</Typography>
										<Typography variant="body2" sx={{ fontWeight: 700 }}>
											{typeTotal > 0 ? Math.round((item.amount / typeTotal) * 100) : 0}%
										</Typography>
									</Box>
								))}
							</Stack>
						</Stack>
					) : (
						<Box sx={{ height: 240 }}>
							<EmptyChart message={overview ? "No invoices yet." : "Loading…"} />
						</Box>
					)}
				</SectionCard>
			</Box>

			<CollectFeeDialog open={dialogOpen} onClose={closeDialog} target={target} />
		</Stack>
	);
}
