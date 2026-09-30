import { useCallback } from "react";
import { getChildLessonPlans } from "../../api/learning";
import { LessonPlanCalendar } from "./LessonPlanCalendar";

/** Approved lesson plans for the child's section, on the shared calendar (#229). */
export function ChildLessonCalendarPanel({ studentId }: { studentId: number }) {
	const fetchPlans = useCallback((from: string, to: string) => getChildLessonPlans(studentId, from, to), [studentId]);
	return <LessonPlanCalendar fetchPlans={fetchPlans} showSection={false} />;
}
