import { useId, useState, type ReactNode } from "react";
import Badge from "@mui/material/Badge";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import IconButton from "@mui/material/IconButton";
import InputAdornment from "@mui/material/InputAdornment";
import Popover from "@mui/material/Popover";
import TextField from "@mui/material/TextField";
import CancelIcon from "@mui/icons-material/Cancel";
import ClearIcon from "@mui/icons-material/Clear";
import FilterListIcon from "@mui/icons-material/FilterList";
import SearchIcon from "@mui/icons-material/Search";

export interface ActiveFilter {
	/** Stable React key, usually the filter's name. */
	key: string;
	/** What the chip shows, e.g. "Status: Active". */
	label: string;
	onDelete: () => void;
}

export interface CompactFilterBarProps {
	/** Omit (together with onSearchChange) on lists that have no free-text search: the search input is then hidden. */
	searchValue?: string;
	onSearchChange?: (value: string) => void;
	/** Accessible name of the search input. */
	searchLabel?: string;
	searchPlaceholder?: string;
	/** Non-search filters currently applied; drives the Filters badge and the removable chips. */
	activeFilters: ActiveFilter[];
	/** Shows a "Clear all" button whenever a search term or any chip is active. */
	onClearAll?: () => void;
	/** Controls kept always visible, before the search input (e.g. attendance's Date). */
	startSlot?: ReactNode;
	/** Trailing content on the row, e.g. a result count. */
	endSlot?: ReactNode;
	/** The filter controls (selects, date fields). Rendered inside the Filters popover. */
	children?: ReactNode;
}

/**
 * One slim row for list pages: a small search box, a Filters button (badge = number of active filters) that opens the
 * remaining controls in a popover, and removable chips for whatever is applied. Fully controlled; the page owns every
 * filter's state, so reset-page-on-change behaviour stays in the page.
 */
export function CompactFilterBar({
	searchValue,
	onSearchChange,
	searchLabel = "Search",
	searchPlaceholder,
	activeFilters,
	onClearAll,
	startSlot,
	endSlot,
	children,
}: CompactFilterBarProps) {
	const [anchor, setAnchor] = useState<HTMLElement | null>(null);
	const popoverId = useId();
	const open = anchor !== null;
	const hasSearch = searchValue !== undefined && onSearchChange !== undefined;
	const hasActive = activeFilters.length > 0 || Boolean(searchValue);
	const count = activeFilters.length;

	return (
		<Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
			<Box sx={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 1 }}>
				{startSlot}
				{hasSearch && (
					<TextField
						size="small"
						value={searchValue}
						onChange={(e) => onSearchChange(e.target.value)}
						placeholder={searchPlaceholder ?? searchLabel}
						sx={{ flex: "0 1 240px", minWidth: 140 }}
						slotProps={{
							htmlInput: { "aria-label": searchLabel },
							input: {
								startAdornment: (
									<InputAdornment position="start">
										<SearchIcon fontSize="small" />
									</InputAdornment>
								),
								endAdornment: searchValue ? (
									<InputAdornment position="end">
										<IconButton size="small" edge="end" aria-label="Clear search" onClick={() => onSearchChange("")}>
											<ClearIcon fontSize="small" />
										</IconButton>
									</InputAdornment>
								) : undefined,
							},
						}}
					/>
				)}
				<Badge color="primary" badgeContent={count} invisible={count === 0}>
					<Button
						variant="outlined"
						color="inherit"
						size="small"
						startIcon={<FilterListIcon />}
						aria-haspopup="dialog"
						aria-expanded={open}
						aria-controls={open ? popoverId : undefined}
						aria-label={count > 0 ? `Filters, ${count} active` : "Filters"}
						onClick={(e) => setAnchor(e.currentTarget)}
						sx={{ height: 40 }}
					>
						Filters
					</Button>
				</Badge>
				{onClearAll && hasActive && (
					<Button size="small" onClick={onClearAll}>
						Clear all
					</Button>
				)}
				{endSlot && <Box sx={{ ml: "auto", display: "flex", alignItems: "center", gap: 1 }}>{endSlot}</Box>}
			</Box>

			{count > 0 && (
				<Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.75 }}>
					{activeFilters.map((filter) => (
						<Chip
							key={filter.key}
							size="small"
							label={filter.label}
							onDelete={filter.onDelete}
							deleteIcon={<CancelIcon titleAccess={`Remove filter ${filter.label}`} />}
						/>
					))}
				</Box>
			)}

			<Popover
				id={popoverId}
				open={open}
				anchorEl={anchor}
				onClose={() => setAnchor(null)}
				anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
				transformOrigin={{ vertical: "top", horizontal: "left" }}
				slotProps={{ paper: { role: "dialog", "aria-label": "Filters", sx: { mt: 0.5, p: 2, width: "min(360px, calc(100vw - 32px))" } } }}
			>
				<Box
					sx={{
						display: "grid",
						gap: 1.5,
						gridTemplateColumns: { xs: "minmax(0, 1fr)", sm: "repeat(2, minmax(0, 1fr))" },
						// Pages hand over plain TextFields; make them fill their grid cell whatever min-width they carry.
						"& > .MuiTextField-root, & > .MuiFormControl-root": { width: "100%", minWidth: 0 },
					}}
				>
					{children}
				</Box>
			</Popover>
		</Box>
	);
}
