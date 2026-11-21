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
import { getSession } from "../api/tokenStore";
import type { StaffInviteResponse } from "../api/users";

interface StaffInviteDialogProps {
	invite: StaffInviteResponse | null;
	onClose: () => void;
}

/** Same query layout as the emailed link (StaffInviteService.sendInviteEmail): org first, then token. */
function claimLink(claimToken: string): string {
	const params = new URLSearchParams();
	const organisationSlug = getSession()?.organisationSlug;
	if (organisationSlug) {
		params.set("org", organisationSlug);
	}
	params.set("token", claimToken);
	return `${window.location.origin}/claim-staff-invite?${params.toString()}`;
}

/** Shows a staff invite's one-time claim link + raw token. Shown when HR gives a staff member login access (HR > Add
 * staff, or "Give login access" on a staff profile) and by Settings > Users' "Resend invitation" for members still in
 * PENDING status. See StaffAccessService/UserController.invite()/StaffInviteService - the token is never stored
 * server-side after this, so losing it means starting over.
 *
 * The link has the same shape as the emailed one (`/claim-staff-invite?org=<slug>&token=<token>`). The org slug is the one
 * the signed-in user typed at login; a session from before that was recorded has none, in which case the link omits `org=`
 * and the claim page asks for it. */
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
								value={claimLink(invite.claimToken)}
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
