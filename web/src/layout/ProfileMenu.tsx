import { useState, type MouseEvent } from "react";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import IconButton from "@mui/material/IconButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import KeyIcon from "@mui/icons-material/Key";
import LogoutIcon from "@mui/icons-material/Logout";
import { useAuth } from "../auth/AuthContext";
import { ChangePasswordDialog } from "./ChangePasswordDialog";
import { colors, fontDisplay } from "../theme";

function initials(name: string | null): string {
	if (!name) return "?";
	return name
		.split(" ")
		.filter(Boolean)
		.map((part) => part[0])
		.join("")
		.slice(0, 2)
		.toUpperCase();
}

/** Shows who's logged in (name/email/role(s)/org) - previously nowhere in the UI, only
 * discoverable by finding your own row in Settings > People with access. */
export function ProfileMenu() {
	const { profile, logout } = useAuth();
	const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
	const [changePasswordOpen, setChangePasswordOpen] = useState(false);

	function openChangePassword() {
		setAnchorEl(null);
		setChangePasswordOpen(true);
	}

	return (
		<>
			<Tooltip title={profile?.personName ?? "Account"}>
				<IconButton onClick={(event: MouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget)} color="inherit">
					<Avatar
						sx={{ width: 32, height: 32, fontSize: 13, fontFamily: fontDisplay, bgcolor: colors.ruleStrong, color: colors.accentInk }}
					>
						{initials(profile?.personName ?? null)}
					</Avatar>
				</IconButton>
			</Tooltip>
			<Menu anchorEl={anchorEl} open={anchorEl !== null} onClose={() => setAnchorEl(null)}>
				<Box sx={{ px: 2, py: 1, minWidth: 220 }}>
					<Typography variant="subtitle1">{profile?.personName ?? "—"}</Typography>
					<Typography variant="body2" color="text.secondary">
						{profile?.email ?? "—"}
					</Typography>
					<Typography variant="caption" color="text.secondary">
						{profile?.organisationName ?? "—"}
					</Typography>
					{profile && profile.roleNames.length > 0 && (
						<Stack direction="row" spacing={0.5} sx={{ mt: 1, flexWrap: "wrap", gap: 0.5 }}>
							{profile.roleNames.map((roleName) => (
								<Chip key={roleName} label={roleName} size="small" variant="outlined" sx={{ borderColor: colors.rule }} />
							))}
						</Stack>
					)}
				</Box>
				<Divider />
				<MenuItem onClick={openChangePassword}>
					<ListItemIcon>
						<KeyIcon fontSize="small" />
					</ListItemIcon>
					Change password
				</MenuItem>
				<MenuItem onClick={logout}>
					<ListItemIcon>
						<LogoutIcon fontSize="small" />
					</ListItemIcon>
					Sign out
				</MenuItem>
			</Menu>
			<ChangePasswordDialog open={changePasswordOpen} onClose={() => setChangePasswordOpen(false)} />
		</>
	);
}
