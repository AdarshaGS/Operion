import { useCallback, useRef, useState, type MouseEvent } from "react";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import ListItemIcon from "@mui/material/ListItemIcon";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import ApartmentIcon from "@mui/icons-material/Apartment";
import BarChartIcon from "@mui/icons-material/BarChart";
import CardMembershipIcon from "@mui/icons-material/CardMembership";
import DarkModeIcon from "@mui/icons-material/DarkMode";
import DashboardIcon from "@mui/icons-material/Dashboard";
import LightModeIcon from "@mui/icons-material/LightMode";
import LogoutIcon from "@mui/icons-material/Logout";
import MonitorHeartIcon from "@mui/icons-material/MonitorHeart";
import PaymentsIcon from "@mui/icons-material/Payments";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import SellIcon from "@mui/icons-material/Sell";
import SettingsBrightnessIcon from "@mui/icons-material/SettingsBrightness";
import SettingsIcon from "@mui/icons-material/Settings";
import SupportAgentIcon from "@mui/icons-material/SupportAgent";
import TimelineIcon from "@mui/icons-material/Timeline";
import ViewKanbanIcon from "@mui/icons-material/ViewKanban";
import { AppShell, type ShellNavGroup } from "../../layout/AppShell";
import { AccountButton, GlobalSearch, type SearchOption } from "../../layout/TopbarParts";
import { colors } from "../../theme";
import { useThemeMode, type ThemePreference } from "../../theme-mode";
import { listOrganisations, type OrganisationResponse } from "../api/organisations";
import { usePlatformAuth } from "../auth/PlatformAuthContext";

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
		items: [
			{ label: "Leads", path: "/platform/crm/leads", icon: <ViewKanbanIcon />, built: true },
			{ label: "Organisations", path: "/platform/organisations", icon: <ApartmentIcon />, built: true },
		],
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
			{ label: "Usage", path: "/platform/usage", icon: <BarChartIcon />, built: true },
			{ label: "Activity", path: "/platform/activity", icon: <TimelineIcon />, built: true },
			{ label: "System Health", path: "/platform/system-health", icon: <MonitorHeartIcon />, built: true },
		],
	},
	{
		label: "Support",
		items: [{ label: "Support / Issues", path: "/platform/support", icon: <SupportAgentIcon />, built: true }],
	},
	{
		label: "Settings",
		items: [{ label: "Platform Settings", path: "/platform/settings", icon: <SettingsIcon />, built: true }],
	},
];

/** Same chrome as the school app (AppShell) but wired to the platform auth plane, not the school
 * org one - kept as a separate component tree rather than parameterizing AppLayout,
 * since the two auth contexts (usePlatformAuth vs useAuth) are deliberately unrelated. */
export function PlatformLayout() {
	const { session, logout } = usePlatformAuth();
	const { preference, setPreference } = useThemeMode();
	const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
	// Organisations are fetched once, on the first search, and filtered client-side.
	const organisationsRef = useRef<Promise<OrganisationResponse[]> | null>(null);

	const groups: ShellNavGroup[] = [
		{ label: null, items: [{ label: "Dashboard", path: "/platform/dashboard", icon: <DashboardIcon /> }] },
		...NAV_GROUPS.map((group) => ({
			label: group.label,
			items: group.items.map((item) => ({
				label: item.label,
				path: item.path,
				icon: item.icon,
				disabled: !item.built,
				reason: item.built ? undefined : "Coming soon",
			})),
		})),
	];
	const searchPages = groups.flatMap((group) => group.items).filter((item) => !item.disabled).map((item) => ({ label: item.label, path: item.path }));

	const lookupOrganisations = useCallback(async (query: string): Promise<SearchOption[]> => {
		organisationsRef.current ??= listOrganisations();
		const organisations = await organisationsRef.current;
		const needle = query.toLowerCase();
		return organisations
			.filter((org) => org.name.toLowerCase().includes(needle) || org.slug.toLowerCase().includes(needle))
			.slice(0, 5)
			.map((org) => ({ label: org.name, sublabel: `${org.slug} · ${org.status}`, path: `/platform/organisations/${org.id}`, group: "Organisations" }));
	}, []);

	return (
		<>
			<AppShell
				subtitle="Platform console"
				groups={groups}
				collapsedKey="operion.platformSidebarCollapsed"
				groupsKey="operion.platformNavGroupsCollapsed"
				homePath="/platform/dashboard"
				topbar={
					<>
						<Chip label="PLATFORM" size="small" sx={{ fontWeight: 700, letterSpacing: "0.06em", color: colors.accent, backgroundColor: colors.accentSoft, flexShrink: 0 }} />
						<GlobalSearch placeholder="Search organisations, pages, anything..." pages={searchPages} lookup={lookupOrganisations} />
						<Box sx={{ ml: "auto", flexShrink: 0 }}>
							<AccountButton name="Platform admin" subtitle="Platform owner" onClick={(event: MouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget)} />
						</Box>
					</>
				}
			/>
			<Menu anchorEl={anchorEl} open={anchorEl !== null} onClose={() => setAnchorEl(null)}>
				<Box sx={{ px: 2, py: 1, minWidth: 220 }}>
					<Typography variant="subtitle1">Platform owner</Typography>
					<Typography variant="body2" color="text.secondary">
						{session?.email ?? "—"}
					</Typography>
				</Box>
				<Divider />
				<Box sx={{ px: 2, py: 1 }}>
					<Typography variant="caption" sx={{ display: "block", mb: 0.75, fontWeight: 700, letterSpacing: "0.06em", color: colors.inkFaint }}>
						THEME
					</Typography>
					<ToggleButtonGroup
						value={preference}
						exclusive
						size="small"
						fullWidth
						onChange={(_, next: ThemePreference | null) => {
							if (next) setPreference(next);
						}}
						aria-label="Theme preference"
					>
						<ToggleButton value="light" aria-label="Light theme">
							<LightModeIcon fontSize="small" />
						</ToggleButton>
						<ToggleButton value="system" aria-label="Match system theme">
							<SettingsBrightnessIcon fontSize="small" />
						</ToggleButton>
						<ToggleButton value="dark" aria-label="Dark theme">
							<DarkModeIcon fontSize="small" />
						</ToggleButton>
					</ToggleButtonGroup>
				</Box>
				<Divider />
				<MenuItem onClick={logout}>
					<ListItemIcon>
						<LogoutIcon fontSize="small" />
					</ListItemIcon>
					Sign out
				</MenuItem>
			</Menu>
		</>
	);
}
