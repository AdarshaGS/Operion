import { useCallback, useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import {
	assignSubstitution,
	listCoverSuggestions,
	listUncoveredSlots,
	type CoverSuggestionResponse,
	type SubstitutionCandidate,
	type UncoveredSlotResponse,
} from "../../api/timetable";
import { useCan } from "../../auth/useCan";
import { useSelectedCampusId } from "../../layout/selectedCampus";
import { colors } from "../../theme";
import { dayLongLabel, timeLabel } from "./timetableViewShared";

/** The server's limits on the two reads: the uncovered list covers at most 62 days, suggestions at most 14. */
const MAX_LIST_DAYS = 62;
const MAX_SUGGEST_DAYS = 14;

function isoDate(offsetDays: number): string {
	const date = new Date();
	date.setDate(date.getDate() + offsetDays);
	return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

/** Whole days from `from` to `to` (0 when they are the same day); NaN when either is not a date. */
function daysBetween(from: string, to: string): number {
	const start = Date.parse(`${from}T00:00:00Z`);
	const end = Date.parse(`${to}T00:00:00Z`);
	return Math.round((end - start) / 86_400_000);
}

const slotKey = (entryId: number, date: string) => `${entryId}:${date}`;

interface RowResult {
	ok: boolean;
	message: string;
}

/** Slots that approved leave leaves without their teacher in a date range, with the cover already arranged. "Auto-suggest
 * cover" asks the server for a proposal per uncovered slot - a read-only call - and opens a preview; nothing is assigned
 * until Confirm, which assigns the kept rows one by one so a failure on one never blocks the rest. */
export function UncoveredPeriodsPanel() {
	const canManage = useCan("TIMETABLE_MANAGE");
	const campusId = useSelectedCampusId();

	const [from, setFrom] = useState(isoDate(0));
	const [to, setTo] = useState(isoDate(1));
	const [uncoveredOnly, setUncoveredOnly] = useState(false);
	const [slots, setSlots] = useState<UncoveredSlotResponse[] | null>(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const [suggesting, setSuggesting] = useState(false);
	const [suggestError, setSuggestError] = useState<string | null>(null);
	const [suggestions, setSuggestions] = useState<CoverSuggestionResponse[] | null>(null);

	const span = daysBetween(from, to);
	const rangeInvalid = !from || !to || Number.isNaN(span) || span < 0;
	const tooLongToList = !rangeInvalid && span + 1 > MAX_LIST_DAYS;
	const tooLongToSuggest = !rangeInvalid && span + 1 > MAX_SUGGEST_DAYS;

	const load = useCallback(() => {
		if (rangeInvalid || tooLongToList) {
			setSlots(null);
			return;
		}
		setLoading(true);
		setError(null);
		listUncoveredSlots({ from, to, campusId, uncoveredOnly })
			.then(setSlots)
			.catch((err) => {
				setSlots(null);
				setError(err instanceof ApiError ? err.message : "Failed to load the uncovered periods");
			})
			.finally(() => setLoading(false));
	}, [from, to, campusId, uncoveredOnly, rangeInvalid, tooLongToList]);

	useEffect(load, [load]);

	async function handleSuggest() {
		setSuggesting(true);
		setSuggestError(null);
		try {
			setSuggestions(await listCoverSuggestions({ from, to, campusId }));
		} catch (err) {
			setSuggestError(err instanceof ApiError ? err.message : "Failed to get cover suggestions");
		} finally {
			setSuggesting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Uncovered periods</Typography>
				<Typography variant="body2" sx={{ color: colors.inkSoft }}>
					Periods whose teacher is on approved leave. A substitution covers one date only - the weekly timetable is never changed.
				</Typography>

				<Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ alignItems: { md: "center" } }}>
					<TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
					<TextField label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
					<FormControlLabel
						control={<Switch checked={uncoveredOnly} onChange={(e) => setUncoveredOnly(e.target.checked)} />}
						label="Uncovered only"
					/>
					{canManage && (
						<Button variant="contained" onClick={handleSuggest} disabled={suggesting || rangeInvalid || tooLongToSuggest}>
							{suggesting ? "Finding cover..." : "Auto-suggest cover"}
						</Button>
					)}
				</Stack>

				{rangeInvalid && <Alert severity="warning">Pick a date range that ends on or after its start.</Alert>}
				{tooLongToList && <Alert severity="warning">Pick a range of at most {MAX_LIST_DAYS} days to list the periods.</Alert>}
				{canManage && tooLongToSuggest && (
					<Typography variant="caption" sx={{ color: colors.inkSoft }}>
						Auto-suggest works on at most {MAX_SUGGEST_DAYS} days - narrow the range to use it.
					</Typography>
				)}
				{suggestError && <Alert severity="error">{suggestError}</Alert>}
				{error && <Alert severity="error">{error}</Alert>}
				{loading && <CircularProgress size={20} aria-label="Loading uncovered periods" />}

				{!loading && !error && slots !== null && slots.length === 0 && (
					<Alert severity="info">{uncoveredOnly ? "Nothing is left uncovered in this range." : "No periods are affected by approved leave in this range."}</Alert>
				)}

				{slots !== null && slots.length > 0 && (
					<TableContainer>
						<Table size="small" aria-label="Uncovered periods">
							<TableHead>
								<TableRow>
									<TableCell>Date</TableCell>
									<TableCell>Period</TableCell>
									<TableCell>Class</TableCell>
									<TableCell>Subject</TableCell>
									<TableCell>Absent teacher</TableCell>
									<TableCell>Cover</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{slots.map((slot) => (
									<TableRow key={slotKey(slot.entryId, slot.date)} sx={slot.substitution ? undefined : { backgroundColor: colors.badBg }}>
										<TableCell>
											{dayLongLabel(slot.dayOfWeek)} {slot.date}
										</TableCell>
										<TableCell>
											{slot.periodLabel} ({timeLabel(slot.startTime)}–{timeLabel(slot.endTime)})
										</TableCell>
										<TableCell>
											{slot.className} {slot.sectionName}
										</TableCell>
										<TableCell>{slot.subjectName}</TableCell>
										<TableCell>{slot.absentTeacherName}</TableCell>
										<TableCell>
											{slot.substitution ? (
												<Typography variant="body2">Covered by {slot.substitution.substituteTeacherName}</Typography>
											) : (
												<Typography variant="body2" sx={{ color: colors.bad, fontWeight: 600 }}>
													Uncovered
												</Typography>
											)}
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>

			{suggestions !== null && (
				<SuggestionsDialog
					suggestions={suggestions}
					slots={slots ?? []}
					onClose={() => setSuggestions(null)}
					onAssigned={load}
				/>
			)}
		</Paper>
	);
}

/** The preview of proposed cover. Holds the user's choices locally; the only call that writes is the assign POST in Confirm. */
export function SuggestionsDialog({
	suggestions,
	slots,
	onClose,
	onAssigned,
}: {
	suggestions: CoverSuggestionResponse[];
	slots: UncoveredSlotResponse[];
	onClose: () => void;
	onAssigned: () => void;
}) {
	// Who can be picked per row: the proposal first, then the slot's other candidates when the list already has them.
	function optionsFor(suggestion: CoverSuggestionResponse): SubstitutionCandidate[] {
		const slot = slots.find((s) => s.entryId === suggestion.entryId && s.date === suggestion.date);
		const options = suggestion.proposed ? [suggestion.proposed] : [];
		for (const candidate of slot?.candidates ?? []) {
			if (!options.some((o) => o.personId === candidate.personId)) options.push(candidate);
		}
		return options;
	}

	const [choice, setChoice] = useState<Record<string, string>>(() =>
		Object.fromEntries(suggestions.map((s) => [slotKey(s.entryId, s.date), s.proposed ? String(s.proposed.personId) : ""])),
	);
	const [results, setResults] = useState<Record<string, RowResult>>({});
	const [confirming, setConfirming] = useState(false);

	const pending = suggestions.filter((s) => choice[slotKey(s.entryId, s.date)] && !results[slotKey(s.entryId, s.date)]?.ok);
	const finished = Object.keys(results).length > 0 && pending.length === 0;

	async function handleConfirm() {
		setConfirming(true);
		// One POST per kept row, in order; a failure is recorded against its row and the loop carries on.
		for (const suggestion of pending) {
			const key = slotKey(suggestion.entryId, suggestion.date);
			try {
				await assignSubstitution({
					entryId: suggestion.entryId,
					date: suggestion.date,
					substituteTeacherPersonId: Number(choice[key]),
					reason: "Auto-suggested cover for approved leave",
				});
				setResults((r) => ({ ...r, [key]: { ok: true, message: "Assigned" } }));
			} catch (err) {
				setResults((r) => ({ ...r, [key]: { ok: false, message: err instanceof ApiError ? err.message : "Failed to assign cover" } }));
			}
		}
		setConfirming(false);
		onAssigned();
	}

	return (
		<Dialog open onClose={confirming ? undefined : onClose} fullWidth maxWidth="md" aria-labelledby="suggest-cover-title">
			<DialogTitle id="suggest-cover-title">Suggested cover</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ pt: 1 }}>
					<Typography variant="body2" sx={{ color: colors.inkSoft }}>
						Nothing is saved until you confirm. Change a teacher, or choose Skip to leave a period uncovered.
					</Typography>
					{suggestions.length === 0 && <Alert severity="info">No uncovered periods to suggest cover for in this range.</Alert>}
					{suggestions.length > 0 && (
						<TableContainer>
							<Table size="small" aria-label="Suggested cover">
								<TableHead>
									<TableRow>
										<TableCell>Date</TableCell>
										<TableCell>Period</TableCell>
										<TableCell>Class</TableCell>
										<TableCell>Subject</TableCell>
										<TableCell>Absent teacher</TableCell>
										<TableCell>Cover by</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{suggestions.map((suggestion) => {
										const key = slotKey(suggestion.entryId, suggestion.date);
										const result = results[key];
										const options = optionsFor(suggestion);
										return (
											<TableRow key={key}>
												<TableCell>{suggestion.date}</TableCell>
												<TableCell>{suggestion.periodLabel}</TableCell>
												<TableCell>{suggestion.sectionName}</TableCell>
												<TableCell>{suggestion.subjectName}</TableCell>
												<TableCell>{suggestion.absentTeacherName}</TableCell>
												<TableCell>
													{options.length === 0 ? (
														<Typography variant="body2" sx={{ color: colors.inkSoft }}>
															{suggestion.reasonIfNone ?? "Nobody is free"}
														</Typography>
													) : (
														<TextField
															select
															size="small"
															label="Substitute"
															value={choice[key] ?? ""}
															onChange={(e) => setChoice((c) => ({ ...c, [key]: e.target.value }))}
															disabled={confirming || result?.ok}
															sx={{ minWidth: 220 }}
														>
															<MenuItem value="">Skip</MenuItem>
															{options.map((option) => (
																<MenuItem key={option.personId} value={String(option.personId)}>
																	{option.name} — {option.rank}
																</MenuItem>
															))}
														</TextField>
													)}
													{result && (
														<Typography variant="caption" role="status" sx={{ display: "block", color: result.ok ? colors.ok : colors.bad }}>
															{result.ok ? "Assigned" : `Failed: ${result.message}`}
														</Typography>
													)}
												</TableCell>
											</TableRow>
										);
									})}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={confirming}>
					{finished ? "Close" : "Cancel"}
				</Button>
				<Button variant="contained" onClick={handleConfirm} disabled={confirming || pending.length === 0}>
					{confirming ? "Assigning..." : "Confirm"}
				</Button>
			</DialogActions>
		</Dialog>
	);
}
