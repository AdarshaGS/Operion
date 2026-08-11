import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { AcademicYearsPanel } from "./AcademicYearsPanel";
import { CampusesPanel } from "./CampusesPanel";
import { RolesPanel } from "./RolesPanel";
import { UsersPanel } from "./UsersPanel";

/** Campus/AcademicYear/Role/User are all Foundation-owned entities
 * (erp-system-plan.md §1), not domain-module ones - kept in their own Settings section
 * rather than folded into any domain module, matching the backend's actual module
 * boundaries (com.operion.organisation / com.operion.authorization / com.operion.identity). */
export function SettingsPage() {
	return (
		<Stack spacing={3} sx={{ maxWidth: 900 }}>
			<Typography variant="h4" component="h1">
				Settings
			</Typography>
			<CampusesPanel />
			<AcademicYearsPanel />
			<RolesPanel />
			<UsersPanel />
		</Stack>
	);
}
