import Stack from "@mui/material/Stack";
import { GradeLevelsPanel } from "./GradeLevelsPanel";
import { SchoolClassesPanel } from "./SchoolClassesPanel";
import { SubjectsPanel } from "./SubjectsPanel";

export function AcademicsPage() {
	return (
		<Stack spacing={3}>
			<GradeLevelsPanel />
			<SubjectsPanel />
			<SchoolClassesPanel />
		</Stack>
	);
}
