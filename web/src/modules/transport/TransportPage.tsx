import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { RoutesPanel } from "./RoutesPanel";
import { StudentAssignmentPanel } from "./StudentAssignmentPanel";
import { VehiclesPanel } from "./VehiclesPanel";

export function TransportPage() {
	return (
		<Stack spacing={3} sx={{ maxWidth: 900 }}>
			<Typography variant="h4" component="h1">
				Transport
			</Typography>
			<VehiclesPanel />
			<RoutesPanel />
			<StudentAssignmentPanel />
		</Stack>
	);
}
