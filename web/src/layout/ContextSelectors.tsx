import { useEffect, useMemo, useState, type ReactNode } from "react";
import ButtonBase from "@mui/material/ButtonBase";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ApartmentIcon from "@mui/icons-material/Apartment";
import CalendarMonthIcon from "@mui/icons-material/CalendarMonth";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import TodayIcon from "@mui/icons-material/Today";
import { listAcademicYears, type AcademicYearResponse } from "../api/academicYears";
import { listCampuses, type CampusResponse } from "../api/campuses";
import { colors } from "../theme";

const CAMPUS_KEY = "operion.selectedCampusId";
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
			<ButtonBase
				onClick={(event) => setAnchorEl(event.currentTarget)}
				aria-label={label}
				sx={{
					display: "flex",
					alignItems: "center",
					gap: 1,
					px: 1.5,
					py: 0.75,
					borderRadius: 2,
					border: `1px solid ${colors.rule}`,
				}}
			>
				<Stack sx={{ color: colors.inkFaint, display: "flex" }}>{icon}</Stack>
				<Typography variant="body2" sx={{ color: colors.ink, fontWeight: 600 }}>
					{selected?.label ?? label}
				</Typography>
				<KeyboardArrowDownIcon fontSize="small" sx={{ color: colors.inkFaint }} />
			</ButtonBase>
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

/** Campus and Academic Year context pickers in the top bar. Pure display/selection
 * context for now - no dashboard or module data is filtered by this yet, since nothing
 * downstream is campus- or year-scoped. Selection persists per-browser only. */
export function ContextSelectors() {
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
				setCampusId(result.some((c) => c.id === stored) ? stored : fallback);
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

	return (
		<Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
			<PillSelect
				icon={<ApartmentIcon fontSize="small" />}
				label="Campus"
				options={campusOptions}
				selectedId={campusId}
				onSelect={(id) => {
					setCampusId(id);
					writeStored(CAMPUS_KEY, id);
				}}
			/>
			<PillSelect
				icon={<CalendarMonthIcon fontSize="small" />}
				label="Academic year"
				options={academicYearOptions}
				selectedId={academicYearId}
				onSelect={(id) => {
					setAcademicYearId(id);
					writeStored(ACADEMIC_YEAR_KEY, id);
				}}
			/>
			<Stack direction="row" spacing={0.75} sx={{ alignItems: "center", color: colors.inkFaint, px: 0.5 }}>
				<TodayIcon fontSize="small" />
				<Typography variant="body2" sx={{ fontWeight: 600 }}>
					{TODAY_FORMATTER.format(new Date())}
				</Typography>
			</Stack>
		</Stack>
	);
}
