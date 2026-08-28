import Stack from "@mui/material/Stack";
import { CustomersPanel } from "./CustomersPanel";
import { ItemCategoriesPanel } from "./ItemCategoriesPanel";
import { ItemsPanel } from "./ItemsPanel";
import { SuppliersPanel } from "./SuppliersPanel";

export function InventoryPage() {
	return (
		<Stack spacing={3}>
			<ItemCategoriesPanel />
			<ItemsPanel />
			<SuppliersPanel />
			<CustomersPanel />
		</Stack>
	);
}
