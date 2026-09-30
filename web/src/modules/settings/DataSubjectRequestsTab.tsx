import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { ApiError } from "../../api/client";
import {
	completeDataSubjectRequest,
	getDataSubjectExport,
	listDataSubjectRequests,
	openDataSubjectRequest,
	rejectDataSubjectRequest,
	verifyDataSubjectIdentity,
	type DataSubjectRequestResponse,
	type DataSubjectRequestStatus,
	type DataSubjectRequestType,
} from "../../api/privacy";
import { PersonPicker } from "../../components/PersonPicker";

const TYPES: { value: DataSubjectRequestType; label: string }[] = [
	{ value: "ACCESS", label: "Access - see my data" },
	{ value: "EXPORT", label: "Export - give me a copy" },
	{ value: "CORRECTION", label: "Correction" },
	{ value: "DELETION", label: "Deletion" },
];

const STATUS_LABEL: Record<DataSubjectRequestStatus, string> = {
	RECEIVED: "Received",
	IDENTITY_VERIFIED: "Identity verified",
	COMPLETED: "Completed",
	REJECTED: "Rejected",
};

type Action = { kind: "verify" | "complete" | "reject"; request: DataSubjectRequestResponse };

const ACTION_COPY: Record<Action["kind"], { title: string; field: string; help: string }> = {
	verify: { title: "Verify identity", field: "How was identity verified?", help: "e.g. Photo ID sighted at the front desk" },
	complete: { title: "Complete request", field: "Resolution note", help: "What was done for the requester" },
	reject: { title: "Reject request", field: "Reason", help: "Why the request cannot be actioned" },
};

/** Working list of data-subject requests. Identity must be verified before a request can be
 * completed or its data released - the buttons mirror the backend's one-way state machine. */
export function DataSubjectRequestsTab() {
	const [requests, setRequests] = useState<DataSubjectRequestResponse[]>([]);
	const [statusFilter, setStatusFilter] = useState<DataSubjectRequestStatus | "">("");
	const [error, setError] = useState<string | null>(null);
	const [newOpen, setNewOpen] = useState(false);
	const [action, setAction] = useState<Action | null>(null);

	function refresh() {
		listDataSubjectRequests(statusFilter || undefined)
			.then(setRequests)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load requests"));
	}

	useEffect(refresh, [statusFilter]);

	async function handleExport(request: DataSubjectRequestResponse) {
		setError(null);
		try {
			const data = await getDataSubjectExport(request.id);
			const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
			const link = document.createElement("a");
			link.href = url;
			link.download = `data-subject-request-${request.id}.json`;
			link.click();
			URL.revokeObjectURL(url);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to export data");
		}
	}

	return (
		<Stack spacing={2}>
			<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 2 }}>
				<TextField
					select
					size="small"
					label="Status"
					value={statusFilter}
					onChange={(e) => setStatusFilter(e.target.value as DataSubjectRequestStatus | "")}
					sx={{ minWidth: 200 }}
				>
					<MenuItem value="">All</MenuItem>
					{(Object.keys(STATUS_LABEL) as DataSubjectRequestStatus[]).map((status) => (
						<MenuItem key={status} value={status}>
							{STATUS_LABEL[status]}
						</MenuItem>
					))}
				</TextField>
				<Button size="small" startIcon={<AddIcon />} onClick={() => setNewOpen(true)}>
					Log request
				</Button>
			</Box>
			{error && <Alert severity="error">{error}</Alert>}

			<Table size="small">
				<TableHead>
					<TableRow>
						<TableCell>Person</TableCell>
						<TableCell>Type</TableCell>
						<TableCell>Received</TableCell>
						<TableCell>Due</TableCell>
						<TableCell>Status</TableCell>
						<TableCell />
					</TableRow>
				</TableHead>
				<TableBody>
					{requests.map((request) => (
						<TableRow key={request.id}>
							<TableCell>
								{request.personName}
								{request.description && (
									<Typography variant="caption" color="text.secondary" component="div">
										{request.description}
									</Typography>
								)}
							</TableCell>
							<TableCell>{request.requestType}</TableCell>
							<TableCell>{request.receivedDate}</TableCell>
							<TableCell>
								{request.dueDate} {request.overdue && <Chip label="Overdue" size="small" color="error" />}
							</TableCell>
							<TableCell>
								<Chip
									label={STATUS_LABEL[request.status]}
									size="small"
									color={request.status === "COMPLETED" ? "success" : request.status === "REJECTED" ? "default" : "warning"}
								/>
								{request.resolutionNote && (
									<Typography variant="caption" color="text.secondary" component="div">
										{request.resolutionNote}
									</Typography>
								)}
							</TableCell>
							<TableCell align="right">
								{request.status === "RECEIVED" && (
									<Button size="small" onClick={() => setAction({ kind: "verify", request })}>
										Verify identity
									</Button>
								)}
								{request.status === "IDENTITY_VERIFIED" && (
									<Button size="small" onClick={() => setAction({ kind: "complete", request })}>
										Complete
									</Button>
								)}
								{(request.status === "IDENTITY_VERIFIED" || request.status === "COMPLETED") &&
									(request.requestType === "ACCESS" || request.requestType === "EXPORT") && (
										<Button size="small" onClick={() => handleExport(request)}>
											Download data
										</Button>
									)}
								{(request.status === "RECEIVED" || request.status === "IDENTITY_VERIFIED") && (
									<Button size="small" color="error" onClick={() => setAction({ kind: "reject", request })}>
										Reject
									</Button>
								)}
							</TableCell>
						</TableRow>
					))}
					{requests.length === 0 && (
						<TableRow>
							<TableCell colSpan={6}>
								<Typography variant="body2" color="text.secondary">
									No requests logged.
								</Typography>
							</TableCell>
						</TableRow>
					)}
				</TableBody>
			</Table>

			{newOpen && (
				<NewRequestDialog
					onClose={() => setNewOpen(false)}
					onCreated={() => {
						setNewOpen(false);
						refresh();
					}}
				/>
			)}
			{action && (
				<ActionDialog
					action={action}
					onClose={() => setAction(null)}
					onDone={() => {
						setAction(null);
						refresh();
					}}
				/>
			)}
		</Stack>
	);
}

function NewRequestDialog({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
	const [personId, setPersonId] = useState<number | "">("");
	const [requestType, setRequestType] = useState<DataSubjectRequestType>("ACCESS");
	const [description, setDescription] = useState("");
	const [receivedDate, setReceivedDate] = useState("");
	const [dueDate, setDueDate] = useState("");
	const [error, setError] = useState<string | null>(null);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		if (personId === "") return;
		try {
			await openDataSubjectRequest({
				personId,
				requestType,
				description: description.trim() || null,
				receivedDate: receivedDate || null,
				dueDate: dueDate || null,
			});
			onCreated();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to log request");
		}
	}

	return (
		<Dialog open onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
			<DialogTitle>Log data request</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<PersonPicker value={personId} onChange={setPersonId} label="Requested by" required fullWidth />
					<TextField select label="Type" value={requestType} onChange={(e) => setRequestType(e.target.value as DataSubjectRequestType)} fullWidth>
						{TYPES.map((type) => (
							<MenuItem key={type.value} value={type.value}>
								{type.label}
							</MenuItem>
						))}
					</TextField>
					<TextField label="Details" value={description} onChange={(e) => setDescription(e.target.value)} multiline fullWidth />
					<TextField label="Received on" type="date" value={receivedDate} onChange={(e) => setReceivedDate(e.target.value)} helperText="Defaults to today" slotProps={{ inputLabel: { shrink: true } }} fullWidth />
					<TextField label="Respond by" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} helperText="Defaults to 30 days after receipt" slotProps={{ inputLabel: { shrink: true } }} fullWidth />
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" disabled={personId === ""}>
					Log request
				</Button>
			</DialogActions>
		</Dialog>
	);
}

function ActionDialog({ action, onClose, onDone }: { action: Action; onClose: () => void; onDone: () => void }) {
	const copy = ACTION_COPY[action.kind];
	const [text, setText] = useState("");
	const [error, setError] = useState<string | null>(null);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		try {
			if (action.kind === "verify") await verifyDataSubjectIdentity(action.request.id, text);
			else if (action.kind === "complete") await completeDataSubjectRequest(action.request.id, text);
			else await rejectDataSubjectRequest(action.request.id, text);
			onDone();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update request");
		}
	}

	return (
		<Dialog open onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
			<DialogTitle>
				{copy.title} - {action.request.personName}
			</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					{action.kind === "complete" && action.request.requestType === "DELETION" && (
						<Alert severity="info">
							Completing a deletion request records that it was handled - it does not erase any data by itself.
						</Alert>
					)}
					<TextField label={copy.field} helperText={copy.help} value={text} onChange={(e) => setText(e.target.value)} required autoFocus multiline fullWidth />
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" color={action.kind === "reject" ? "error" : "primary"}>
					{copy.title}
				</Button>
			</DialogActions>
		</Dialog>
	);
}
