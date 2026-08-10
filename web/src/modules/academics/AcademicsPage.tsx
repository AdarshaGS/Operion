import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { GradeLevelsPanel } from "./GradeLevelsPanel";
import { SchoolClassesPanel } from "./SchoolClassesPanel";
import { SubjectsPanel } from "./SubjectsPanel";

export function AcademicsPage() {
	return (
		<Stack spacing={3} sx={{ maxWidth: 900 }}>
			<Typography variant="h4" component="h1">
				Academics
			</Typography>
			<GradeLevelsPanel />
			<SubjectsPanel />
			<SchoolClassesPanel />
		</Stack>
	);
}
