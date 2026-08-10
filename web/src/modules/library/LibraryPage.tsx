import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { BooksPanel } from "./BooksPanel";
import { BorrowPanel } from "./BorrowPanel";

export function LibraryPage() {
	return (
		<Stack spacing={3} sx={{ maxWidth: 900 }}>
			<Typography variant="h4" component="h1">
				Library
			</Typography>
			<BooksPanel />
			<BorrowPanel />
		</Stack>
	);
}
