import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";

/** #208 fills this in with real assigned sections/attendance-marking/leave data; #210
 * only needs the route to exist. */
export function TeacherDashboardPage() {
	return (
		<Stack spacing={2}>
			<Typography variant="h5">Teacher portal</Typography>
			<Typography variant="body2" color="text.secondary">
				Your sections, attendance and leave requests will appear here.
			</Typography>
		</Stack>
	);
}
