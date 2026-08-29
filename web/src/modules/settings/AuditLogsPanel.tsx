import { useEffect, useMemo, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
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
import { getAuditLogEntityTypes, getAuditLogs, type AuditLogResponse } from "../../api/auditLogs";
import { ApiError } from "../../api/client";
import { listUsers, type UserResponse } from "../../api/users";

const PAGE_SIZE = 25;

function formatOccurredAt(iso: string): string {
	return new Date(iso).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
}

/** Read-only viewer over the shared audit ledger (#145) - the write side
 * (AuditLogService.record) is already called from every module; this is purely the
 * read side, filterable by entity type / actor / date range. */
export function AuditLogsPanel() {
	const [rows, setRows] = useState<AuditLogResponse[]>([]);
	const [totalElements, setTotalElements] = useState(0);
	const [page, setPage] = useState(0);
	const [entityTypes, setEntityTypes] = useState<string[]>([]);
	const [users, setUsers] = useState<UserResponse[]>([]);
	const [entityType, setEntityType] = useState("");
	const [actorUserId, setActorUserId] = useState("");
	const [from, setFrom] = useState("");
	const [to, setTo] = useState("");
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getAuditLogEntityTypes().catch(() => []).then((result) => setEntityTypes(result ?? []));
		listUsers().catch(() => []).then((result) => setUsers(result ?? []));
	}, []);

	useEffect(() => {
		getAuditLogs({
			entityType: entityType || null,
			actorUserId: actorUserId ? Number(actorUserId) : null,
			from: from ? new Date(from).toISOString() : null,
			to: to ? new Date(to).toISOString() : null,
			page,
			size: PAGE_SIZE,
		})
			.then((result) => {
				setRows(result.content);
				setTotalElements(result.totalElements);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load audit logs"));
	}, [entityType, actorUserId, from, to, page]);

	const actorEmailById = useMemo(() => new Map(users.map((user) => [user.id, user.email])), [users]);

	function clearFilters() {
		setEntityType("");
		setActorUserId("");
		setFrom("");
		setTo("");
		setPage(0);
	}

	const hasFilters = entityType !== "" || actorUserId !== "" || from !== "" || to !== "";

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Audit logs</Typography>
				<Typography variant="body2" color="text.secondary">
					Who changed roles, fee records, marks, student data, and settings - read-only.
				</Typography>

				{error && <Alert severity="error">{error}</Alert>}

				<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
					<TextField
						select
						label="Entity type"
						size="small"
						value={entityType}
						onChange={(e) => {
							setEntityType(e.target.value);
							setPage(0);
						}}
						sx={{ minWidth: 180 }}
					>
						<MenuItem value="">All</MenuItem>
						{entityTypes.map((type) => (
							<MenuItem key={type} value={type}>
								{type}
							</MenuItem>
						))}
					</TextField>
					<TextField
						select
						label="Actor"
						size="small"
						value={actorUserId}
						onChange={(e) => {
							setActorUserId(e.target.value);
							setPage(0);
						}}
						sx={{ minWidth: 200 }}
					>
						<MenuItem value="">All</MenuItem>
						{users.map((user) => (
							<MenuItem key={user.id} value={String(user.id)}>
								{user.email}
							</MenuItem>
						))}
					</TextField>
					<TextField
						label="From"
						type="date"
						size="small"
						value={from}
						onChange={(e) => {
							setFrom(e.target.value);
							setPage(0);
						}}
						slotProps={{ inputLabel: { shrink: true } }}
					/>
					<TextField
						label="To"
						type="date"
						size="small"
						value={to}
						onChange={(e) => {
							setTo(e.target.value);
							setPage(0);
						}}
						slotProps={{ inputLabel: { shrink: true } }}
					/>
					{hasFilters && (
						<Button size="small" onClick={clearFilters} sx={{ alignSelf: "center" }}>
							Clear filters
						</Button>
					)}
				</Stack>

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>When</TableCell>
								<TableCell>Action</TableCell>
								<TableCell>Entity</TableCell>
								<TableCell>Actor</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{rows.map((log) => (
								<TableRow key={log.id}>
									<TableCell>{formatOccurredAt(log.occurredAt)}</TableCell>
									<TableCell>{log.action}</TableCell>
									<TableCell>
										{log.entityType} #{log.entityId}
									</TableCell>
									<TableCell>{log.actorUserId != null ? (actorEmailById.get(log.actorUserId) ?? `User #${log.actorUserId}`) : "System"}</TableCell>
								</TableRow>
							))}
							{rows.length === 0 && (
								<TableRow>
									<TableCell colSpan={4}>
										<Box sx={{ py: 3, textAlign: "center" }}>
											<Typography variant="body2" color="text.secondary">
												No audit log entries match these filters.
											</Typography>
										</Box>
									</TableCell>
								</TableRow>
							)}
						</TableBody>
					</Table>
				</TableContainer>

				<TablePagination
					component="div"
					count={totalElements}
					page={page}
					onPageChange={(_, newPage) => setPage(newPage)}
					rowsPerPage={PAGE_SIZE}
					rowsPerPageOptions={[PAGE_SIZE]}
				/>
			</Stack>
		</Paper>
	);
}
