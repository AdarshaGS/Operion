import { useCallback, useEffect, useState } from "react";
import { listCampuses } from "../../api/campuses";
import { listGradeLevels, type GradeLevelResponse } from "../../api/gradeLevels";
import type { SchoolClassResponse } from "../../api/schoolClasses";

/** A class's own name: its display name, else its grade level's name (template-built classes have
 * no display name). */
export function baseClassName(schoolClass: SchoolClassResponse, gradeLevelName: string | undefined): string {
	return schoolClass.displayName ?? gradeLevelName ?? `Class #${schoolClass.id}`;
}

/** Picker/heading label for a class. Every campus has its own class per grade, so in a multi-campus
 * organisation the campus is appended ("LKG - Peresandra Branch") to tell them apart; a single-campus
 * organisation keeps the plain name. */
export function formatClassLabel(schoolClass: SchoolClassResponse, gradeLevelName: string | undefined, multiCampus: boolean): string {
	const name = baseClassName(schoolClass, gradeLevelName);
	return multiCampus && schoolClass.campusName ? `${name} - ${schoolClass.campusName}` : name;
}

/** Returns a label function for the Academics screens, loading the grade levels and the campus count it needs. */
export function useClassLabel(): (schoolClass: SchoolClassResponse) => string {
	const [gradeLevels, setGradeLevels] = useState<GradeLevelResponse[]>([]);
	const [multiCampus, setMultiCampus] = useState(false);

	useEffect(() => {
		listGradeLevels().then(setGradeLevels).catch(() => {});
		listCampuses().then((campuses) => setMultiCampus(campuses.length > 1)).catch(() => {});
	}, []);

	return useCallback(
		(schoolClass) => formatClassLabel(schoolClass, gradeLevels.find((level) => level.id === schoolClass.gradeLevelId)?.name, multiCampus),
		[gradeLevels, multiCampus],
	);
}
