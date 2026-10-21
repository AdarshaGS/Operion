import { useState, type ReactNode } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import ButtonBase from "@mui/material/ButtonBase";
import Collapse from "@mui/material/Collapse";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import useMediaQuery from "@mui/material/useMediaQuery";
import type { Theme } from "@mui/material/styles";
import AutoStoriesIcon from "@mui/icons-material/AutoStories";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import MenuOpenIcon from "@mui/icons-material/MenuOpen";
import MenuIcon from "@mui/icons-material/Menu";
import { colors } from "../theme";

const SIDEBAR_WIDTH = 240;
const SIDEBAR_WIDTH_COLLAPSED = 72;
const TOPBAR_HEIGHT = 64;

export interface ShellNavItem {
	label: string;
	path: string;
	icon: ReactNode;
	disabled?: boolean;
	/** Why the item is disabled - shown as a tooltip. */
	reason?: string;
}

export interface ShellNavGroup {
	/** Null = an ungrouped block pinned at the top (the Dashboard link). */
	label: string | null;
	items: ShellNavItem[];
}

function readBool(key: string): boolean {
	try {
		return localStorage.getItem(key) === "1";
	} catch {
		return false;
	}
}

function readGroups(key: string): Record<string, boolean> {
	try {
		const raw = localStorage.getItem(key);
		return raw ? (JSON.parse(raw) as Record<string, boolean>) : {};
	} catch {
		return {};
	}
}

/** The product mark: an open-book tile plus "Operion" and a one-line subtitle (the signed-in
 * organisation for the school side, "Platform console" for the owner side). */
export function ShellBrand({ subtitle, compact = false, onClick }: { subtitle: string; compact?: boolean; onClick?: () => void }) {
	return (
		<ButtonBase onClick={onClick} disableRipple aria-label="Go to home" sx={{ gap: 1.25, borderRadius: 2, minWidth: 0, textAlign: "left", justifyContent: "flex-start" }}>
			<Box
				sx={{
					width: 40,
					height: 40,
					borderRadius: "12px",
					display: "flex",
					alignItems: "center",
					justifyContent: "center",
					flexShrink: 0,
					color: colors.accentInk,
					background: `linear-gradient(135deg, ${colors.accent} 0%, ${colors.accentStrong} 100%)`,
				}}
			>
				<AutoStoriesIcon fontSize="small" />
			</Box>
			{!compact && (
				<Box sx={{ minWidth: 0 }}>
					<Typography sx={{ fontSize: "1.35rem", fontWeight: 800, lineHeight: 1.1, color: colors.ink, letterSpacing: "-0.02em" }}>Operion</Typography>
					<Typography variant="caption" noWrap sx={{ color: colors.inkSoft, display: "block", maxWidth: 150 }}>
						{subtitle}
					</Typography>
				</Box>
			)}
		</ButtonBase>
	);
}

interface AppShellProps {
	/** Shown under the brand name. */
	subtitle: string;
	groups: ShellNavGroup[];
	/** Contents of the top bar, to the right of the sidebar. */
	topbar: ReactNode;
	/** Rendered instead of the routed page (e.g. a "module switched off" notice). */
	override?: ReactNode;
	/** localStorage keys - per-viewer convenience only. */
	collapsedKey: string;
	groupsKey: string;
	homePath: string;
}

/** Shared chrome for both the school app and the platform console: a full-height sidebar
 * carrying the brand and navigation, a top bar to its right, and the routed page below.
 * Callers decide which nav items exist (permissions, module flags) - this only draws them. */
export function AppShell({ subtitle, groups, topbar, override, collapsedKey, groupsKey, homePath }: AppShellProps) {
	const navigate = useNavigate();
	const location = useLocation();
	const [storedCollapsed, setStoredCollapsed] = useState(() => readBool(collapsedKey));
	const [collapsedGroups, setCollapsedGroups] = useState(() => readGroups(groupsKey));
	// Tablet and phone widths always get the icon rail - a full sidebar would squeeze the page.
	const isNarrow = useMediaQuery((theme: Theme) => theme.breakpoints.down("lg"));
	const collapsed = storedCollapsed || isNarrow;
	const width = collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH;

	function toggleCollapsed() {
		setStoredCollapsed((prev) => {
			const next = !prev;
			try {
				localStorage.setItem(collapsedKey, next ? "1" : "0");
			} catch {
				// per-viewer convenience only - fine to no-op when storage is unavailable
			}
			return next;
		});
	}

	function toggleGroup(label: string) {
		setCollapsedGroups((prev) => {
			const next = { ...prev, [label]: !prev[label] };
			try {
				localStorage.setItem(groupsKey, JSON.stringify(next));
			} catch {
				// per-viewer convenience only - fine to no-op when storage is unavailable
			}
			return next;
		});
	}

	function renderItem(item: ShellNavItem) {
		const selected = location.pathname === item.path || location.pathname.startsWith(`${item.path}/`);
		const tooltip = collapsed ? (item.reason ? `${item.label} — ${item.reason}` : item.label) : (item.reason ?? "");
		return (
			<Tooltip key={item.path} title={tooltip} placement="right">
				<span>
					<ButtonBase
						disabled={item.disabled}
						onClick={() => navigate(item.path)}
						aria-current={selected ? "page" : undefined}
						sx={{
							width: "100%",
							height: 44,
							px: collapsed ? 0 : 1.75,
							gap: 1.5,
							justifyContent: collapsed ? "center" : "flex-start",
							borderRadius: "12px",
							fontSize: "0.92rem",
							fontWeight: selected ? 700 : 500,
							color: selected ? colors.navActiveInk : colors.ink,
							background: selected ? colors.navActiveBg : "transparent",
							opacity: item.disabled ? 0.45 : 1,
							transition: "background-color 120ms",
							"&:hover": { background: selected ? colors.navActiveBg : colors.paperSunken },
							"& .MuiSvgIcon-root": { fontSize: 21, color: selected ? colors.navActiveInk : colors.inkSoft },
						}}
					>
						{item.icon}
						{!collapsed && (
							<Box component="span" sx={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
								{item.label}
							</Box>
						)}
					</ButtonBase>
				</span>
			</Tooltip>
		);
	}

	return (
		<Box sx={{ display: "flex", minHeight: "100vh", backgroundColor: colors.paper }}>
			<Box
				component="nav"
				aria-label="Main navigation"
				sx={{
					width,
					flexShrink: 0,
					position: "sticky",
					top: 0,
					height: "100vh",
					display: "flex",
					flexDirection: "column",
					backgroundColor: colors.chrome,
					borderRight: `1px solid ${colors.rule}`,
					transition: (theme) => theme.transitions.create("width", { duration: theme.transitions.duration.shortest }),
					zIndex: (theme) => theme.zIndex.appBar,
				}}
			>
				<Stack direction="row" sx={{ height: TOPBAR_HEIGHT, px: collapsed ? 0 : 2, alignItems: "center", justifyContent: collapsed ? "center" : "space-between", flexShrink: 0 }}>
					<ShellBrand subtitle={subtitle} compact={collapsed} onClick={() => navigate(homePath)} />
					{!collapsed && !isNarrow && (
						<Tooltip title="Collapse sidebar">
							<IconButton size="small" onClick={toggleCollapsed} aria-label="Toggle sidebar" sx={{ color: colors.accent }}>
								<MenuOpenIcon />
							</IconButton>
						</Tooltip>
					)}
				</Stack>
				{collapsed && !isNarrow && (
					<Stack sx={{ alignItems: "center", pb: 0.5 }}>
						<Tooltip title="Expand sidebar" placement="right">
							<IconButton size="small" onClick={toggleCollapsed} aria-label="Toggle sidebar" sx={{ color: colors.accent }}>
								<MenuIcon />
							</IconButton>
						</Tooltip>
					</Stack>
				)}
				<Box sx={{ flexGrow: 1, overflowY: "auto", overflowX: "hidden", px: collapsed ? 1 : 1.5, pb: 2 }}>
					{groups.map((group, index) => {
						const open = collapsed || group.label === null || !collapsedGroups[group.label];
						return (
							<Box key={group.label ?? `group-${index}`} sx={{ mt: index === 0 ? 0.5 : 1.5 }}>
								{group.label !== null && !collapsed && (
									<ButtonBase
										onClick={() => toggleGroup(group.label as string)}
										sx={{
											width: "100%",
											justifyContent: "space-between",
											px: 1.75,
											py: 0.75,
											borderRadius: 1,
											fontSize: "0.68rem",
											fontWeight: 700,
											letterSpacing: "0.1em",
											color: colors.inkFaint,
										}}
									>
										{group.label.toUpperCase()}
										<ExpandMoreIcon
											sx={{
												fontSize: 16,
												transition: "transform 150ms",
												transform: open ? "rotate(0deg)" : "rotate(-90deg)",
											}}
										/>
									</ButtonBase>
								)}
								{group.label !== null && collapsed && index > 0 && <Box sx={{ borderTop: `1px solid ${colors.rule}`, mx: 1, mb: 1 }} />}
								<Collapse in={open} timeout="auto">
									<Stack spacing={0.5}>{group.items.map(renderItem)}</Stack>
								</Collapse>
							</Box>
						);
					})}
				</Box>
			</Box>

			<Box sx={{ flexGrow: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
				<Stack
					component="header"
					direction="row"
					sx={{
						position: "sticky",
						top: 0,
						zIndex: (theme) => theme.zIndex.appBar - 1,
						height: TOPBAR_HEIGHT,
						px: { xs: 1.5, md: 3 },
						gap: { xs: 1, md: 1.5 },
						alignItems: "center",
						backgroundColor: colors.chrome,
						borderBottom: `1px solid ${colors.rule}`,
					}}
				>
					{topbar}
				</Stack>
				<Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: { xs: 2, md: 3 } }}>
					<Box sx={{ maxWidth: 1440, mx: "auto" }}>{override ?? <Outlet />}</Box>
				</Box>
			</Box>
		</Box>
	);
}
