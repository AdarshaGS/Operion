import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Badge from "@mui/material/Badge";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import NotificationsIcon from "@mui/icons-material/Notifications";
import { myNotifications } from "../api/notifications";

const POLL_INTERVAL_MS = 60_000;

/** Unread count for the signed-in user, polled rather than pushed (no websocket/SSE
 * channel exists yet) - clicking goes to the My notifications panel on Communication. */
export function NotificationBell() {
	const navigate = useNavigate();
	const [unreadCount, setUnreadCount] = useState(0);

	useEffect(() => {
		function refresh() {
			myNotifications()
				.then((notifications) => setUnreadCount(notifications.filter((n) => n.readAt === null).length))
				.catch(() => {});
		}
		refresh();
		const interval = setInterval(refresh, POLL_INTERVAL_MS);
		return () => clearInterval(interval);
	}, []);

	return (
		<Tooltip title="Notifications">
			<IconButton color="inherit" aria-label="Notifications" onClick={() => navigate("/communication")}>
				<Badge badgeContent={unreadCount} color="error" max={9}>
					<NotificationsIcon />
				</Badge>
			</IconButton>
		</Tooltip>
	);
}
