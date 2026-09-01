import Stack from "@mui/material/Stack";
import { ExaminationSettingsPanel } from "./ExaminationSettingsPanel";
import { ExamsPanel } from "./ExamsPanel";
import { GradingScalesPanel } from "./GradingScalesPanel";

export function ExaminationsPage() {
	return (
		<Stack spacing={3}>
			<ExaminationSettingsPanel />
			<GradingScalesPanel />
			<ExamsPanel />
		</Stack>
	);
}
