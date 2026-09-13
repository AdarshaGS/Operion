import Stack from "@mui/material/Stack";
import { FeeCategoriesPanel } from "./FeeCategoriesPanel";
import { FeeStructuresPanel } from "./FeeStructuresPanel";

/** Setup-time fee configuration (#203) - split from the daily collection workflow, which
 * lives in FeeCollectionPage. */
export function FeesSetupPage() {
	return (
		<Stack spacing={3}>
			<FeeCategoriesPanel />
			<FeeStructuresPanel />
		</Stack>
	);
}
