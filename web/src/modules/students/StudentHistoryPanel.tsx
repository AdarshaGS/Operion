import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import type { StudentEnrollmentResponse } from "../../api/enrollments";
import type { EnrollmentLabels } from "./useEnrollmentLabels";

interface StudentHistoryPanelProps {
	enrollments: StudentEnrollmentResponse[];
	labels: EnrollmentLabels;
}

/** A student's enrollment history, newest first. Takes the page's already-loaded enrollments and
 * label resolvers as props - it fetches nothing itself. */
export function StudentHistoryPanel({ enrollments, labels }: StudentHistoryPanelProps) {
	const rows = [...enrollments].sort((a, b) => b.enrolledDate.localeCompare(a.enrolledDate));

	return (
		<Paper sx={{ p: 2 }}>
			<Stack spacing={1.5}>
				<Typography variant="subtitle1">Enrollment history</Typography>
				{rows.length === 0 ? (
					<Typography variant="body2" color="text.secondary">
						No enrollment history yet.
					</Typography>
				) : (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Academic year</TableCell>
									<TableCell>Class</TableCell>
									<TableCell>Section</TableCell>
									<TableCell>Roll no.</TableCell>
									<TableCell>Enrolled</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{rows.map((enrollment) => (
									<TableRow key={enrollment.id}>
										<TableCell>{labels.academicYearName(enrollment.academicYearId)}</TableCell>
										<TableCell>{labels.classLabel(enrollment.schoolClassId)}</TableCell>
										<TableCell>{labels.sectionName(enrollment.sectionId)}</TableCell>
										<TableCell>{enrollment.rollNumber ?? "—"}</TableCell>
										<TableCell>{enrollment.enrolledDate}</TableCell>
										<TableCell>{enrollment.current && <Chip size="small" color="success" variant="outlined" label="Current" />}</TableCell>
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
