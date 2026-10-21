import { useNavigate } from "react-router-dom";
import ButtonBase from "@mui/material/ButtonBase";
import IconButton from "@mui/material/IconButton";
import Box from "@mui/material/Box";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import BoltIcon from "@mui/icons-material/Bolt";
import CampaignIcon from "@mui/icons-material/Campaign";
import CloseIcon from "@mui/icons-material/Close";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import GroupAddIcon from "@mui/icons-material/GroupAdd";
import PaymentsIcon from "@mui/icons-material/Payments";
import PersonAddAlt1Icon from "@mui/icons-material/PersonAddAlt1";
import AssessmentIcon from "@mui/icons-material/Assessment";
import BadgeIcon from "@mui/icons-material/Badge";
import { useAuth } from "../../auth/AuthContext";
import { SectionCard, type Tint } from "../../components/DashboardKit";
import { colors } from "../../theme";

interface Action {
	label: string;
	path: string;
	icon: React.ReactNode;
	tint: Tint;
	/** Gated the same way sidebar nav items are - this is a shortcut into an existing
	 * flow, not a separate permission surface (#124). */
	requiredPermissions: string[];
}

const TINT_BG: Record<Tint, string> = {
	violet: colors.tintViolet,
	green: colors.tintGreen,
	orange: colors.tintOrange,
	pink: colors.tintPink,
	blue: colors.tintBlue,
};
const TINT_INK: Record<Tint, string> = { violet: colors.accent, green: colors.ok, orange: colors.warn, pink: colors.bad, blue: colors.info };

const ACTIONS: Action[] = [
	{ label: "Take attendance", path: "/attendance/mark", icon: <EventAvailableIcon fontSize="small" />, tint: "green", requiredPermissions: ["ATTENDANCE_MARK"] },
	{ label: "Add student", path: "/students/new", icon: <PersonAddAlt1Icon fontSize="small" />, tint: "blue", requiredPermissions: ["STUDENT_MANAGE"] },
	{ label: "Fee collection", path: "/fees/collect", icon: <PaymentsIcon fontSize="small" />, tint: "orange", requiredPermissions: ["FEE_COLLECT"] },
	{ label: "Generate report", path: "/reports", icon: <AssessmentIcon fontSize="small" />, tint: "violet", requiredPermissions: ["REPORT_CREATE", "REPORT_MANAGE"] },
	{ label: "Create notice", path: "/communication", icon: <CampaignIcon fontSize="small" />, tint: "pink", requiredPermissions: ["ANNOUNCEMENT_CREATE"] },
	{ label: "Add staff", path: "/hr/staff/new", icon: <BadgeIcon fontSize="small" />, tint: "blue", requiredPermissions: ["HR_STAFF_MANAGE"] },
	{ label: "Invite member", path: "/members/invite", icon: <GroupAddIcon fontSize="small" />, tint: "violet", requiredPermissions: ["MEMBERSHIP_MANAGE"] },
];

interface QuickActionsProps {
	onDismiss: () => void;
}

/** Shortcuts into the most common flows - reachable normally via nav too, this just puts
 * them one click from the dashboard landing (#97). Each shortcut is gated by the same
 * permission its target flow itself requires (#124) - no assumption about which role
 * "should" do these, purely the org's own configured permissions. Dismissible (permanent
 * per-user) for anyone who already knows the system. */
export function QuickActions({ onDismiss }: QuickActionsProps) {
	const navigate = useNavigate();
	const { hasAnyPermission, isPathEnabled } = useAuth();
	const actions = ACTIONS.filter((action) => isPathEnabled(action.path) && hasAnyPermission(action.requiredPermissions)).slice(0, 6);

	if (actions.length === 0) {
		return null;
	}

	return (
		<SectionCard
			title="Quick actions"
			icon={<BoltIcon fontSize="small" />}
			tint="violet"
			headerExtra={
				<Tooltip title="Hide this - you can always reach these from the sidebar">
					<IconButton size="small" onClick={onDismiss} aria-label="Dismiss quick actions">
						<CloseIcon fontSize="small" />
					</IconButton>
				</Tooltip>
			}
		>
			<Box sx={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 1.25 }}>
				{actions.map((action) => (
					<ButtonBase
						key={action.path}
						onClick={() => navigate(action.path)}
						sx={{
							display: "flex",
							alignItems: "center",
							justifyContent: "flex-start",
							gap: 1.25,
							p: 1.5,
							borderRadius: "12px",
							textAlign: "left",
							color: TINT_INK[action.tint],
							backgroundColor: TINT_BG[action.tint],
							transition: "transform 120ms",
							"&:hover": { transform: "translateY(-1px)" },
						}}
					>
						{action.icon}
						<Typography variant="body2" sx={{ fontWeight: 700, color: colors.ink, lineHeight: 1.2 }}>
							{action.label}
						</Typography>
					</ButtonBase>
				))}
			</Box>
		</SectionCard>
	);
}
