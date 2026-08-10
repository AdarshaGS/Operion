import { Outlet, useLocation, useNavigate } from "react-router-dom";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Drawer from "@mui/material/Drawer";
import IconButton from "@mui/material/IconButton";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Toolbar from "@mui/material/Toolbar";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import SchoolIcon from "@mui/icons-material/School";
import ClassIcon from "@mui/icons-material/Class";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import PaymentsIcon from "@mui/icons-material/Payments";
import AssignmentIcon from "@mui/icons-material/Assignment";
import CampaignIcon from "@mui/icons-material/Campaign";
import DirectionsBusIcon from "@mui/icons-material/DirectionsBus";
import MenuBookIcon from "@mui/icons-material/MenuBook";
import Inventory2Icon from "@mui/icons-material/Inventory2";
import BadgeIcon from "@mui/icons-material/Badge";
import SettingsIcon from "@mui/icons-material/Settings";
import LogoutIcon from "@mui/icons-material/Logout";
import { useAuth } from "../auth/AuthContext";

const DRAWER_WIDTH = 240;

interface NavItem {
	label: string;
	path: string;
	icon: React.ReactNode;
	/** Screens for these modules aren't built yet - shown so the intended portal shape is visible from day one. */
	enabled: boolean;
}

const NAV_ITEMS: NavItem[] = [
	{ label: "Students", path: "/students", icon: <SchoolIcon />, enabled: true },
	{ label: "Academics", path: "/academics", icon: <ClassIcon />, enabled: true },
	{ label: "Attendance", path: "/attendance", icon: <EventAvailableIcon />, enabled: true },
	{ label: "Fees", path: "/fees", icon: <PaymentsIcon />, enabled: true },
	{ label: "Examinations", path: "/examinations", icon: <AssignmentIcon />, enabled: true },
	{ label: "Communication", path: "/communication", icon: <CampaignIcon />, enabled: true },
	{ label: "Transport", path: "/transport", icon: <DirectionsBusIcon />, enabled: true },
	{ label: "Library", path: "/library", icon: <MenuBookIcon />, enabled: true },
	{ label: "Inventory", path: "/inventory", icon: <Inventory2Icon />, enabled: true },
	{ label: "HR", path: "/hr", icon: <BadgeIcon />, enabled: true },
];

/** Foundation-owned config (Campus/AcademicYear), not a domain module - kept in its own
 * nav group below a divider rather than mixed into the module list above. */
const SETTINGS_ITEM: NavItem = { label: "Settings", path: "/settings", icon: <SettingsIcon />, enabled: true };

export function AppLayout() {
	const navigate = useNavigate();
	const location = useLocation();
	const { logout } = useAuth();

	return (
		<Box sx={{ display: "flex" }}>
			<AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
				<Toolbar>
					<Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1 }}>
						Operion
					</Typography>
					<Tooltip title="Sign out">
						<IconButton color="inherit" onClick={logout}>
							<LogoutIcon />
						</IconButton>
					</Tooltip>
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
						<Tooltip key={item.path} title={item.enabled ? "" : "Coming soon"} placement="right">
							<span>
								<ListItemButton
									selected={location.pathname.startsWith(item.path)}
									disabled={!item.enabled}
									onClick={() => navigate(item.path)}
								>
									<ListItemIcon>{item.icon}</ListItemIcon>
									<ListItemText primary={item.label} />
								</ListItemButton>
							</span>
						</Tooltip>
					))}
				</List>
				<Divider />
				<List>
					<ListItemButton selected={location.pathname.startsWith(SETTINGS_ITEM.path)} onClick={() => navigate(SETTINGS_ITEM.path)}>
						<ListItemIcon>{SETTINGS_ITEM.icon}</ListItemIcon>
						<ListItemText primary={SETTINGS_ITEM.label} />
					</ListItemButton>
				</List>
			</Drawer>
			<Box component="main" sx={{ flexGrow: 1, p: 3 }}>
				<Toolbar />
				<Outlet />
			</Box>
		</Box>
	);
}
