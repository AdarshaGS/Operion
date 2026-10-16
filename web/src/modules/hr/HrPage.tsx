import { useState } from "react";
import Stack from "@mui/material/Stack";
import { JobApplicationsPanel } from "./JobApplicationsPanel";
import { LeaveRequestsInboxPanel } from "./LeaveRequestsInboxPanel";
import { LeaveTypesPanel } from "./LeaveTypesPanel";
import { StaffProfilesPanel } from "./StaffProfilesPanel";

export function HrPage() {
	// Approving an application hires the applicant, so the staff roster has to reload.
	const [staffVersion, setStaffVersion] = useState(0);
	return (
		<Stack spacing={3}>
			<LeaveTypesPanel />
			<StaffProfilesPanel key={staffVersion} />
			<LeaveRequestsInboxPanel />
			<JobApplicationsPanel onHired={() => setStaffVersion((v) => v + 1)} />
		</Stack>
	);
}
