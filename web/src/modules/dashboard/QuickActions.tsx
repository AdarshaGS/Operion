import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import GroupAddIcon from "@mui/icons-material/GroupAdd";
import PaymentsIcon from "@mui/icons-material/Payments";
import PersonAddAlt1Icon from "@mui/icons-material/PersonAddAlt1";
import { useAuth } from "../../auth/AuthContext";
import { colors } from "../../theme";

interface Action {
	label: string;
	description: string;
	path: string;
	icon: React.ReactNode;
	/** Gated the same way sidebar nav items are - this is a shortcut into an existing
	 * flow, not a separate permission surface (#124). */
	requiredPermission: string;
}

const ACTIONS: Action[] = [
	{
		label: "Add student",
		description: "Create a new student record",
		path: "/students/new",
		icon: <PersonAddAlt1Icon fontSize="small" />,
		requiredPermission: "STUDENT_MANAGE",
	},
	{
		label: "Mark attendance",
		description: "Take attendance for today",
		path: "/attendance/mark",
		icon: <EventAvailableIcon fontSize="small" />,
		requiredPermission: "ATTENDANCE_MARK",
	},
	{
		label: "Collect fee",
		description: "Record a fee payment",
		path: "/fees/collect",
		icon: <PaymentsIcon fontSize="small" />,
		requiredPermission: "FEE_COLLECT",
	},
	{
		label: "Invite member",
		description: "Add staff or teacher to the team",
		path: "/members/invite",
		icon: <GroupAddIcon fontSize="small" />,
		requiredPermission: "MEMBERSHIP_MANAGE",
	},
];

/** Shortcuts into the four most common admin flows - reachable normally via nav too,
 * this just puts them one click from the dashboard landing (#97). Each shortcut is
 * gated by the same permission its target flow itself requires (#124) - no assumption
 * about which role "should" do these, purely the org's own configured permissions. */
export function QuickActions() {
	const navigate = useNavigate();
	const { hasAnyPermission } = useAuth();
	const actions = ACTIONS.filter((action) => hasAnyPermission([action.requiredPermission]));

	if (actions.length === 0) {
		return null;
	}

	return (
		<Paper sx={{ p: 3, height: "100%" }}>
			<Stack spacing={1.5}>
				<Typography variant="subtitle1">Quick actions</Typography>
				<Stack spacing={1}>
					{actions.map((action) => (
						<Box
							key={action.path}
							onClick={() => navigate(action.path)}
							sx={{
								display: "flex",
								alignItems: "center",
								gap: 1.5,
								p: 1.25,
								borderRadius: 2,
								border: `1px solid ${colors.rule}`,
								cursor: "pointer",
								"&:hover": { bgcolor: colors.paperSunken },
							}}
						>
							<Box
								sx={{
									width: 36,
									height: 36,
									borderRadius: 1.5,
									display: "flex",
									alignItems: "center",
									justifyContent: "center",
									flexShrink: 0,
									color: colors.accent,
									backgroundColor: colors.accentSoft,
								}}
							>
								{action.icon}
							</Box>
							<Box sx={{ flexGrow: 1 }}>
								<Typography variant="body2" sx={{ color: colors.ink, fontWeight: 700 }}>
									{action.label}
								</Typography>
								<Typography variant="caption" sx={{ color: colors.inkSoft }}>
									{action.description}
								</Typography>
							</Box>
							<ChevronRightIcon fontSize="small" sx={{ color: colors.inkFaint, flexShrink: 0 }} />
						</Box>
					))}
				</Stack>
			</Stack>
		</Paper>
	);
}
