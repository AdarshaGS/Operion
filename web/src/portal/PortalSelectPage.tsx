import { useNavigate } from "react-router-dom";
import FamilyRestroomIcon from "@mui/icons-material/FamilyRestroom";
import SchoolIcon from "@mui/icons-material/School";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import ButtonBase from "@mui/material/ButtonBase";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useAuth } from "../auth/AuthContext";
import { colors } from "../theme";

/** Shown at /portal when a PORTAL session holds more than one context (e.g. Guardian and
 * Teacher simultaneously) - or when it holds none, which surfaces as a plain explainer
 * rather than a blank/broken page (an org marked a role portal-only without yet linking
 * the person to a Guardian or TeacherAssignment record). */
export function PortalSelectPage() {
	const navigate = useNavigate();
	const { portalIdentity } = useAuth();
	const contexts = portalIdentity?.portalContexts ?? [];

	if (contexts.length === 0) {
		return (
			<Alert severity="info">
				Your account doesn't have any portal access configured yet. Contact your school's administrator.
			</Alert>
		);
	}

	return (
		<Stack spacing={2}>
			<Typography variant="h6">Choose what you'd like to view</Typography>
			<Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
				{contexts.map((context) => (
					<ButtonBase
						key={context.type}
						onClick={() => navigate(context.type === "GUARDIAN" ? "/portal/guardian" : "/portal/teacher")}
						sx={{
							flex: 1,
							p: 3,
							borderRadius: 2,
							border: `1px solid ${colors.rule}`,
							display: "flex",
							flexDirection: "column",
							alignItems: "center",
							gap: 1,
						}}
					>
						{context.type === "GUARDIAN" ? <FamilyRestroomIcon fontSize="large" /> : <SchoolIcon fontSize="large" />}
						<Box sx={{ textAlign: "center" }}>
							<Typography variant="subtitle1">{context.type === "GUARDIAN" ? "Parent portal" : "Teacher portal"}</Typography>
							<Typography variant="body2" color="text.secondary">
								{context.type === "GUARDIAN"
									? `${context.children?.length ?? 0} linked ${context.children?.length === 1 ? "child" : "children"}`
									: "Your sections and students"}
							</Typography>
						</Box>
					</ButtonBase>
				))}
			</Stack>
		</Stack>
	);
}
