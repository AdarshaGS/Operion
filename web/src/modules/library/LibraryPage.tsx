import { useEffect, useState, type SyntheticEvent } from "react";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import { getLibrarySummary, type LibraryStatusSummaryResponse } from "../../api/books";
import { BooksPanel } from "./BooksPanel";
import { BorrowPanel } from "./BorrowPanel";
import { LibrarySettingsPanel } from "./LibrarySettingsPanel";
import { OverduePanel } from "./OverduePanel";

type LibraryTab = "books" | "borrow" | "overdue" | "settings";

/** Available/Issued/Overdue/Lost/Damaged summary counts + tabs, replacing the previous
 * two-stacked-panels layout (#172). */
export function LibraryPage() {
	const [tab, setTab] = useState<LibraryTab>("books");
	const [summary, setSummary] = useState<LibraryStatusSummaryResponse | null>(null);

	useEffect(() => {
		getLibrarySummary().then(setSummary).catch(() => {});
	}, [tab]);

	function handleChange(_event: SyntheticEvent, value: LibraryTab) {
		setTab(value);
	}

	return (
		<Stack spacing={2}>
			{summary && (
				<Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
					<Chip label={`Available: ${summary.available}`} size="small" color="success" variant="outlined" />
					<Chip label={`Issued: ${summary.issued}`} size="small" color="warning" variant="outlined" />
					<Chip label={`Overdue: ${summary.overdue}`} size="small" color="error" variant="outlined" />
					<Chip label={`Lost: ${summary.lost}`} size="small" variant="outlined" />
					<Chip label={`Damaged: ${summary.damaged}`} size="small" variant="outlined" />
				</Stack>
			)}

			<Box sx={{ borderBottom: 1, borderColor: "divider" }}>
				<Tabs value={tab} onChange={handleChange}>
					<Tab label="Books" value="books" />
					<Tab label="Borrow / return" value="borrow" />
					<Tab label="Overdue" value="overdue" />
					<Tab label="Settings" value="settings" />
				</Tabs>
			</Box>

			{tab === "books" && <BooksPanel />}
			{tab === "borrow" && <BorrowPanel />}
			{tab === "overdue" && <OverduePanel />}
			{tab === "settings" && <LibrarySettingsPanel />}
		</Stack>
	);
}
