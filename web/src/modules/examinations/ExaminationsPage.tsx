import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { ExamsPanel } from "./ExamsPanel";
import { GradingScalesPanel } from "./GradingScalesPanel";

export function ExaminationsPage() {
	return (
		<Stack spacing={3} sx={{ maxWidth: 900 }}>
			<Typography variant="h4" component="h1">
				Examinations
			</Typography>
			<GradingScalesPanel />
			<ExamsPanel />
		</Stack>
	);
}
