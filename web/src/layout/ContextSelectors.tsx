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
import { CAMPUS_KEY, setSelectedCampusId } from "./selectedCampus";

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
}

function PillSelect({ icon, label, options, selectedId, onSelect }: PillSelectProps) {
	const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
	const selected = options.find((option) => option.id === selectedId);

	if (options.length === 0) {
		return null;
	}

	return (
		<>
			<TopbarPill icon={icon} label={selected?.label ?? label} ariaLabel={label} onClick={(event) => setAnchorEl(event.currentTarget)} />
			<Menu anchorEl={anchorEl} open={anchorEl !== null} onClose={() => setAnchorEl(null)}>
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
 * selectedCampus.ts (class lists filter by it); the year choice is still display-only.
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
				const stored = readStored(CAMPUS_KEY);
				const fallback = result[0]?.id ?? null;
				const resolved = result.some((c) => c.id === stored) ? stored : fallback;
				setCampusId(resolved);
				setSelectedCampusId(resolved);
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
					writeStored(CAMPUS_KEY, id);
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
