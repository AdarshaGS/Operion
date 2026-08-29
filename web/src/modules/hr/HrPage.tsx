import Stack from "@mui/material/Stack";
import { JobApplicationsPanel } from "./JobApplicationsPanel";
import { LeaveRequestsInboxPanel } from "./LeaveRequestsInboxPanel";
import { LeaveTypesPanel } from "./LeaveTypesPanel";
import { StaffProfilesPanel } from "./StaffProfilesPanel";

export function HrPage() {
	return (
		<Stack spacing={3}>
			<LeaveTypesPanel />
			<StaffProfilesPanel />
			<LeaveRequestsInboxPanel />
			<JobApplicationsPanel />
		</Stack>
	);
}
