import { useLocation } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Stack from "@mui/material/Stack";
import { GradeLevelsPanel } from "./GradeLevelsPanel";
import { SchoolClassesPanel } from "./SchoolClassesPanel";
import { SubjectsPanel } from "./SubjectsPanel";

export function AcademicsPage() {
	// Set when a screen (e.g. student admission) redirects here because academic setup
	// isn't done yet - see StudentCreatePage's prerequisite check.
	const blockedMessage = (useLocation().state as { blockedMessage?: string } | null)?.blockedMessage;

	return (
		<Stack spacing={3}>
			{blockedMessage && <Alert severity="warning">{blockedMessage}</Alert>}
			<GradeLevelsPanel />
			<SubjectsPanel />
			<SchoolClassesPanel />
		</Stack>
	);
}
