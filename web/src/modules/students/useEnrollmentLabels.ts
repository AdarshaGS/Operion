import { useCallback, useEffect, useRef, useState } from "react";
import { listAcademicYears, type AcademicYearResponse } from "../../api/academicYears";
import type { StudentEnrollmentResponse } from "../../api/enrollments";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { useSchoolClassLabel } from "../../utils/useSchoolClassLabel";

export interface EnrollmentLabels {
	academicYearName: (id: number) => string;
	classLabel: (id: number) => string;
	sectionName: (id: number) => string;
}

/** Resolves the academic year / class / section ids on a student's enrollments to display labels,
 * the same lookups EnrollDialog uses (year -> classes -> sections). Nothing is fetched until
 * `enabled`, so a closed Records drawer costs no requests, and each lookup is latched once it has
 * loaded so revisiting the History tab does not refetch. Unresolved ids fall back to "#id". */
export function useEnrollmentLabels(enrollments: StudentEnrollmentResponse[], enabled: boolean): EnrollmentLabels {
	const schoolClassLabel = useSchoolClassLabel();
	const [years, setYears] = useState<AcademicYearResponse[]>([]);
	const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
	const [sections, setSections] = useState<SectionResponse[]>([]);

	const yearKey = [...new Set(enrollments.map((e) => e.academicYearId))].sort().join(",");
	const classKey = [...new Set(enrollments.map((e) => e.schoolClassId))].sort().join(",");

	// Latches: the key (or flag) a lookup last completed for, so re-enabling does not refetch it.
	const yearsLoaded = useRef(false);
	const loadedClassKey = useRef<string | null>(null);
	const loadedSectionKey = useRef<string | null>(null);

	useEffect(() => {
		if (!enabled || yearsLoaded.current) return;
		let cancelled = false;
		listAcademicYears()
			.then((list) => {
				if (cancelled) return;
				yearsLoaded.current = true;
				setYears(list);
			})
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	}, [enabled]);

	useEffect(() => {
		if (!enabled || !yearKey || loadedClassKey.current === yearKey) return;
		let cancelled = false;
		Promise.all(yearKey.split(",").map((id) => listSchoolClasses(Number(id)).catch(() => [] as SchoolClassResponse[])))
			.then((lists) => {
				if (cancelled) return;
				loadedClassKey.current = yearKey;
				setClasses(lists.flat());
			})
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	}, [enabled, yearKey]);

	useEffect(() => {
		if (!enabled || !classKey || loadedSectionKey.current === classKey) return;
		let cancelled = false;
		Promise.all(classKey.split(",").map((id) => listSections(Number(id)).catch(() => [] as SectionResponse[])))
			.then((lists) => {
				if (cancelled) return;
				loadedSectionKey.current = classKey;
				setSections(lists.flat());
			})
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	}, [enabled, classKey]);

	const academicYearName = useCallback((id: number) => years.find((y) => y.id === id)?.name ?? `#${id}`, [years]);
	const classLabel = useCallback(
		(id: number) => {
			const schoolClass = classes.find((c) => c.id === id);
			return schoolClass ? schoolClassLabel(schoolClass) : `#${id}`;
		},
		[classes, schoolClassLabel],
	);
	const sectionName = useCallback((id: number) => sections.find((s) => s.id === id)?.name ?? `#${id}`, [sections]);

	return { academicYearName, classLabel, sectionName };
}
