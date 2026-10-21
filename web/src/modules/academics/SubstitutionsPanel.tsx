import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
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
import { ApiError } from "../../api/client";
import { listPersons, type PersonResponse } from "../../api/persons";
import {
	assignSubstitution,
	listSubstitutionNeeds,
	listSubstitutionsOn,
	removeSubstitution,
	type SubstitutionResponse,
	type SubstitutionNeedResponse,
} from "../../api/timetable";
import { useAuth } from "../../auth/AuthContext";
import { dayLongLabel, timeLabel } from "./timetableViewShared";

function isoDate(offsetDays: number): string {
	const date = new Date();
	date.setDate(date.getDate() + offsetDays);
	return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

/** Cover for an absent teacher: lists every slot they would miss in a date range and who could take it, best match
 * first (teaches the section, then the subject, then anyone free). A substitution covers one date only - the weekly
 * timetable is never changed. */
export function SubstitutionsPanel() {
	const { hasAnyPermission } = useAuth();
	const canManage = hasAnyPermission(["TIMETABLE_MANAGE"]);

	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [teacherId, setTeacherId] = useState("");
	const [from, setFrom] = useState(isoDate(0));
	const [to, setTo] = useState(isoDate(6));
	const [reason, setReason] = useState("");
	const [needs, setNeeds] = useState<SubstitutionNeedResponse[] | null>(null);
	const [choice, setChoice] = useState<Record<string, string>>({});
	const [error, setError] = useState<string | null>(null);
	const [busy, setBusy] = useState(false);

	const [arranged, setArranged] = useState<SubstitutionResponse[] | null>(null);

	useEffect(() => {
		listPersons().then(setPersons).catch(() => {});
	}, []);

	// Cover already arranged for the "From" date, so the tab has records before any search is run.
	function loadArranged() {
		if (!from) return;
		listSubstitutionsOn(from).then(setArranged).catch(() => setArranged([]));
	}
	useEffect(loadArranged, [from]);

	async function load() {
		if (!teacherId) return;
		setError(null);
		try {
			setNeeds(await listSubstitutionNeeds(Number(teacherId), from, to));
		} catch (err) {
			setNeeds(null);
			setError(err instanceof ApiError ? err.message : "Failed to find the slots");
		}
	}

	async function act(action: () => Promise<unknown>, failure: string) {
		setBusy(true);
		setError(null);
		try {
			await action();
			await load();
			loadArranged();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : failure);
		} finally {
			setBusy(false);
		}
	}

	const key = (need: SubstitutionNeedResponse) => `${need.entryId}:${need.date}`;

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Substitutions</Typography>
				<Typography variant="subtitle2">Cover arranged for {from}</Typography>
				{arranged !== null && arranged.length === 0 && <Alert severity="info">No cover arranged for this date yet. Pick the absent teacher below to find the slots needing cover.</Alert>}
				{arranged !== null && arranged.length > 0 && (
					<TableContainer>
						<Table size="small" aria-label="Cover arranged">
							<TableHead>
								<TableRow>
									<TableCell>Period</TableCell>
									<TableCell>Class</TableCell>
									<TableCell>Subject</TableCell>
									<TableCell>Absent teacher</TableCell>
									<TableCell>Covered by</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{arranged.map((row) => (
									<TableRow key={row.id}>
										<TableCell>
											{row.periodLabel} ({timeLabel(row.startTime)}–{timeLabel(row.endTime)})
										</TableCell>
										<TableCell>
											{row.className} {row.sectionName}
										</TableCell>
										<TableCell>{row.subjectName}</TableCell>
										<TableCell>{row.absentTeacherName}</TableCell>
										<TableCell>{row.substituteTeacherName}</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
				<Typography variant="subtitle2">Find cover for an absent teacher</Typography>
				<Stack direction={{ xs: "column", md: "row" }} spacing={2}>
					<TextField select label="Absent teacher" value={teacherId} onChange={(e) => setTeacherId(e.target.value)} sx={{ minWidth: 240 }}>
						{persons.map((person) => (
							<MenuItem key={person.id} value={String(person.id)}>
								{person.firstName} {person.lastName}
							</MenuItem>
						))}
					</TextField>
					<TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
					<TextField label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
					<TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} sx={{ flexGrow: 1 }} />
					<Button variant="outlined" onClick={load} disabled={!teacherId || !from || !to}>
						Find slots
					</Button>
				</Stack>

				{error && <Alert severity="error">{error}</Alert>}
				{needs !== null && needs.length === 0 && <Alert severity="info">This teacher has no timetable slots in that period.</Alert>}

				{needs !== null && needs.length > 0 && (
					<TableContainer>
						<Table size="small" aria-label="Slots needing cover">
							<TableHead>
								<TableRow>
									<TableCell>Date</TableCell>
									<TableCell>Period</TableCell>
									<TableCell>Class</TableCell>
									<TableCell>Subject</TableCell>
									<TableCell>Cover</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{needs.map((need) => (
									<TableRow key={key(need)}>
										<TableCell>
											{dayLongLabel(need.dayOfWeek)} {need.date}
										</TableCell>
										<TableCell>
											{need.periodLabel} ({timeLabel(need.startTime)}–{timeLabel(need.endTime)})
										</TableCell>
										<TableCell>
											{need.className} {need.sectionName}
										</TableCell>
										<TableCell>{need.subjectName}</TableCell>
										<TableCell>
											{need.substitution ? (
												<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
													<Typography variant="body2">Covered by {need.substitution.substituteTeacherName}</Typography>
													{canManage && (
														<Button size="small" color="error" disabled={busy} onClick={() => act(() => removeSubstitution(need.substitution!.id), "Failed to remove cover")}>
															Remove
														</Button>
													)}
												</Stack>
											) : need.candidates.length === 0 ? (
												<Typography variant="body2" color="text.secondary">
													Nobody is free
												</Typography>
											) : (
												canManage && (
													<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
														<TextField
															select
															size="small"
															label="Substitute"
															value={choice[key(need)] ?? ""}
															onChange={(e) => setChoice((c) => ({ ...c, [key(need)]: e.target.value }))}
															sx={{ minWidth: 240 }}
														>
															{need.candidates.map((candidate) => (
																<MenuItem key={candidate.personId} value={String(candidate.personId)}>
																	{candidate.name} — {candidate.rank}
																</MenuItem>
															))}
														</TextField>
														<Button
															size="small"
															variant="contained"
															disabled={busy || !choice[key(need)]}
															onClick={() =>
																act(
																	() =>
																		assignSubstitution({
																			entryId: need.entryId,
																			date: need.date,
																			substituteTeacherPersonId: Number(choice[key(need)]),
																			reason: reason || null,
																		}),
																	"Failed to assign cover",
																)
															}
														>
															Assign
														</Button>
													</Stack>
												)
											)}
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>
		</Paper>
	);
}
