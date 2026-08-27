import Stack from "@mui/material/Stack";
import { RoutesPanel } from "./RoutesPanel";
import { StudentAssignmentPanel } from "./StudentAssignmentPanel";
import { VehiclesPanel } from "./VehiclesPanel";

export function TransportPage() {
	return (
		<Stack spacing={3}>
			<VehiclesPanel />
			<RoutesPanel />
			<StudentAssignmentPanel />
		</Stack>
	);
}
