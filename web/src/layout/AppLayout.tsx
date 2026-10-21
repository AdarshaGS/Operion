import { useCallback, useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Stack from "@mui/material/Stack";
import DashboardIcon from "@mui/icons-material/Dashboard";
import SchoolIcon from "@mui/icons-material/School";
import HowToRegIcon from "@mui/icons-material/HowToReg";
import ClassIcon from "@mui/icons-material/Class";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import PaymentsIcon from "@mui/icons-material/Payments";
import AssignmentIcon from "@mui/icons-material/Assignment";
import CampaignIcon from "@mui/icons-material/Campaign";
import ChatIcon from "@mui/icons-material/Chat";
import DirectionsBusIcon from "@mui/icons-material/DirectionsBus";
import MenuBookIcon from "@mui/icons-material/MenuBook";
import AutoStoriesIcon from "@mui/icons-material/AutoStories";
import Inventory2Icon from "@mui/icons-material/Inventory2";
import ShoppingCartIcon from "@mui/icons-material/ShoppingCart";
import PointOfSaleIcon from "@mui/icons-material/PointOfSale";
import BadgeIcon from "@mui/icons-material/Badge";
import RequestQuoteIcon from "@mui/icons-material/RequestQuote";
import AssessmentIcon from "@mui/icons-material/Assessment";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";
import GroupIcon from "@mui/icons-material/Group";
import SettingsIcon from "@mui/icons-material/Settings";
import { useAuth } from "../auth/AuthContext";
import { getDashboardSummary, type SetupChecklist } from "../api/dashboard";
import { searchStudents } from "../api/students";
import { AppShell, type ShellNavGroup, type ShellNavItem } from "./AppShell";
import { ContextSelectors } from "./ContextSelectors";
import { NotificationBell } from "./NotificationBell";
import { ProfileMenu } from "./ProfileMenu";
import { GlobalSearch, type SearchOption } from "./TopbarParts";

const SIDEBAR_COLLAPSED_KEY = "operion.sidebarCollapsed";
const NAV_GROUPS_COLLAPSED_KEY = "operion.navGroupsCollapsed";

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

/** Pinned above every collapsible group, ungrouped - it's overall activity across the whole
 * app, not an academics-specific view, so it doesn't belong inside the Academics section. */
const DASHBOARD_ITEM: NavItem = {
	label: "Dashboard",
	path: "/dashboard",
	icon: <DashboardIcon />,
	built: true,
	requiredPermissions: ["ORGANISATION_MANAGE"],
};

const NAV_GROUPS: NavGroup[] = [
	{
		label: "Academics",
		items: [
			{ label: "Students", path: "/students", icon: <SchoolIcon />, built: true, requiredPermissions: ["STUDENT_VIEW"] },
			{ label: "Admissions", path: "/admissions", icon: <HowToRegIcon />, built: true, requiredPermissions: ["STUDENT_VIEW"] },
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
			{ label: "Learning", path: "/learning", icon: <AutoStoriesIcon />, built: true, requiredPermissions: ["LEARNING_VIEW", "LESSON_PLAN_REVIEW"] },
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
			{ label: "Messaging", path: "/messaging", icon: <ChatIcon />, built: true, requiredPermissions: ["COMMUNICATION_VIEW"] },
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
			{ label: "Payroll", path: "/payroll", icon: <RequestQuoteIcon />, built: true, requiredPermissions: ["PAYROLL_VIEW"] },
			{ label: "Performance", path: "/performance", icon: <TrendingUpIcon />, built: true, requiredPermissions: ["KPI_VIEW"] },
			{ label: "Members", path: "/members", icon: <GroupIcon />, built: true, requiredPermissions: ["MEMBERSHIP_VIEW"] },
			/** Foundation-owned config (Campus/AcademicYear), not a domain module - kept here rather than mixed into
			 * a module group. Campus/AcademicYear mutation endpoints reuse ORGANISATION_MANAGE but their listing
			 * endpoints are deliberately ungated, so this item is never permission-gated. */
			{ label: "Settings", path: "/settings", icon: <SettingsIcon />, built: true, requiredPermissions: [] },
		],
	},
];

export function AppLayout() {
	const location = useLocation();
	const { hasAnyPermission, hasPermission, permissionsLoaded, profile, isPathEnabled } = useAuth();
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

	function toShellItem(item: NavItem): ShellNavItem {
		const permitted = isPermitted(item);
		const blocked = permitted && setupBlocked(item);
		const enabled = item.built && permitted && !blocked;
		const reason = !item.built
			? "Coming soon"
			: !permitted
				? "You don't have permission to view this"
				: blocked
					? (item.setupGate as NonNullable<NavItem["setupGate"]>).message
					: undefined;
		return { label: item.label, path: item.path, icon: item.icon, disabled: !enabled, reason };
	}

	// A module the platform owner switched off, or one the caller has no permission for,
	// disappears entirely (not just greyed out) so people only see what is theirs to use.
	// Backend enforces both on every call, this only stops the UI offering dead ends.
	const groups: ShellNavGroup[] = [
		...(isPathEnabled(DASHBOARD_ITEM.path) ? [{ label: null, items: [toShellItem(DASHBOARD_ITEM)] }] : []),
		...NAV_GROUPS.map((group) => ({
			label: group.label,
			items: group.items.filter((item) => isPathEnabled(item.path) && isPermitted(item)).map(toShellItem),
		})).filter((group) => group.items.length > 0),
	];
	const currentModuleOff = !isPathEnabled(location.pathname);

	const searchPages = groups.flatMap((group) => group.items).filter((item) => !item.disabled).map((item) => ({ label: item.label, path: item.path }));
	const canSearchStudents = isPathEnabled("/students") && (!permissionsLoaded || hasPermission("STUDENT_VIEW"));
	const lookupStudents = useCallback(
		async (query: string): Promise<SearchOption[]> => {
			if (!canSearchStudents) return [];
			const page = await searchStudents({ search: query, size: 5 });
			return page.content.map((student) => ({
				label: [student.firstName, student.lastName].filter(Boolean).join(" "),
				sublabel: [student.studentId, student.schoolClassDisplayName].filter(Boolean).join(" · "),
				path: `/students/${student.id}`,
				group: "Students",
			}));
		},
		[canSearchStudents],
	);

	return (
		<AppShell
			subtitle={profile?.organisationName ?? "School ERP"}
			groups={groups}
			collapsedKey={SIDEBAR_COLLAPSED_KEY}
			groupsKey={NAV_GROUPS_COLLAPSED_KEY}
			homePath="/"
			override={
				currentModuleOff ? (
					<Alert severity="info">This feature is not enabled for your organisation. Contact your administrator if you need it.</Alert>
				) : undefined
			}
			topbar={
				<>
					<ContextSelectors search={<GlobalSearch pages={searchPages} lookup={canSearchStudents ? lookupStudents : undefined} />} />
					<Stack direction="row" spacing={1} sx={{ alignItems: "center", flexShrink: 0 }}>
						<NotificationBell />
						<ProfileMenu />
					</Stack>
				</>
			}
		/>
	);
}
