import { useCallback, useEffect, useState } from "react";
import { listCampuses, type CampusResponse } from "../api/campuses";
import { listGradeLevels, type GradeLevelResponse } from "../api/gradeLevels";
import type { SchoolClassResponse } from "../api/schoolClasses";

/** Picker label for a class: its display name, else its grade level's name (template-built classes
 * have no display name), then the campus - a grade usually exists on every campus, so the campus is
 * what tells two options apart. */
export function useSchoolClassLabel(): (schoolClass: SchoolClassResponse) => string {
	const [gradeLevels, setGradeLevels] = useState<GradeLevelResponse[]>([]);
	const [campuses, setCampuses] = useState<CampusResponse[]>([]);

	useEffect(() => {
		listGradeLevels().then(setGradeLevels).catch(() => {});
		listCampuses().then(setCampuses).catch(() => {});
	}, []);

	return useCallback(
		(schoolClass) => {
			const name = schoolClass.displayName ?? gradeLevels.find((g) => g.id === schoolClass.gradeLevelId)?.name ?? `Class #${schoolClass.id}`;
			const campus = campuses.find((c) => c.id === schoolClass.campusId)?.name;
			return campus ? `${name} · ${campus}` : name;
		},
		[gradeLevels, campuses],
	);
}
