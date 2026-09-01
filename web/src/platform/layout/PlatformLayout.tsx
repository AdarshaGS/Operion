import { Outlet, useLocation, useNavigate } from "react-router-dom";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import Drawer from "@mui/material/Drawer";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import ApartmentIcon from "@mui/icons-material/Apartment";
import DashboardIcon from "@mui/icons-material/Dashboard";
import SellIcon from "@mui/icons-material/Sell";
import LogoutIcon from "@mui/icons-material/Logout";
import { Wordmark } from "../../branding/Wordmark";
import { colors } from "../../theme";
import { usePlatformAuth } from "../auth/PlatformAuthContext";

const DRAWER_WIDTH = 220;

// No standalone "Integrations" nav entry - entitlement is per-organisation (see
// PlatformExternalServiceController), so it lives as a section on each org's own
// OrganisationDetailPage instead of a single global page.
const NAV_ITEMS = [
	{ label: "Dashboard", path: "/platform/dashboard", icon: <DashboardIcon /> },
	{ label: "Organisations", path: "/platform/organisations", icon: <ApartmentIcon /> },
	{ label: "Plans", path: "/platform/plans", icon: <SellIcon /> },
];

/** Mirrors AppLayout's shape but is wired to the platform auth plane, not the school
 * org one - kept as a separate component tree rather than parameterizing AppLayout,
 * since the two auth contexts (usePlatformAuth vs useAuth) are deliberately unrelated. */
export function PlatformLayout() {
	const navigate = useNavigate();
	const location = useLocation();
	const { session, logout } = usePlatformAuth();

	return (
		<Box sx={{ display: "flex" }}>
			<AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
				<Toolbar sx={{ gap: 2 }}>
					<Wordmark size="small" />
					<Chip label="PLATFORM" size="small" variant="outlined" sx={{ borderColor: colors.accent, color: colors.accent }} />
					<Box sx={{ flexGrow: 1 }} />
					<Typography variant="body2" sx={{ color: colors.inkSoft }}>
						{session?.email}
					</Typography>
					<Button size="small" startIcon={<LogoutIcon fontSize="small" />} onClick={logout}>
						Sign out
					</Button>
				</Toolbar>
			</AppBar>
			<Drawer
				variant="permanent"
				sx={{
					width: DRAWER_WIDTH,
					flexShrink: 0,
					[`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: "border-box" },
				}}
			>
				<Toolbar />
				<Divider />
				<List>
					{NAV_ITEMS.map((item) => (
						<ListItemButton key={item.path} selected={location.pathname.startsWith(item.path)} onClick={() => navigate(item.path)}>
							<ListItemIcon>{item.icon}</ListItemIcon>
							<ListItemText primary={item.label} />
						</ListItemButton>
					))}
				</List>
			</Drawer>
			<Box component="main" sx={{ flexGrow: 1, p: { xs: 2, md: 4 } }}>
				<Toolbar />
				<Box sx={{ maxWidth: 1280, mx: "auto" }}>
					<Outlet />
				</Box>
			</Box>
		</Box>
	);
}
