import Stack from "@mui/material/Stack";
import { BooksPanel } from "./BooksPanel";
import { BorrowPanel } from "./BorrowPanel";

export function LibraryPage() {
	return (
		<Stack spacing={3}>
			<BooksPanel />
			<BorrowPanel />
		</Stack>
	);
}
