import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { AnnouncementsPanel } from "./AnnouncementsPanel";
import { MyNotificationsPanel } from "./MyNotificationsPanel";

export function CommunicationPage() {
	return (
		<Stack spacing={3} sx={{ maxWidth: 900 }}>
			<Typography variant="h4" component="h1">
				Communication
			</Typography>
			<MyNotificationsPanel />
			<AnnouncementsPanel />
		</Stack>
	);
}
