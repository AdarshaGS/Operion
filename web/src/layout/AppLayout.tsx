import { useEffect, useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import ButtonBase from "@mui/material/ButtonBase";
import Collapse from "@mui/material/Collapse";
import Divider from "@mui/material/Divider";
import Drawer from "@mui/material/Drawer";
import IconButton from "@mui/material/IconButton";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import ListSubheader from "@mui/material/ListSubheader";
import Stack from "@mui/material/Stack";
import Toolbar from "@mui/material/Toolbar";
import Tooltip from "@mui/material/Tooltip";
import { Wordmark } from "../branding/Wordmark";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
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
import { getDashboardSummary, type SetupChecklist } from "../api/dashboard";
import { ContextSelectors } from "./ContextSelectors";
import { NotificationBell } from "./NotificationBell";
import { ProfileMenu } from "./ProfileMenu";
import { colors } from "../theme";

const DRAWER_WIDTH = 240;
const DRAWER_WIDTH_COLLAPSED = 72;
const SIDEBAR_COLLAPSED_KEY = "operion.sidebarCollapsed";
const NAV_GROUPS_COLLAPSED_KEY = "operion.navGroupsCollapsed";

function readStoredCollapsed(): boolean {
	try {
		return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1";
	} catch {
		return false;
	}
}

function readStoredGroupsCollapsed(): Record<string, boolean> {
	try {
		const raw = localStorage.getItem(NAV_GROUPS_COLLAPSED_KEY);
		return raw ? (JSON.parse(raw) as Record<string, boolean>) : {};
	} catch {
		return {};
	}
}

function writeStoredGroupsCollapsed(value: Record<string, boolean>) {
	try {
		localStorage.setItem(NAV_GROUPS_COLLAPSED_KEY, JSON.stringify(value));
	} catch {
		// per-viewer convenience only - fine to no-op when storage is unavailable
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
	/** Disabled with a "what to do first" tooltip, distinct from "no permission"/"coming
	 * soon", until this SetupChecklist field is true (#127) - guidance, not a hard block
	 * elsewhere in the app; only set on items with a genuine prerequisite. */
	setupGate?: { field: keyof SetupChecklist; message: string };
}

interface NavGroup {
	label: string;
	items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
	{
		label: "Academics",
		items: [
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
				setupGate: { field: "studentsAdded", message: "Add students first" },
			},
			{ label: "Examinations", path: "/examinations", icon: <AssignmentIcon />, built: true, requiredPermissions: ["EXAM_VIEW"] },
			{ label: "Library", path: "/library", icon: <MenuBookIcon />, built: true, requiredPermissions: ["LIBRARY_VIEW"] },
		],
	},
	{
		label: "Operations",
		items: [
			{
				label: "Fees",
				path: "/fees",
				icon: <PaymentsIcon />,
				built: true,
				requiredPermissions: ["FEE_VIEW"],
				setupGate: { field: "studentsAdded", message: "Add students first" },
			},
			{ label: "Transport", path: "/transport", icon: <DirectionsBusIcon />, built: true, requiredPermissions: ["TRANSPORT_VIEW"] },
			{ label: "Communication", path: "/communication", icon: <CampaignIcon />, built: true, requiredPermissions: ["COMMUNICATION_VIEW"] },
			{ label: "Inventory", path: "/inventory", icon: <Inventory2Icon />, built: true, requiredPermissions: ["INVENTORY_VIEW"] },
			{ label: "Purchase", path: "/purchase", icon: <ShoppingCartIcon />, built: true, requiredPermissions: ["PURCHASE_VIEW"] },
			{ label: "Sales", path: "/sales", icon: <PointOfSaleIcon />, built: true, requiredPermissions: ["SALES_VIEW"] },
			{ label: "Reports", path: "/reports", icon: <AssessmentIcon />, built: true, requiredPermissions: ["REPORT_CREATE", "REPORT_MANAGE"] },
		],
	},
	{
		label: "Administration",
		items: [
			{ label: "HR", path: "/hr", icon: <BadgeIcon />, built: true, requiredPermissions: ["HR_VIEW"] },
			/** Foundation-owned config (Campus/AcademicYear), not a domain module - kept here rather than mixed into
			 * a module group. Campus/AcademicYear mutation endpoints reuse ORGANISATION_MANAGE but their listing
			 * endpoints are deliberately ungated, so this item is never permission-gated. */
			{ label: "Settings", path: "/settings", icon: <SettingsIcon />, built: true, requiredPermissions: [] },
		],
	},
];

export function AppLayout() {
	const navigate = useNavigate();
	const location = useLocation();
	const { hasAnyPermission, permissionsLoaded, profile } = useAuth();
	const [collapsed, setCollapsed] = useState(readStoredCollapsed);
	const [collapsedGroups, setCollapsedGroups] = useState(readStoredGroupsCollapsed);
	const [checklist, setChecklist] = useState<SetupChecklist | null>(null);

	// The summary endpoint is ORGANISATION_MANAGE-gated backend-side - only fetch it for
	// callers who'd actually get a 200, so everyone else just keeps today's two-reason
	// disabled state (permission/coming-soon) with no extra "setup incomplete" gate.
	useEffect(() => {
		if (!permissionsLoaded || !hasAnyPermission(["ORGANISATION_MANAGE"])) {
			return;
		}
		getDashboardSummary()
			.then((summary) => setChecklist(summary.setupChecklist))
			.catch(() => {});
	}, [permissionsLoaded, hasAnyPermission]);

	// While permissions are still loading, don't gate on them - enforcement is backend-side
	// regardless, this is UX sugar to avoid a flash of every nav item looking unauthorized.
	const isPermitted = (item: NavItem) => item.requiredPermissions.length === 0 || !permissionsLoaded || hasAnyPermission(item.requiredPermissions);
	const setupBlocked = (item: NavItem) => item.setupGate !== undefined && checklist !== null && !checklist[item.setupGate.field];

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

	function toggleGroup(label: string) {
		setCollapsedGroups((prev) => {
			const next = { ...prev, [label]: !prev[label] };
			writeStoredGroupsCollapsed(next);
			return next;
		});
	}

	const drawerWidth = collapsed ? DRAWER_WIDTH_COLLAPSED : DRAWER_WIDTH;

	return (
		<Box sx={{ display: "flex" }}>
			<AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
				<Toolbar sx={{ gap: 2 }}>
					<ButtonBase onClick={() => navigate("/")} disableRipple sx={{ borderRadius: 1 }} aria-label="Go to home">
						<Wordmark size="small" text={profile?.organisationName ?? "OPERION"} />
					</ButtonBase>
					<Box sx={{ flexGrow: 1 }} />
					<ContextSelectors />
					<Stack direction="row" spacing={0.5} sx={{ alignItems: "center" }}>
						<NotificationBell />
						<ProfileMenu />
					</Stack>
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
				<Box sx={{ overflowY: "auto" }}>
					{NAV_GROUPS.map((group, groupIndex) => {
						const groupOpen = collapsed || !collapsedGroups[group.label];
						return (
						<Box key={group.label}>
							{groupIndex > 0 && <Divider sx={{ my: 0.5 }} />}
							{!collapsed && (
								<ListSubheader
									component="div"
									disableSticky
									onClick={() => toggleGroup(group.label)}
									sx={{
										display: "flex",
										alignItems: "center",
										justifyContent: "space-between",
										cursor: "pointer",
										lineHeight: "2rem",
										fontSize: "0.68rem",
										fontWeight: 700,
										letterSpacing: "0.08em",
										color: colors.inkFaint,
										backgroundColor: "transparent",
									}}
								>
									{group.label.toUpperCase()}
									<ExpandMoreIcon
										fontSize="small"
										sx={{
											transition: (theme) => theme.transitions.create("transform", { duration: theme.transitions.duration.shortest }),
											transform: groupOpen ? "rotate(0deg)" : "rotate(-90deg)",
										}}
									/>
								</ListSubheader>
							)}
							<Collapse in={groupOpen} timeout="auto">
							<List>
								{group.items.map((item) => {
									const permitted = isPermitted(item);
									const blocked = permitted && setupBlocked(item);
									const enabled = item.built && permitted && !blocked;
									const reason = !item.built
										? "Coming soon"
										: !permitted
											? "You don't have permission to view this"
											: blocked
												? (item.setupGate as NonNullable<NavItem["setupGate"]>).message
												: "";
									const tooltip = collapsed ? (reason ? `${item.label} — ${reason}` : item.label) : reason;
									return (
										<Tooltip key={item.path} title={tooltip} placement="right">
											<span>
												<ListItemButton
													selected={location.pathname.startsWith(item.path)}
													disabled={!enabled}
													onClick={() => navigate(item.path)}
													sx={{ justifyContent: collapsed ? "center" : "flex-start", px: collapsed ? 1.5 : 2, mx: 1, width: "auto" }}
												>
													<ListItemIcon sx={{ minWidth: collapsed ? 0 : 40, justifyContent: "center" }}>{item.icon}</ListItemIcon>
													{!collapsed && <ListItemText primary={item.label} />}
												</ListItemButton>
											</span>
										</Tooltip>
									);
								})}
							</List>
							</Collapse>
						</Box>
						);
					})}
				</Box>
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
