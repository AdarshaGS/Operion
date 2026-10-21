import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import InputAdornment from "@mui/material/InputAdornment";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import CheckCircleOutlinedIcon from "@mui/icons-material/CheckCircleOutlined";
import GroupsOutlinedIcon from "@mui/icons-material/GroupsOutlined";
import HourglassTopIcon from "@mui/icons-material/HourglassTop";
import SearchIcon from "@mui/icons-material/Search";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";
import { StatCard, StatGrid } from "../../components/DashboardKit";
import type { CycleOverview, StaffRow } from "../../api/performance";
import { ReviewStatusChip } from "./ReviewStatusChip";
import {
	distinctValues,
	filterStaff,
	formatDate,
	formatPercent,
	hasFilters,
	NO_FILTERS,
	NOT_SET,
	orNotSet,
	REVIEW_STATUS_LABEL,
	REVIEW_STATUSES,
	type StaffFilters,
} from "./reviewFormat";

export interface OverviewPanelProps {
	overview: CycleOverview | null;
	loading: boolean;
	error: string | null;
	onRetry: () => void;
	canReview: boolean;
	canManage: boolean;
	onOpenMetrics: () => void;
}

const PAGE_SIZES = [10, 25, 50];

/** Summary cards and the staff table for the selected cycle. Filtering is client-side: the whole roster of a cycle arrives in one call. */
export function OverviewPanel({ overview, loading, error, onRetry, canReview, canManage, onOpenMetrics }: OverviewPanelProps) {
	const navigate = useNavigate();
	const [filters, setFilters] = useState<StaffFilters>(NO_FILTERS);
	const [page, setPage] = useState(0);
	const [pageSize, setPageSize] = useState(PAGE_SIZES[0]);

	// A different cycle is a different roster: start from a clean slate.
	const cycleId = overview?.cycle.id;
	useEffect(() => {
		setFilters(NO_FILTERS);
		setPage(0);
	}, [cycleId]);

	const staff = overview?.staff;
	const filtered = useMemo(() => filterStaff(staff ?? [], filters), [staff, filters]);
	const departments = useMemo(() => distinctValues(staff ?? [], (r) => r.department), [staff]);
	const designations = useMemo(() => distinctValues(staff ?? [], (r) => r.designation), [staff]);
	const visible = filtered.slice(page * pageSize, page * pageSize + pageSize);

	function change(patch: Partial<StaffFilters>) {
		setFilters((current) => ({ ...current, ...patch }));
		setPage(0);
	}

	if (error) {
		return (
			<Alert severity="error" action={<Button color="inherit" size="small" onClick={onRetry}>Retry</Button>}>
				{error}
			</Alert>
		);
	}
	if (loading || !overview) {
		return <OverviewSkeleton />;
	}

	const { summary, cycle } = overview;
	const completion = summary.totalStaff === 0 ? null : Math.round((summary.completed / summary.totalStaff) * 100);
	const closed = cycle.status === "CLOSED";

	return (
		<Stack spacing={2.5}>
			{!overview.metricsConfigured && (
				<Alert severity="info" action={canManage ? <Button color="inherit" size="small" onClick={onOpenMetrics}>Choose metrics</Button> : undefined}>
					No metrics are being tracked yet, so staff have no overall score. Ratings and goals still work.
				</Alert>
			)}

			<StatGrid columns={4}>
				<StatCard icon={<GroupsOutlinedIcon fontSize="large" />} tint="violet" label="Total staff" value={summary.totalStaff} chip="in this cycle" />
				<StatCard icon={<HourglassTopIcon fontSize="large" />} tint="orange" label="Pending reviews" value={summary.pending}
					chip={summary.pending === 0 ? "all caught up" : closed ? "closed without a review" : "awaiting a manager rating"} chipTone={summary.pending > 0 ? "warn" : "good"} />
				<StatCard icon={<CheckCircleOutlinedIcon fontSize="large" />} tint="green" label="Completed reviews" value={summary.completed}
					chip={completion === null ? undefined : `${completion}% of staff${closed ? " · finalized" : ""}`} chipTone={completion === 100 ? "good" : "neutral"} />
				<StatCard icon={<TrendingUpIcon fontSize="large" />} tint="blue" label="Average performance"
					value={summary.averageScore === null ? "—" : formatPercent(summary.averageScore)}
					chip={summary.scoredCount === 0 ? "no scored reviews yet" : `${summary.scoredCount} scored review${summary.scoredCount === 1 ? "" : "s"}`} />
			</StatGrid>
			<Typography variant="caption" color="text.secondary">
				Average performance is the mean overall score of completed reviews that have a score. Missing scores are left out, not counted as zero.
			</Typography>

			<Paper variant="outlined">
				<Stack direction={{ xs: "column", md: "row" }} spacing={1.5} sx={{ p: 2, flexWrap: "wrap", alignItems: { md: "center" } }}>
					<TextField size="small" placeholder="Search name or employee ID" value={filters.search} onChange={(e) => change({ search: e.target.value })}
						sx={{ minWidth: { md: 300 } }} slotProps={{ htmlInput: { "aria-label": "Search staff" }, input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
					<TextField select size="small" label="Department" value={filters.department} onChange={(e) => change({ department: e.target.value })} sx={{ minWidth: 170 }}>
						<MenuItem value="">All departments</MenuItem>
						{departments.map((d) => <MenuItem key={d} value={d}>{d === NOT_SET ? "Not set" : d}</MenuItem>)}
					</TextField>
					<TextField select size="small" label="Designation" value={filters.designation} onChange={(e) => change({ designation: e.target.value })} sx={{ minWidth: 170 }}>
						<MenuItem value="">All designations</MenuItem>
						{designations.map((d) => <MenuItem key={d} value={d}>{d === NOT_SET ? "Not set" : d}</MenuItem>)}
					</TextField>
					<TextField select size="small" label="Review status" value={filters.status} onChange={(e) => change({ status: e.target.value as StaffFilters["status"] })} sx={{ minWidth: 160 }}>
						<MenuItem value="">All statuses</MenuItem>
						{REVIEW_STATUSES.map((s) => <MenuItem key={s} value={s}>{REVIEW_STATUS_LABEL[s]}</MenuItem>)}
					</TextField>
					{hasFilters(filters) && <Button size="small" onClick={() => change(NO_FILTERS)}>Clear filters</Button>}
				</Stack>

				<TableContainer>
					<Table size="small" aria-label="Staff performance">
						<TableHead>
							<TableRow>
								<TableCell>Staff</TableCell>
								<TableCell>Designation</TableCell>
								<TableCell>Department</TableCell>
								<TableCell align="right">
									<Tooltip title="Share of recorded working days attended in the review period. Reported separately from the overall score.">
										<span>Attendance</span>
									</Tooltip>
								</TableCell>
								<TableCell align="right">Overall score</TableCell>
								<TableCell>Review status</TableCell>
								<TableCell>Last updated</TableCell>
								<TableCell align="right" />
							</TableRow>
						</TableHead>
						<TableBody>
							{visible.map((row) => (
								<StaffTableRow key={row.personId} row={row} canReview={canReview}
									onOpen={() => navigate(`/performance/staff/${row.personId}?cycle=${cycle.id}`)} />
							))}
							{visible.length === 0 && (
								<TableRow>
									<TableCell colSpan={8}>
										<Stack sx={{ py: 4, alignItems: "center", textAlign: "center" }} spacing={1}>
											{summary.totalStaff === 0 ? (
												<Typography variant="body2" color="text.secondary">
													No staff are in this cycle yet. Teachers appear here once they have an active class assignment.
												</Typography>
											) : (
												<>
													<Typography variant="body2" color="text.secondary">No staff match these filters.</Typography>
													<Button size="small" onClick={() => change(NO_FILTERS)}>Clear filters</Button>
												</>
											)}
										</Stack>
									</TableCell>
								</TableRow>
							)}
						</TableBody>
					</Table>
				</TableContainer>
				<TablePagination component="div" count={filtered.length} page={page} rowsPerPage={pageSize} rowsPerPageOptions={PAGE_SIZES}
					onPageChange={(_e, next) => setPage(next)} onRowsPerPageChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }} />
			</Paper>
			<Typography variant="caption" color="text.secondary">
				Staff are listed alphabetically on purpose: this is a coaching view, not a ranking. A dash means there is no record to calculate from, and is never treated as zero.
			</Typography>
		</Stack>
	);
}

function StaffTableRow({ row, canReview, onOpen }: { row: StaffRow; canReview: boolean; onOpen: () => void }) {
	const reviewNow = canReview && row.reviewStatus === "PENDING";
	return (
		<TableRow hover sx={{ cursor: "pointer", "&:last-child td": { borderBottom: 0 } }} onClick={onOpen}>
			<TableCell>
				<Typography variant="body2" sx={{ fontWeight: 600 }}>{row.name}</Typography>
				<Typography variant="caption" color="text.secondary">{row.employeeCode ?? "No employee ID"}</Typography>
			</TableCell>
			<TableCell>{orNotSet(row.designation)}</TableCell>
			<TableCell>{orNotSet(row.department)}</TableCell>
			<TableCell align="right" sx={{ fontVariantNumeric: "tabular-nums" }}>{formatPercent(row.presencePercent)}</TableCell>
			<TableCell align="right" sx={{ fontVariantNumeric: "tabular-nums" }}>
				{formatPercent(row.overallScore)}
				{row.managerRating !== null && (
					<Typography component="div" variant="caption" color="text.secondary">Manager {row.managerRating.toFixed(1)} / 5</Typography>
				)}
			</TableCell>
			<TableCell><ReviewStatusChip status={row.reviewStatus} /></TableCell>
			<TableCell>{formatDate(row.lastUpdated)}</TableCell>
			<TableCell align="right">
				<Button size="small" variant={reviewNow ? "contained" : "outlined"}
					onClick={(e) => { e.stopPropagation(); onOpen(); }} aria-label={`${reviewNow ? "Review" : "View"} appraisal for ${row.name}`}>
					{reviewNow ? "Review" : "View"}
				</Button>
			</TableCell>
		</TableRow>
	);
}

function OverviewSkeleton() {
	return (
		<Stack spacing={2.5} aria-busy="true" aria-label="Loading performance overview">
			<Box sx={{ display: "grid", gap: 2.5, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", lg: "repeat(4, 1fr)" } }}>
				{[0, 1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={104} />)}
			</Box>
			<Skeleton variant="rounded" height={320} />
		</Stack>
	);
}
