import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { ItemCategoriesPanel } from "./ItemCategoriesPanel";
import { ItemsPanel } from "./ItemsPanel";

export function InventoryPage() {
	return (
		<Stack spacing={3} sx={{ maxWidth: 900 }}>
			<Typography variant="h4" component="h1">
				Inventory
			</Typography>
			<ItemCategoriesPanel />
			<ItemsPanel />
		</Stack>
	);
}
