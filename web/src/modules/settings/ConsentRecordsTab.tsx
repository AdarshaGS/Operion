import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
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
import { ApiError } from "../../api/client";
import {
	getConsentHistory,
	getCurrentConsents,
	recordConsent,
	type ConsentRecordResponse,
	type ConsentSource,
	type ConsentState,
	type CurrentConsentResponse,
} from "../../api/privacy";
import { PersonPicker } from "../../components/PersonPicker";

const SOURCES: { value: ConsentSource; label: string }[] = [
	{ value: "PAPER_FORM", label: "Paper form" },
	{ value: "VERBAL", label: "Verbal" },
	{ value: "ADMIN_ENTRY", label: "Entered by staff" },
];

const STATE_CHIP: Record<ConsentState, { label: string; color: "success" | "warning" | "default" }> = {
	GRANTED: { label: "Granted", color: "success" },
	WITHDRAWN: { label: "Withdrawn", color: "warning" },
	NOT_RECORDED: { label: "Not recorded", color: "default" },
};

/** Per-person consent: current standing per purpose, the action to record a new decision, and the
 * full insert-only history underneath. */
export function ConsentRecordsTab() {
	const [personId, setPersonId] = useState<number | "">("");
	const [current, setCurrent] = useState<CurrentConsentResponse[]>([]);
	const [history, setHistory] = useState<ConsentRecordResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [recording, setRecording] = useState<{ purpose: CurrentConsentResponse; granted: boolean } | null>(null);

	function refresh(id: number) {
		Promise.all([getCurrentConsents(id), getConsentHistory(id)])
			.then(([cur, hist]) => {
				setCurrent(cur);
				setHistory(hist);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load consent records"));
	}

	useEffect(() => {
		setError(null);
		if (personId === "") {
			setCurrent([]);
			setHistory([]);
			return;
		}
		refresh(personId);
	}, [personId]);

	return (
		<Stack spacing={2}>
			<PersonPicker value={personId} onChange={setPersonId} label="Person" fullWidth />
			{error && <Alert severity="error">{error}</Alert>}

			{personId !== "" && (
				<>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Purpose</TableCell>
								<TableCell>Status</TableCell>
								<TableCell>Since</TableCell>
								<TableCell />
							</TableRow>
						</TableHead>
						<TableBody>
							{current.map((item) => (
								<TableRow key={item.purposeId}>
									<TableCell>{item.purposeName}</TableCell>
									<TableCell>
										<Chip label={STATE_CHIP[item.state].label} size="small" color={STATE_CHIP[item.state].color} />
									</TableCell>
									<TableCell>{item.since ? new Date(item.since).toLocaleString() : "-"}</TableCell>
									<TableCell align="right">
										<Button size="small" disabled={item.state === "GRANTED"} onClick={() => setRecording({ purpose: item, granted: true })}>
											Record consent
										</Button>
										<Button size="small" disabled={item.state !== "GRANTED"} onClick={() => setRecording({ purpose: item, granted: false })}>
											Record withdrawal
										</Button>
									</TableCell>
								</TableRow>
							))}
							{current.length === 0 && (
								<TableRow>
									<TableCell colSpan={4}>
										<Typography variant="body2" color="text.secondary">
											No active consent purposes - add one under the Purposes tab.
										</Typography>
									</TableCell>
								</TableRow>
							)}
						</TableBody>
					</Table>

					<Typography variant="subtitle2">History</Typography>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>When</TableCell>
								<TableCell>Purpose</TableCell>
								<TableCell>Decision</TableCell>
								<TableCell>Source</TableCell>
								<TableCell>Note</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{history.map((record) => (
								<TableRow key={record.id}>
									<TableCell>{new Date(record.recordedAt).toLocaleString()}</TableCell>
									<TableCell>{record.purposeName}</TableCell>
									<TableCell>{record.granted ? "Granted" : "Withdrawn"}</TableCell>
									<TableCell>{SOURCES.find((s) => s.value === record.source)?.label ?? record.source}</TableCell>
									<TableCell>{record.note}</TableCell>
								</TableRow>
							))}
							{history.length === 0 && (
								<TableRow>
									<TableCell colSpan={5}>
										<Typography variant="body2" color="text.secondary">
											Nothing recorded for this person yet.
										</Typography>
									</TableCell>
								</TableRow>
							)}
						</TableBody>
					</Table>
				</>
			)}

			{recording && personId !== "" && (
				<RecordDialog
					personId={personId}
					purpose={recording.purpose}
					granted={recording.granted}
					onClose={() => setRecording(null)}
					onRecorded={() => {
						setRecording(null);
						refresh(personId);
					}}
				/>
			)}
		</Stack>
	);
}

function RecordDialog({
	personId,
	purpose,
	granted,
	onClose,
	onRecorded,
}: {
	personId: number;
	purpose: CurrentConsentResponse;
	granted: boolean;
	onClose: () => void;
	onRecorded: () => void;
}) {
	const [source, setSource] = useState<ConsentSource>("PAPER_FORM");
	const [note, setNote] = useState("");
	const [error, setError] = useState<string | null>(null);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		try {
			await recordConsent({ personId, purposeId: purpose.purposeId, granted, source, note: note.trim() || null });
			onRecorded();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to record");
		}
	}

	return (
		<Dialog open onClose={onClose} component="form" onSubmit={handleSubmit} fullWidth maxWidth="xs">
			<DialogTitle>
				{granted ? "Record consent" : "Record withdrawal"} - {purpose.purposeName}
			</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{error && <Alert severity="error">{error}</Alert>}
					<TextField select label="How was it given?" value={source} onChange={(e) => setSource(e.target.value as ConsentSource)} fullWidth>
						{SOURCES.map((s) => (
							<MenuItem key={s.value} value={s.value}>
								{s.label}
							</MenuItem>
						))}
					</TextField>
					<TextField label="Note" helperText="e.g. form reference" value={note} onChange={(e) => setNote(e.target.value)} multiline fullWidth />
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button type="submit" variant="contained">
					Record
				</Button>
			</DialogActions>
		</Dialog>
	);
}
