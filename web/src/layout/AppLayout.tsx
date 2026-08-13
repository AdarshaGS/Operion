import { Outlet, useLocation, useNavigate } from "react-router-dom";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Drawer from "@mui/material/Drawer";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Toolbar from "@mui/material/Toolbar";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import { Wordmark } from "../branding/Wordmark";
import { colors } from "../theme";
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
import { useAuth } from "../auth/AuthContext";
import { ProfileMenu } from "./ProfileMenu";

const DRAWER_WIDTH = 240;

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
	{ label: "HR", path: "/hr", icon: <BadgeIcon />, built: true, requiredPermissions: ["HR_VIEW"] },
];

/** Foundation-owned config (Campus/AcademicYear), not a domain module - kept in its own
 * nav group below a divider rather than mixed into the module list above. Campus/AcademicYear
 * mutation endpoints reuse ORGANISATION_MANAGE but their listing endpoints are deliberately
 * ungated (reachable by any authenticated org member), so this nav item is never permission-gated. */
const SETTINGS_ITEM: NavItem = { label: "Settings", path: "/settings", icon: <SettingsIcon />, built: true, requiredPermissions: [] };

export function AppLayout() {
	const navigate = useNavigate();
	const location = useLocation();
	const { profile, hasAnyPermission, permissionsLoaded } = useAuth();

	// While permissions are still loading, don't gate on them - enforcement is backend-side
	// regardless, this is UX sugar to avoid a flash of every nav item looking unauthorized.
	const isPermitted = (item: NavItem) => item.requiredPermissions.length === 0 || !permissionsLoaded || hasAnyPermission(item.requiredPermissions);

	return (
		<Box sx={{ display: "flex" }}>
			<AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
				<Toolbar sx={{ gap: 2 }}>
					<Wordmark size="small" />
					{profile?.organisationName && (
						<Typography
							variant="body2"
							sx={{ color: colors.inkSoft, pl: 1.5, ml: 0.5, borderLeft: `1px solid ${colors.rule}` }}
						>
							{profile.organisationName}
						</Typography>
					)}
					<Box sx={{ flexGrow: 1 }} />
					<ProfileMenu />
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
					{NAV_ITEMS.map((item) => {
						const permitted = isPermitted(item);
						const enabled = item.built && permitted;
						const tooltip = !item.built ? "Coming soon" : !permitted ? "You don't have permission to view this" : "";
						return (
							<Tooltip key={item.path} title={tooltip} placement="right">
								<span>
									<ListItemButton
										selected={location.pathname.startsWith(item.path)}
										disabled={!enabled}
										onClick={() => navigate(item.path)}
									>
										<ListItemIcon>{item.icon}</ListItemIcon>
										<ListItemText primary={item.label} />
									</ListItemButton>
								</span>
							</Tooltip>
						);
					})}
				</List>
				<Divider />
				<List>
					<ListItemButton selected={location.pathname.startsWith(SETTINGS_ITEM.path)} onClick={() => navigate(SETTINGS_ITEM.path)}>
						<ListItemIcon>{SETTINGS_ITEM.icon}</ListItemIcon>
						<ListItemText primary={SETTINGS_ITEM.label} />
					</ListItemButton>
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
