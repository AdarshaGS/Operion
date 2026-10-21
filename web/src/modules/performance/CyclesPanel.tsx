import { useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import LinearProgress from "@mui/material/LinearProgress";
import Paper from "@mui/material/Paper";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import type { CycleSummary } from "../../api/performance";
import { CloseCycleDialog } from "./CloseCycleDialog";
import { formatDate } from "./reviewFormat";

export interface CyclesPanelProps {
	summaries: CycleSummary[] | null;
	error: string | null;
	onRetry: () => void;
	canManage: boolean;
	onCreate: () => void;
	onOpenCycle: (cycleId: number) => void;
	onChanged: () => void;
}

/** Appraisal cycles with how far each review has got. Staff and completion counts come from the same rule as the dashboard. */
export function CyclesPanel({ summaries, error, onRetry, canManage, onCreate, onOpenCycle, onChanged }: CyclesPanelProps) {
	const [closing, setClosing] = useState<CycleSummary | null>(null);

	if (error) {
		return <Alert severity="error" action={<Button color="inherit" size="small" onClick={onRetry}>Retry</Button>}>{error}</Alert>;
	}
	if (!summaries) {
		return <Skeleton variant="rounded" height={200} />;
	}

	return (
		<Stack spacing={2}>
			<Paper variant="outlined">
				<TableContainer>
					<Table size="small" aria-label="Appraisal cycles">
						<TableHead>
							<TableRow>
								<TableCell>Cycle</TableCell>
								<TableCell>Review period</TableCell>
								<TableCell>Status</TableCell>
								<TableCell align="right">Staff</TableCell>
								<TableCell sx={{ minWidth: 190 }}>Reviews completed</TableCell>
								<TableCell align="right">Pending</TableCell>
								<TableCell align="right" />
							</TableRow>
						</TableHead>
						<TableBody>
							{summaries.map((s) => {
								const pct = s.staffCount === 0 ? 0 : Math.round((s.completed / s.staffCount) * 100);
								return (
									<TableRow key={s.cycle.id} hover sx={{ "&:last-child td": { borderBottom: 0 } }}>
										<TableCell sx={{ fontWeight: 600 }}>{s.cycle.name}</TableCell>
										<TableCell>{formatDate(s.cycle.startDate)} – {formatDate(s.cycle.endDate)}</TableCell>
										<TableCell>
											<Chip size="small" label={s.cycle.status === "OPEN" ? "Open" : "Closed"} color={s.cycle.status === "OPEN" ? "success" : "default"} variant="outlined" />
										</TableCell>
										<TableCell align="right">{s.staffCount}</TableCell>
										<TableCell>
											<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
												<LinearProgress variant="determinate" value={pct} sx={{ flexGrow: 1, height: 6, borderRadius: 3 }} aria-label={`${s.cycle.name} completion`} />
												<Typography variant="caption" sx={{ minWidth: 70, textAlign: "right" }}>{s.completed} of {s.staffCount}</Typography>
											</Stack>
										</TableCell>
										<TableCell align="right">{s.pending}</TableCell>
										<TableCell align="right">
											<Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
												<Button size="small" onClick={() => onOpenCycle(s.cycle.id)}>View staff</Button>
												{canManage && s.cycle.status === "OPEN" && (
													<Button size="small" color="warning" onClick={() => setClosing(s)}>Close cycle</Button>
												)}
											</Stack>
										</TableCell>
									</TableRow>
								);
							})}
							{summaries.length === 0 && (
								<TableRow>
									<TableCell colSpan={7}>
										<Stack sx={{ py: 4, alignItems: "center", textAlign: "center" }} spacing={1}>
											<Typography variant="body2" color="text.secondary">
												No appraisal cycles yet. A cycle is a named review period that ratings and goals belong to.
											</Typography>
											{canManage && <Button variant="contained" size="small" onClick={onCreate}>Create appraisal cycle</Button>}
										</Stack>
									</TableCell>
								</TableRow>
							)}
						</TableBody>
					</Table>
				</TableContainer>
			</Paper>
			<Typography variant="caption" color="text.secondary">
				A cycle has no saved roster: its staff are the teachers with an active class assignment, plus anyone already rated in it. A review counts as completed once a manager rating is recorded.
			</Typography>
			{closing && (
				<CloseCycleDialog cycle={closing.cycle} pending={closing.pending} onClose={() => setClosing(null)}
					onClosed={() => { setClosing(null); onChanged(); }} />
			)}
		</Stack>
	);
}
