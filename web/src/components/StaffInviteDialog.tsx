import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import type { StaffInviteResponse } from "../api/users";

interface StaffInviteDialogProps {
	invite: StaffInviteResponse | null;
	onClose: () => void;
}

/** Shows a freshly-issued staff/user invite's one-time claim link + raw token - shared
 * across every place a login gets granted (Settings > Users' "Add user", HR's staff
 * detail "Grant login access", and the "Add staff member" opt-in checkbox) so this UX
 * lives in exactly one place. See UserController.invite()/StaffInviteService - the
 * token is never stored server-side after this, so losing it means starting over. */
export function StaffInviteDialog({ invite, onClose }: StaffInviteDialogProps) {
	return (
		<Dialog open={invite !== null} onClose={onClose} fullWidth maxWidth="sm">
			<DialogTitle>Staff invite issued</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{invite && (
						<Alert severity={invite.emailSent ? "success" : "warning"}>
							{invite.emailSent
								? "An email with this invite link has been sent to the new hire."
								: "Couldn't send the invite email (no provider configured or the send failed)."}
						</Alert>
					)}
					<Alert severity="warning">
						This token is shown once and never stored - copy it now. Share it with the new hire along with your organisation's
						login slug; they'll set their own password on the sign-up page.
					</Alert>
					{invite && (
						<>
							<TextField
								label="Claim link"
								value={`${window.location.origin}/claim-staff-invite?token=${encodeURIComponent(invite.claimToken)}`}
								fullWidth
								slotProps={{ input: { readOnly: true } }}
							/>
							<Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
								<TextField label="Raw token" value={invite.claimToken} fullWidth slotProps={{ input: { readOnly: true } }} />
								<Tooltip title="Copy token">
									<IconButton onClick={() => navigator.clipboard.writeText(invite.claimToken)}>
										<ContentCopyIcon fontSize="small" />
									</IconButton>
								</Tooltip>
							</Box>
							<Typography variant="caption" color="text.secondary">
								Expires {new Date(invite.expiresAt).toLocaleString()}
							</Typography>
						</>
					)}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	);
}
