import Stack from "@mui/material/Stack";
import { ExamsPanel } from "./ExamsPanel";
import { GradingScalesPanel } from "./GradingScalesPanel";

export function ExaminationsPage() {
	return (
		<Stack spacing={3}>
			<GradingScalesPanel />
			<ExamsPanel />
		</Stack>
	);
}
