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
import ListSubheader from "@mui/material/ListSubheader";
import Toolbar from "@mui/material/Toolbar";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import ApartmentIcon from "@mui/icons-material/Apartment";
import BarChartIcon from "@mui/icons-material/BarChart";
import CardMembershipIcon from "@mui/icons-material/CardMembership";
import DashboardIcon from "@mui/icons-material/Dashboard";
import MonitorHeartIcon from "@mui/icons-material/MonitorHeart";
import PaymentsIcon from "@mui/icons-material/Payments";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import SellIcon from "@mui/icons-material/Sell";
import SettingsIcon from "@mui/icons-material/Settings";
import SupportAgentIcon from "@mui/icons-material/SupportAgent";
import TimelineIcon from "@mui/icons-material/Timeline";
import LogoutIcon from "@mui/icons-material/Logout";
import { Wordmark } from "../../branding/Wordmark";
import { colors } from "../../theme";
import { usePlatformAuth } from "../auth/PlatformAuthContext";

const DRAWER_WIDTH = 240;

interface PlatformNavItem {
	label: string;
	path: string;
	icon: React.ReactNode;
	/** Screen isn't built yet - kept visible (disabled, "Coming soon") so the intended
	 * shape of the platform console is visible from day one, same convention as
	 * AppLayout's NavItem.built on the org-facing side. */
	built: boolean;
}

interface PlatformNavGroup {
	label: string;
	items: PlatformNavItem[];
}

const NAV_GROUPS: PlatformNavGroup[] = [
	{
		// No standalone "Integrations" item here - entitlement is per-organisation (see
		// PlatformExternalServiceController), so it lives as a section on each org's own
		// OrganisationDetailPage instead of a single global page.
		label: "Customers",
		items: [{ label: "Organisations", path: "/platform/organisations", icon: <ApartmentIcon />, built: true }],
	},
	{
		label: "Billing",
		items: [
			{ label: "Plans", path: "/platform/plans", icon: <SellIcon />, built: true },
			{ label: "Subscriptions", path: "/platform/subscriptions", icon: <CardMembershipIcon />, built: true },
			{ label: "Invoices", path: "/platform/invoices", icon: <ReceiptLongIcon />, built: true },
			{ label: "Payments", path: "/platform/payments", icon: <PaymentsIcon />, built: true },
		],
	},
	{
		label: "Operations",
		items: [
			{ label: "Usage", path: "/platform/usage", icon: <BarChartIcon />, built: false },
			{ label: "Activity", path: "/platform/activity", icon: <TimelineIcon />, built: true },
			{ label: "System Health", path: "/platform/system-health", icon: <MonitorHeartIcon />, built: false },
		],
	},
	{
		label: "Support",
		items: [{ label: "Support / Issues", path: "/platform/support", icon: <SupportAgentIcon />, built: false }],
	},
	{
		label: "Settings",
		items: [{ label: "Platform Settings", path: "/platform/settings", icon: <SettingsIcon />, built: false }],
	},
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
					<ListItemButton
						selected={location.pathname === "/platform/dashboard"}
						onClick={() => navigate("/platform/dashboard")}
					>
						<ListItemIcon>
							<DashboardIcon />
						</ListItemIcon>
						<ListItemText primary="Dashboard" />
					</ListItemButton>
				</List>
				{NAV_GROUPS.map((group) => (
					<Box key={group.label}>
						<Divider sx={{ my: 0.5 }} />
						<ListSubheader
							component="div"
							disableSticky
							sx={{ fontSize: "0.68rem", fontWeight: 700, letterSpacing: "0.08em", color: colors.inkFaint, backgroundColor: "transparent" }}
						>
							{group.label.toUpperCase()}
						</ListSubheader>
						<List>
							{group.items.map((item) => (
								<Tooltip key={item.path} title={item.built ? "" : "Coming soon"} placement="right">
									<span>
										<ListItemButton
											selected={location.pathname.startsWith(item.path)}
											disabled={!item.built}
											onClick={() => navigate(item.path)}
										>
											<ListItemIcon>{item.icon}</ListItemIcon>
											<ListItemText primary={item.label} />
										</ListItemButton>
									</span>
								</Tooltip>
							))}
						</List>
					</Box>
				))}
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
