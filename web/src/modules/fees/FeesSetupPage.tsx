import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { Can } from "../../auth/Can";
import { useBackTarget } from "../../utils/useBackTarget";
import { AdmissionFeeSettingsPanel } from "./AdmissionFeeSettingsPanel";
import { FeeCategoriesPanel } from "./FeeCategoriesPanel";
import { FeeStructuresPanel } from "./FeeStructuresPanel";

/** Setup-time fee configuration (#203) - split from the daily collection workflow, which
 * lives in FeeCollectionPage. */
export function FeesSetupPage() {
	const navigate = useNavigate();
	// Opened from the Dashboard setup card, Back returns there instead of to fee collection.
	const back = useBackTarget("/fees");
	return (
		<Stack spacing={3}>
			<Box>
				<Button size="small" startIcon={<ArrowBackIcon />} onClick={() => navigate(back.path)}>
					{back.fromDashboard ? "Back to dashboard" : "Back to fee collection"}
				</Button>
			</Box>
			<Can anyOf={["FEE_CATEGORY_MANAGE"]}>
				<FeeCategoriesPanel />
			</Can>
			<Can anyOf={["FEE_STRUCTURE_MANAGE"]}>
				<FeeStructuresPanel />
			</Can>
			{/* Readable by anyone on this page; the panel itself makes it editable only with ORGANISATION_MANAGE. */}
			<AdmissionFeeSettingsPanel />
		</Stack>
	);
}
