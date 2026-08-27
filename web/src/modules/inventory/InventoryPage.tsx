import Stack from "@mui/material/Stack";
import { ItemCategoriesPanel } from "./ItemCategoriesPanel";
import { ItemsPanel } from "./ItemsPanel";

export function InventoryPage() {
	return (
		<Stack spacing={3}>
			<ItemCategoriesPanel />
			<ItemsPanel />
		</Stack>
	);
}
