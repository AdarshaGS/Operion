import Paper from "@mui/material/Paper";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import type { Scorecard, ScorecardTeacher } from "../../api/performance";
import { formatScore, formatValue, targetLabel } from "./kpiFormat";

/** Teachers are listed alphabetically on purpose - this is a coaching view, not a ranking. */
export function ScorecardTable({ scorecard, onSelect }: { scorecard: Scorecard; onSelect?: (teacher: ScorecardTeacher) => void }) {
	return (
		<Paper variant="outlined" sx={{ overflowX: "auto" }}>
			<Table size="small">
				<TableHead>
					<TableRow>
						<TableCell>Teacher</TableCell>
						{scorecard.metrics.map((m) => (
							<TableCell key={m.metric} align="right" title={m.description}>
								{m.label}{" "}
								<Typography component="span" variant="caption" color="text.secondary">({targetLabel(m)})</Typography>
							</TableCell>
						))}
						<TableCell align="right">Overall score</TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{scorecard.teachers.map((teacher) => (
						<TableRow key={teacher.personId} hover={!!onSelect} onClick={onSelect ? () => onSelect(teacher) : undefined} sx={{ cursor: onSelect ? "pointer" : "default" }}>
							<TableCell>{teacher.name}</TableCell>
							{teacher.cells.map((cell, i) => {
								const metric = scorecard.metrics[i];
								return (
									<TableCell key={cell.metric} align="right" title={cell.sample ? `${cell.sample} records` : "No records in this period"}>
										{formatValue(cell.value, metric.unit)}
										{cell.previous !== null && (
											<Typography component="div" variant="caption" color="text.secondary">
												was {formatValue(cell.previous, metric.unit)}
											</Typography>
										)}
									</TableCell>
								);
							})}
							<TableCell align="right">{formatScore(teacher.overallScore)}</TableCell>
						</TableRow>
					))}
					{scorecard.teachers.length === 0 && (
						<TableRow>
							<TableCell colSpan={scorecard.metrics.length + 2}>No teachers have an active class assignment.</TableCell>
						</TableRow>
					)}
				</TableBody>
			</Table>
		</Paper>
	);
}
