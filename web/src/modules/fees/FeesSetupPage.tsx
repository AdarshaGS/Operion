import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { Can } from "../../auth/Can";
import { FeeCategoriesPanel } from "./FeeCategoriesPanel";
import { FeeStructuresPanel } from "./FeeStructuresPanel";

/** Setup-time fee configuration (#203) - split from the daily collection workflow, which
 * lives in FeeCollectionPage. */
export function FeesSetupPage() {
	const navigate = useNavigate();
	return (
		<Stack spacing={3}>
			<Box>
				<Button size="small" startIcon={<ArrowBackIcon />} onClick={() => navigate("/fees")}>
					Back to fee collection
				</Button>
			</Box>
			<Can anyOf={["FEE_CATEGORY_MANAGE"]}>
				<FeeCategoriesPanel />
			</Can>
			<Can anyOf={["FEE_STRUCTURE_MANAGE"]}>
				<FeeStructuresPanel />
			</Can>
		</Stack>
	);
}
