import Stack from "@mui/material/Stack";
import { AnnouncementsPanel } from "./AnnouncementsPanel";
import { EventCalendarPanel } from "./EventCalendarPanel";
import { MyNotificationsPanel } from "./MyNotificationsPanel";

export function CommunicationPage() {
	return (
		<Stack spacing={3}>
			<MyNotificationsPanel />
			<AnnouncementsPanel />
			<EventCalendarPanel />
		</Stack>
	);
}
