import { useParams } from "react-router-dom";
import { getChildTimetable, getMyTimetable } from "../api/timetable";
import { PortalTimetableView } from "./PortalTimetableView";

/** The signed-in teacher's own published timetable, with cover they give or receive. */
export function MyTimetablePage() {
	return <PortalTimetableView title="My timetable" backTo="/portal/teacher" load={getMyTimetable} showTeacher={false} showClass />;
}

/** A linked child's section timetable for a guardian; the studentId is verified server-side against the caller's StudentGuardian links. */
export function ChildTimetablePage() {
	const { studentId } = useParams();
	const id = Number(studentId);
	return (
		<PortalTimetableView
			title="Class timetable"
			backTo="/portal/guardian"
			load={(date) => getChildTimetable(id, date)}
			showTeacher
			showClass={false}
		/>
	);
}
