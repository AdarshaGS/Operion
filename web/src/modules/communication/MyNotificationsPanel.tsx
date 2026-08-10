import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import FormControlLabel from "@mui/material/FormControlLabel";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import {
	markNotificationRead,
	myNotificationPreferences,
	myNotifications,
	setMyNotificationPreference,
	type NotificationRecipientResponse,
} from "../../api/notifications";

/** Notifications/preferences are scoped to "me" - whoever is logged in. The demo admin
 * has their own Person too, so publishing an ORG-wide announcement fans out to them as
 * well - this panel is how that gets verified without needing a second test user. */
export function MyNotificationsPanel() {
	const [notifications, setNotifications] = useState<NotificationRecipientResponse[]>([]);
	const [inAppEnabled, setInAppEnabled] = useState(true);
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		myNotifications()
			.then(setNotifications)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load notifications"));
		myNotificationPreferences()
			.then((prefs) => {
				const inApp = prefs.find((p) => p.channel === "IN_APP");
				if (inApp) setInAppEnabled(inApp.enabled);
			})
			.catch(() => {});
	}

	useEffect(refresh, []);

	async function handleMarkRead(id: number) {
		try {
			await markNotificationRead(id);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to mark as read");
		}
	}

	async function handleTogglePreference(enabled: boolean) {
		setInAppEnabled(enabled);
		try {
			await setMyNotificationPreference("IN_APP", enabled);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update preference");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">My notifications</Typography>
					<FormControlLabel
						control={<Switch checked={inAppEnabled} onChange={(e) => handleTogglePreference(e.target.checked)} />}
						label="In-app notifications"
					/>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{notifications.length === 0 && <Alert severity="info">No notifications yet.</Alert>}

				{notifications.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Channel</TableCell>
									<TableCell>Status</TableCell>
									<TableCell>Sent</TableCell>
									<TableCell />
								</TableRow>
							</TableHead>
							<TableBody>
								{notifications.map((notification) => (
									<TableRow key={notification.id}>
										<TableCell>{notification.channel}</TableCell>
										<TableCell>
											<Chip label={notification.deliveryStatus} size="small" />
										</TableCell>
										<TableCell>{notification.sentAt ? new Date(notification.sentAt).toLocaleString() : "—"}</TableCell>
										<TableCell>
											{notification.deliveryStatus !== "READ" && (
												<Button size="small" onClick={() => handleMarkRead(notification.id)}>
													Mark read
												</Button>
											)}
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>
		</Paper>
	);
}
