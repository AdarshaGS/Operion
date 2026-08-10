import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { LeaveRequestsInboxPanel } from "./LeaveRequestsInboxPanel";
import { LeaveTypesPanel } from "./LeaveTypesPanel";
import { StaffProfilesPanel } from "./StaffProfilesPanel";

export function HrPage() {
	return (
		<Stack spacing={3} sx={{ maxWidth: 900 }}>
			<Typography variant="h4" component="h1">
				HR
			</Typography>
			<LeaveTypesPanel />
			<StaffProfilesPanel />
			<LeaveRequestsInboxPanel />
		</Stack>
	);
}
