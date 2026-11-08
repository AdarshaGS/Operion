import { useEffect, useMemo, useState, type ReactNode } from "react";
import Box from "@mui/material/Box";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import ApartmentIcon from "@mui/icons-material/Apartment";
import CalendarMonthIcon from "@mui/icons-material/CalendarMonth";
import TodayIcon from "@mui/icons-material/Today";
import { listAcademicYears, type AcademicYearResponse } from "../api/academicYears";
import { listCampuses, type CampusResponse } from "../api/campuses";
import { TopbarPill } from "./TopbarParts";
import { readStoredCampus, resolveCampusSelection, setSelectedCampusId, writeStoredCampus } from "./selectedCampus";

const ACADEMIC_YEAR_KEY = "operion.selectedAcademicYearId";

function readStored(key: string): number | null {
	try {
		const raw = localStorage.getItem(key);
		return raw ? Number(raw) : null;
	} catch {
		return null;
	}
}

function writeStored(key: string, id: number) {
	try {
		localStorage.setItem(key, String(id));
	} catch {
		// per-viewer convenience only - fine to no-op when storage is unavailable
	}
}

const TODAY_FORMATTER = new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short", year: "numeric" });

interface PillSelectProps {
	icon: ReactNode;
	label: string;
	options: { id: number; label: string }[];
	selectedId: number | null;
	onSelect: (id: number) => void;
	/** When set (and there is more than one option), a leading entry with this label selects "none" (selectedId null). */
	allLabel?: string;
	onSelectAll?: () => void;
}

function PillSelect({ icon, label, options, selectedId, onSelect, allLabel, onSelectAll }: PillSelectProps) {
	const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
	const selected = options.find((option) => option.id === selectedId);

	if (options.length === 0) {
		return null;
	}

	const showAll = allLabel !== undefined && options.length > 1;

	return (
		<>
			<TopbarPill icon={icon} label={selected?.label ?? (showAll && selectedId === null ? allLabel : label)} ariaLabel={label} onClick={(event) => setAnchorEl(event.currentTarget)} />
			<Menu anchorEl={anchorEl} open={anchorEl !== null} onClose={() => setAnchorEl(null)}>
				{showAll && (
					<MenuItem
						selected={selectedId === null}
						onClick={() => {
							onSelectAll?.();
							setAnchorEl(null);
						}}
					>
						{allLabel}
					</MenuItem>
				)}
				{options.map((option) => (
					<MenuItem
						key={option.id}
						selected={option.id === selectedId}
						onClick={() => {
							onSelect(option.id);
							setAnchorEl(null);
						}}
					>
						{option.label}
					</MenuItem>
				))}
			</Menu>
		</>
	);
}

/** Campus and Academic Year context pickers in the top bar. The campus choice is shared via
 * selectedCampus.ts (class lists filter by it; "All campuses" - the first entry, only offered when
 * there is more than one campus - turns the filter off); the year choice is still display-only.
 * Selection persists per-browser only. */
export function ContextSelectors({ search }: { search?: ReactNode }) {
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);
	const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
	const [campusId, setCampusId] = useState<number | null>(null);
	const [academicYearId, setAcademicYearId] = useState<number | null>(null);

	useEffect(() => {
		listCampuses()
			.then((result) => {
				setCampuses(result);
				const resolved = resolveCampusSelection(readStoredCampus(), result.map((c) => c.id));
				setCampusId(resolved);
				setSelectedCampusId(resolved);
				// Stored once resolved, so a stale id is replaced and the next load starts on the right campus.
				writeStoredCampus(resolved);
			})
			.catch(() => {});
		listAcademicYears()
			.then((result) => {
				setAcademicYears(result);
				const stored = readStored(ACADEMIC_YEAR_KEY);
				const current = result.find((y) => y.current)?.id ?? result[0]?.id ?? null;
				setAcademicYearId(result.some((y) => y.id === stored) ? stored : current);
			})
			.catch(() => {});
	}, []);

	const campusOptions = useMemo(() => campuses.map((c) => ({ id: c.id, label: c.name })), [campuses]);
	const academicYearOptions = useMemo(() => academicYears.map((y) => ({ id: y.id, label: y.name })), [academicYears]);

	// Order mirrors the target layout: campus, then the (stretching) search, then year and today's date.
	return (
		<>
			<PillSelect
				icon={<ApartmentIcon />}
				label="Campus"
				options={campusOptions}
				selectedId={campusId}
				onSelect={(id) => {
					setCampusId(id);
					setSelectedCampusId(id);
					writeStoredCampus(id);
				}}
				allLabel="All campuses"
				onSelectAll={() => {
					setCampusId(null);
					setSelectedCampusId(null);
					writeStoredCampus(null);
				}}
			/>
			{search ?? <Box sx={{ flexGrow: 1 }} />}
			<PillSelect
				icon={<CalendarMonthIcon />}
				label="Academic year"
				options={academicYearOptions}
				selectedId={academicYearId}
				onSelect={(id) => {
					setAcademicYearId(id);
					writeStored(ACADEMIC_YEAR_KEY, id);
				}}
			/>
			<Box sx={{ display: { xs: "none", lg: "block" } }}>
				<TopbarPill icon={<TodayIcon />} label={TODAY_FORMATTER.format(new Date())} hideLabelOnPhone={false} />
			</Box>
		</>
	);
}
