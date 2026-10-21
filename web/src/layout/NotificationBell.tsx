import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Badge from "@mui/material/Badge";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import NotificationsIcon from "@mui/icons-material/Notifications";
import { useAuth } from "../auth/AuthContext";
import { myNotifications } from "../api/notifications";
import { colors } from "../theme";

const POLL_INTERVAL_MS = 60_000;

/** Unread count for the signed-in user, polled rather than pushed (no websocket/SSE
 * channel exists yet) - clicking goes to the My notifications panel on Communication. */
export function NotificationBell() {
	const navigate = useNavigate();
	const { isPathEnabled, permissionsLoaded } = useAuth();
	const [unreadCount, setUnreadCount] = useState(0);
	// Notifications belong to the Communication module: with it switched off the API refuses the request, and the
	// bell would only lead to a page the user cannot open. Wait for the module list before asking.
	const available = permissionsLoaded && isPathEnabled("/communication");

	useEffect(() => {
		if (!available) return;
		function refresh() {
			myNotifications()
				.then((notifications) => setUnreadCount(notifications.filter((n) => n.readAt === null).length))
				.catch(() => {});
		}
		refresh();
		const interval = setInterval(refresh, POLL_INTERVAL_MS);
		return () => clearInterval(interval);
	}, [available]);

	if (!available) return null;

	return (
		<Tooltip title="Notifications">
			<IconButton aria-label="Notifications" onClick={() => navigate("/communication")} sx={{ width: 44, height: 44, borderRadius: "12px", border: `1px solid ${colors.rule}`, backgroundColor: colors.paper, color: colors.ink }}>
				<Badge badgeContent={unreadCount} color="error" max={9}>
					<NotificationsIcon />
				</Badge>
			</IconButton>
		</Tooltip>
	);
}
