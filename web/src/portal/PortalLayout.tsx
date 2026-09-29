import { Outlet, useNavigate } from "react-router-dom";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import ButtonBase from "@mui/material/ButtonBase";
import Stack from "@mui/material/Stack";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import LogoutIcon from "@mui/icons-material/Logout";
import { Wordmark } from "../branding/Wordmark";
import { useAuth } from "../auth/AuthContext";

/**
 * Deliberately a fraction of AppLayout's admin shell (#210) - no sidebar, no module nav,
 * just identity + sign out. Guardian/Teacher dashboards render their own content below.
 */
export function PortalLayout() {
	const navigate = useNavigate();
	const { profile, logout } = useAuth();

	return (
		<Box sx={{ display: "flex", flexDirection: "column", minHeight: "100vh" }}>
			<AppBar position="static" elevation={0}>
				<Toolbar sx={{ gap: 2 }}>
					<ButtonBase onClick={() => navigate("/")} disableRipple sx={{ borderRadius: 1 }} aria-label="Go to home">
						<Wordmark size="small" text={profile?.organisationName ?? "OPERION"} />
					</ButtonBase>
					<Box sx={{ flexGrow: 1 }} />
					<Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
						{profile?.personName && (
							<Typography variant="body2" sx={{ opacity: 0.85 }}>
								{profile.personName}
							</Typography>
						)}
						<Button color="inherit" size="small" startIcon={<LogoutIcon fontSize="small" />} onClick={() => logout()}>
							Sign out
						</Button>
					</Stack>
				</Toolbar>
			</AppBar>
			<Box component="main" sx={{ flexGrow: 1, p: { xs: 2, md: 4 } }}>
				<Box sx={{ maxWidth: 900, mx: "auto" }}>
					<Outlet />
				</Box>
			</Box>
		</Box>
	);
}
