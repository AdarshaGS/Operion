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
import Paper from "@mui/material/Paper";
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
import { listRegulatoryExportSchemas, type RegulatoryExportSchemaResponse } from "../../api/regulatoryExports";
import {
	cancelTransferConnectorJob,
	failTransferConnectorJob,
	listStudentExits,
	listTransferConnectorJobs,
	prepareTransferConnectorJob,
	reprepareTransferConnectorJob,
	submitTransferConnectorJob,
	type StudentExitResponse,
	type TransferConnectorJobResponse,
	type TransferConnectorJobStatus,
} from "../../api/transferConnectorJobs";
import { StudentPicker } from "../../components/StudentPicker";

const STATUS_COLOR: Record<TransferConnectorJobStatus, "default" | "warning" | "success" | "error"> = {
	PREPARED: "warning",
	SUBMITTED: "success",
	FAILED: "error",
	CANCELLED: "default",
};

type Prompt = { kind: "submit" | "fail"; job: TransferConnectorJobResponse };

function message(err: unknown, fallback: string): string {
	return err instanceof ApiError ? err.message : fallback;
}

/** Hand-off jobs for a leaving student's transfer record (#235). Operion prepares the payload from
 * a student export layout and tracks the outcome; a person sends it to the receiving system and
 * records the result here - no external system is called. */
export function TransferConnectorsPanel() {
	const [jobs, setJobs] = useState<TransferConnectorJobResponse[]>([]);
	const [statusFilter, setStatusFilter] = useState<TransferConnectorJobStatus | "">("");
	const [error, setError] = useState<string | null>(null);
	const [prepareOpen, setPrepareOpen] = useState(false);
	const [viewing, setViewing] = useState<TransferConnectorJobResponse | null>(null);
	const [prompt, setPrompt] = useState<Prompt | null>(null);

	function refresh() {
		listTransferConnectorJobs(statusFilter || undefined)
			.then(setJobs)
			.catch((err) => setError(message(err, "Failed to load connector jobs")));
	}

	useEffect(refresh, [statusFilter]);

	async function run(action: () => Promise<unknown>, fallback: string) {
		setError(null);
		try {
			await action();
			refresh();
		} catch (err) {
			setError(message(err, fallback));
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 2 }}>
					<Typography variant="h6">Transfer hand-off jobs</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => setPrepareOpen(true)}>
						Prepare job
					</Button>
				</Box>
				<Typography variant="body2" color="text.secondary">
					For a student recorded as transferred out, prepare the record an external system needs, then note here
					whether it was submitted. Needs an active student export layout under Regulatory exports.
				</Typography>
				<TextField
					select
					size="small"
					label="Status"
					value={statusFilter}
					onChange={(e) => setStatusFilter(e.target.value as TransferConnectorJobStatus | "")}
					sx={{ maxWidth: 200 }}
				>
					<MenuItem value="">All</MenuItem>
					{(Object.keys(STATUS_COLOR) as TransferConnectorJobStatus[]).map((status) => (
						<MenuItem key={status} value={status}>
							{status}
						</MenuItem>
					))}
				</TextField>
				{error && <Alert severity="error">{error}</Alert>}

				<Table size="small">
					<TableHead>
						<TableRow>
							<TableCell>Student</TableCell>
							<TableCell>Connector</TableCell>
							<TableCell>Layout</TableCell>
							<TableCell>Status</TableCell>
							<TableCell />
						</TableRow>
					</TableHead>
					<TableBody>
						{jobs.map((job) => (
							<TableRow key={job.id}>
								<TableCell>{job.studentName}</TableCell>
								<TableCell>{job.connectorName}</TableCell>
								<TableCell>
									<code>{job.schemaCode}</code>
								</TableCell>
								<TableCell>
									<Chip label={job.status} size="small" color={STATUS_COLOR[job.status]} />
									{job.externalReference && (
										<Typography variant="caption" color="text.secondary" component="div">
											Ref: {job.externalReference}
										</Typography>
									)}
									{job.failureReason && (
										<Typography variant="caption" color="error" component="div">
											{job.failureReason}
										</Typography>
									)}
								</TableCell>
								<TableCell align="right">
									<Button size="small" onClick={() => setViewing(job)}>
										View payload
									</Button>
									{job.status === "PREPARED" && (
										<>
											<Button size="small" onClick={() => setPrompt({ kind: "submit", job })}>
												Mark submitted
											</Button>
											<Button size="small" onClick={() => setPrompt({ kind: "fail", job })}>
												Mark failed
											</Button>
										</>
									)}
									{job.status === "FAILED" && (
										<Button size="small" onClick={() => run(() => reprepareTransferConnectorJob(job.id), "Failed to re-prepare")}>
											Re-prepare
										</Button>
									)}
									{(job.status === "PREPARED" || job.status === "FAILED") && (
										<Button size="small" color="error" onClick={() => run(() => cancelTransferConnectorJob(job.id), "Failed to cancel")}>
											Cancel
										</Button>
									)}
								</TableCell>
							</TableRow>
						))}
						{jobs.length === 0 && (
							<TableRow>
								<TableCell colSpan={5}>
									<Typography variant="body2" color="text.secondary">
										No hand-off jobs yet.
									</Typography>
								</TableCell>
							</TableRow>
						)}
					</TableBody>
				</Table>
			</Stack>

			{prepareOpen && (
				<PrepareDialog
					onClose={() => setPrepareOpen(false)}
					onPrepared={() => {
						setPrepareOpen(false);
						refresh();
					}}
				/>
			)}
			{viewing && (
				<Dialog open onClose={() => setViewing(null)} fullWidth maxWidth="sm">
					<DialogTitle>Payload - {viewing.studentName}</DialogTitle>
					<DialogContent>
						<Box component="pre" sx={{ m: 0, whiteSpace: "pre-wrap", fontSize: 13 }}>
							{JSON.stringify(JSON.parse(viewing.payload), null, 2)}
						</Box>
					</DialogContent>
					<DialogActions>
						<Button onClick={() => setViewing(null)}>Close</Button>
					</DialogActions>
				</Dialog>
			)}
			{prompt && (
				<PromptDialog
					prompt={prompt}
					onClose={() => setPrompt(null)}
					onDone={() => {
						setPrompt(null);
						refresh();
					}}
				/>
			)}
		</Paper>
	);
}

function PrepareDialog({ onClose, onPrepared }: { onClose: () => void; onPrepared: () => void }) {
	const [studentId, setStudentId] = useState<number | "">("");
	const [exits, setExits] = useState<StudentExitResponse[]>([]);
	const [exitId, setExitId] = useState<number | "">("");
	const [schemas, setSchemas] = useState<RegulatoryExportSchemaResponse[]>([]);
	const [schemaId, setSchemaId] = useState<number | "">("");
	const [connectorName, setConnectorName] = useState("");
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		listRegulatoryExportSchemas()
			.then((all) => setSchemas(all.filter((s) => s.entityType === "STUDENT" && s.status === "ACTIVE" && s.fields.length > 0)))
			.catch((err) => setError(message(err, "Failed to load export layouts")));
	}, []);

	useEffect(() => {
		setExitId("");
		setExits([]);
		if (studentId === "") return;
		listStudentExits(studentId)
			.then((all) => setExits(all.filter((e) => e.exitType === "TRANSFER")))
			.catch((err) => setError(message(err, "Failed to load the student's exits")));
	}, [studentId]);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		if (exitId === "" || schemaId === "") return;
		try {
			await prepareTransferConnectorJob({ studentExitId: exitId, schemaId, connectorName: connectorName.trim() });
			onPrepared();
		} catch (err) {
			setError(message(err, "Failed to prepare job"));
		}
	}

	return (
		<Dialog open onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
			<DialogTitle>Prepare hand-off job</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<StudentPicker value={studentId} onChange={(id) => setStudentId(id)} required fullWidth />
					<TextField
						select
						label="Transfer"
						value={exitId}
						onChange={(e) => setExitId(Number(e.target.value))}
						disabled={studentId === ""}
						helperText={studentId !== "" && exits.length === 0 ? "This student has no recorded transfer exit" : undefined}
						required
						fullWidth
					>
						{exits.map((exit) => (
							<MenuItem key={exit.id} value={exit.id}>
								{exit.exitDate}
								{exit.destinationSchool ? ` - ${exit.destinationSchool}` : ""}
							</MenuItem>
						))}
					</TextField>
					<TextField
						select
						label="Export layout"
						value={schemaId}
						onChange={(e) => setSchemaId(Number(e.target.value))}
						helperText={schemas.length === 0 ? "No active student layout with columns yet" : "Decides which fields go in the payload"}
						required
						fullWidth
					>
						{schemas.map((schema) => (
							<MenuItem key={schema.id} value={schema.id}>
								{schema.name}
							</MenuItem>
						))}
					</TextField>
					<TextField
						label="Connector"
						helperText="The external system this goes to, e.g. its name"
						value={connectorName}
						onChange={(e) => setConnectorName(e.target.value)}
						required
						fullWidth
					/>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" disabled={exitId === "" || schemaId === ""}>
					Prepare
				</Button>
			</DialogActions>
		</Dialog>
	);
}

function PromptDialog({ prompt, onClose, onDone }: { prompt: Prompt; onClose: () => void; onDone: () => void }) {
	const isSubmit = prompt.kind === "submit";
	const [text, setText] = useState("");
	const [error, setError] = useState<string | null>(null);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		try {
			if (isSubmit) await submitTransferConnectorJob(prompt.job.id, text.trim() || null);
			else await failTransferConnectorJob(prompt.job.id, text);
			onDone();
		} catch (err) {
			setError(message(err, "Failed to update job"));
		}
	}

	return (
		<Dialog open onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
			<DialogTitle>
				{isSubmit ? "Mark submitted" : "Mark failed"} - {prompt.job.studentName}
			</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField
						label={isSubmit ? "Reference from the receiving system (optional)" : "What went wrong?"}
						value={text}
						onChange={(e) => setText(e.target.value)}
						required={!isSubmit}
						autoFocus
						multiline
						fullWidth
					/>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained" color={isSubmit ? "primary" : "error"}>
					{isSubmit ? "Mark submitted" : "Mark failed"}
				</Button>
			</DialogActions>
		</Dialog>
	);
}
