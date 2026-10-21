import { useEffect, useState } from "react";
import { getMarkableSections, type MarkableSectionsResponse } from "../../api/attendance";

/** Class teachers mark only their own (homeroom) sections; ATTENDANCE_MARK_ANY_SECTION marks any. */
export function useMarkableSections() {
	const [markable, setMarkable] = useState<MarkableSectionsResponse | null>(null);

	useEffect(() => {
		getMarkableSections()
			.then(setMarkable)
			.catch(() => setMarkable({ scope: "NONE", sectionIds: [], schoolClassIds: [] }));
	}, []);

	const canMarkSection = (sectionId: number | string) =>
		!!markable && (markable.scope === "ALL" || (markable.scope === "OWN" && markable.sectionIds.includes(Number(sectionId))));

	return { markable, canMarkSection };
}
