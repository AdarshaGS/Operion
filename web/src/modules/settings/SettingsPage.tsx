import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { AcademicYearsPanel } from "./AcademicYearsPanel";
import { CampusesPanel } from "./CampusesPanel";

/** Campus and AcademicYear are Foundation-owned entities (erp-system-plan.md §1), not
 * Academic ones - kept in their own Settings section rather than folded into the
 * Academics module, matching the backend's actual module boundaries. */
export function SettingsPage() {
	return (
		<Stack spacing={3} sx={{ maxWidth: 900 }}>
			<Typography variant="h4" component="h1">
				Settings
			</Typography>
			<CampusesPanel />
			<AcademicYearsPanel />
		</Stack>
	);
}
