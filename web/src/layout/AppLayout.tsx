import { useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import ButtonBase from "@mui/material/ButtonBase";
import Divider from "@mui/material/Divider";
import Drawer from "@mui/material/Drawer";
import IconButton from "@mui/material/IconButton";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Toolbar from "@mui/material/Toolbar";
import Tooltip from "@mui/material/Tooltip";
import { Wordmark } from "../branding/Wordmark";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import DashboardIcon from "@mui/icons-material/Dashboard";
import SchoolIcon from "@mui/icons-material/School";
import ClassIcon from "@mui/icons-material/Class";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import PaymentsIcon from "@mui/icons-material/Payments";
import AssignmentIcon from "@mui/icons-material/Assignment";
import CampaignIcon from "@mui/icons-material/Campaign";
import DirectionsBusIcon from "@mui/icons-material/DirectionsBus";
import MenuBookIcon from "@mui/icons-material/MenuBook";
import Inventory2Icon from "@mui/icons-material/Inventory2";
import ShoppingCartIcon from "@mui/icons-material/ShoppingCart";
import PointOfSaleIcon from "@mui/icons-material/PointOfSale";
import BadgeIcon from "@mui/icons-material/Badge";
import AssessmentIcon from "@mui/icons-material/Assessment";
import SettingsIcon from "@mui/icons-material/Settings";
import { useAuth } from "../auth/AuthContext";
import { ProfileMenu } from "./ProfileMenu";

const DRAWER_WIDTH = 240;
const DRAWER_WIDTH_COLLAPSED = 72;
const SIDEBAR_COLLAPSED_KEY = "operion.sidebarCollapsed";

function readStoredCollapsed(): boolean {
	try {
		return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1";
	} catch {
		return false;
	}
}

interface NavItem {
	label: string;
	path: string;
	icon: React.ReactNode;
	/** Screens for these modules aren't built yet - shown so the intended portal shape is visible from day one. */
	built: boolean;
	/** Visible/enabled if the caller holds ANY of these view permissions. Empty = no gate (ungated backend endpoints, e.g. Settings). */
	requiredPermissions: string[];
}

const NAV_ITEMS: NavItem[] = [
	{ label: "Dashboard", path: "/dashboard", icon: <DashboardIcon />, built: true, requiredPermissions: ["ORGANISATION_MANAGE"] },
	{ label: "Students", path: "/students", icon: <SchoolIcon />, built: true, requiredPermissions: ["STUDENT_VIEW"] },
	{
		label: "Academics",
		path: "/academics",
		icon: <ClassIcon />,
		built: true,
		requiredPermissions: ["CLASS_VIEW", "GRADE_LEVEL_VIEW", "SUBJECT_VIEW", "TEACHER_ASSIGNMENT_VIEW"],
	},
	{
		label: "Attendance",
		path: "/attendance",
		icon: <EventAvailableIcon />,
		built: true,
		requiredPermissions: ["ATTENDANCE_VIEW", "STAFF_ATTENDANCE_VIEW"],
	},
	{ label: "Fees", path: "/fees", icon: <PaymentsIcon />, built: true, requiredPermissions: ["FEE_VIEW"] },
	{ label: "Examinations", path: "/examinations", icon: <AssignmentIcon />, built: true, requiredPermissions: ["EXAM_VIEW"] },
	{ label: "Communication", path: "/communication", icon: <CampaignIcon />, built: true, requiredPermissions: ["COMMUNICATION_VIEW"] },
	{ label: "Transport", path: "/transport", icon: <DirectionsBusIcon />, built: true, requiredPermissions: ["TRANSPORT_VIEW"] },
	{ label: "Library", path: "/library", icon: <MenuBookIcon />, built: true, requiredPermissions: ["LIBRARY_VIEW"] },
	{ label: "Inventory", path: "/inventory", icon: <Inventory2Icon />, built: true, requiredPermissions: ["INVENTORY_VIEW"] },
	{ label: "Purchase", path: "/purchase", icon: <ShoppingCartIcon />, built: true, requiredPermissions: ["PURCHASE_VIEW"] },
	{ label: "Sales", path: "/sales", icon: <PointOfSaleIcon />, built: true, requiredPermissions: ["SALES_VIEW"] },
	{ label: "HR", path: "/hr", icon: <BadgeIcon />, built: true, requiredPermissions: ["HR_VIEW"] },
	{ label: "Reports", path: "/reports", icon: <AssessmentIcon />, built: true, requiredPermissions: ["REPORT_CREATE", "REPORT_MANAGE"] },
];

/** Foundation-owned config (Campus/AcademicYear), not a domain module - kept in its own
 * nav group below a divider rather than mixed into the module list above. Campus/AcademicYear
 * mutation endpoints reuse ORGANISATION_MANAGE but their listing endpoints are deliberately
 * ungated (reachable by any authenticated org member), so this nav item is never permission-gated. */
const SETTINGS_ITEM: NavItem = { label: "Settings", path: "/settings", icon: <SettingsIcon />, built: true, requiredPermissions: [] };

export function AppLayout() {
	const navigate = useNavigate();
	const location = useLocation();
	const { hasAnyPermission, permissionsLoaded } = useAuth();
	const [collapsed, setCollapsed] = useState(readStoredCollapsed);

	// While permissions are still loading, don't gate on them - enforcement is backend-side
	// regardless, this is UX sugar to avoid a flash of every nav item looking unauthorized.
	const isPermitted = (item: NavItem) => item.requiredPermissions.length === 0 || !permissionsLoaded || hasAnyPermission(item.requiredPermissions);

	function toggleCollapsed() {
		setCollapsed((prev) => {
			const next = !prev;
			try {
				localStorage.setItem(SIDEBAR_COLLAPSED_KEY, next ? "1" : "0");
			} catch {
				// per-viewer convenience only - fine to no-op when storage is unavailable
			}
			return next;
		});
	}

	const drawerWidth = collapsed ? DRAWER_WIDTH_COLLAPSED : DRAWER_WIDTH;

	return (
		<Box sx={{ display: "flex" }}>
			<AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
				<Toolbar sx={{ gap: 2 }}>
					<ButtonBase onClick={() => navigate("/")} disableRipple sx={{ borderRadius: 1 }} aria-label="Go to home">
						<Wordmark size="small" />
					</ButtonBase>
					<Box sx={{ flexGrow: 1 }} />
					<ProfileMenu />
				</Toolbar>
			</AppBar>
			<Drawer
				variant="permanent"
				sx={{
					width: drawerWidth,
					flexShrink: 0,
					transition: (theme) => theme.transitions.create("width", { duration: theme.transitions.duration.shortest }),
					[`& .MuiDrawer-paper`]: {
						width: drawerWidth,
						boxSizing: "border-box",
						overflowX: "hidden",
						transition: (theme) => theme.transitions.create("width", { duration: theme.transitions.duration.shortest }),
					},
				}}
			>
				<Toolbar />
				<Box sx={{ display: "flex", justifyContent: collapsed ? "center" : "flex-end", px: 1, py: 0.5 }}>
					<Tooltip title={collapsed ? "Expand sidebar" : "Collapse sidebar"} placement="right">
						<IconButton size="small" onClick={toggleCollapsed} aria-label="Toggle sidebar">
							{collapsed ? <ChevronRightIcon fontSize="small" /> : <ChevronLeftIcon fontSize="small" />}
						</IconButton>
					</Tooltip>
				</Box>
				<Divider />
				<List>
					{NAV_ITEMS.map((item) => {
						const permitted = isPermitted(item);
						const enabled = item.built && permitted;
						const reason = !item.built ? "Coming soon" : !permitted ? "You don't have permission to view this" : "";
						const tooltip = collapsed ? (reason ? `${item.label} — ${reason}` : item.label) : reason;
						return (
							<Tooltip key={item.path} title={tooltip} placement="right">
								<span>
									<ListItemButton
										selected={location.pathname.startsWith(item.path)}
										disabled={!enabled}
										onClick={() => navigate(item.path)}
										sx={{ justifyContent: collapsed ? "center" : "flex-start", px: collapsed ? 1.5 : 2 }}
									>
										<ListItemIcon sx={{ minWidth: collapsed ? 0 : 40, justifyContent: "center" }}>{item.icon}</ListItemIcon>
										{!collapsed && <ListItemText primary={item.label} />}
									</ListItemButton>
								</span>
							</Tooltip>
						);
					})}
				</List>
				<Divider />
				<List>
					<Tooltip title={collapsed ? SETTINGS_ITEM.label : ""} placement="right">
						<span>
							<ListItemButton
								selected={location.pathname.startsWith(SETTINGS_ITEM.path)}
								onClick={() => navigate(SETTINGS_ITEM.path)}
								sx={{ justifyContent: collapsed ? "center" : "flex-start", px: collapsed ? 1.5 : 2 }}
							>
								<ListItemIcon sx={{ minWidth: collapsed ? 0 : 40, justifyContent: "center" }}>{SETTINGS_ITEM.icon}</ListItemIcon>
								{!collapsed && <ListItemText primary={SETTINGS_ITEM.label} />}
							</ListItemButton>
						</span>
					</Tooltip>
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
