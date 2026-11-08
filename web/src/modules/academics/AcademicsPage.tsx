import { useEffect, useState, type ReactNode } from "react";
import Alert from "@mui/material/Alert";
import CircularProgress from "@mui/material/CircularProgress";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import TextField from "@mui/material/TextField";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { useCampusFiltered, useSelectedCampusId } from "../../layout/selectedCampus";
import { listSectionEnrollments } from "../../api/enrollments";
import { listPersons } from "../../api/persons";
import { listSections, type SectionResponse } from "../../api/sections";
import { listTeacherAssignmentsForSection } from "../../api/teacherAssignments";
import { ClassSubjectsPanel } from "./ClassSubjectsPanel";
import { useClassLabel } from "./classLabel";
import { GradeLevelsPanel } from "./GradeLevelsPanel";
import { SchoolClassesPanel } from "./SchoolClassesPanel";
import { PeriodsPanel } from "./PeriodsPanel";
import { RoomsPanel } from "./RoomsPanel";
import { SectionsPanel, type SectionDetail } from "./SectionsPanel";
import { SectionTimetablePanel } from "./SectionTimetablePanel";
import { SubjectsPanel } from "./SubjectsPanel";
import { SubstitutionsPanel } from "./SubstitutionsPanel";
import { TeacherAssignmentsPanel } from "./TeacherAssignmentsPanel";

const TABS = ["gradeLevels", "sections", "subjects", "classOfferings", "teacherAssignments", "periods", "rooms", "timetable", "substitutions"] as const;
type TabKey = (typeof TABS)[number];

const TAB_LABELS: Record<TabKey, string> = {
	gradeLevels: "Grade levels",
	sections: "Sections",
	subjects: "Subjects",
	classOfferings: "Class offerings",
	teacherAssignments: "Teacher assignments",
	periods: "Periods",
	rooms: "Rooms",
	timetable: "Timetable",
	substitutions: "Substitutions",
};

/** Class-offering selector shared by the Sections and Teacher assignments tabs (#246) -
 * both concepts are inherently scoped to a class (a section belongs to one, a teacher
 * assignment belongs to one of its sections), so each tab asks "which class" via this
 * selector instead of the old drill-down-to-a-separate-route navigation. Limited to the
 * campus picked in the top bar (every campus under "All campuses"). */
function useSchoolClassOptions() {
	const [allClasses, setAllClasses] = useState<SchoolClassResponse[]>([]);
	const classLabel = useClassLabel();
	const schoolClasses = useCampusFiltered(allClasses);

	useEffect(() => {
		listSchoolClasses().then(setAllClasses).catch(() => {});
	}, []);

	return { schoolClasses, classLabel };
}

/** The picked class offering as a select value: starts on the first class, and moves to the first one again
 * when the campus changes and the pick is no longer among the (campus-filtered) classes. */
function useSelectedClassId(schoolClasses: SchoolClassResponse[]) {
	const [schoolClassId, setSchoolClassId] = useState("");

	useEffect(() => {
		if (schoolClasses.some((c) => String(c.id) === schoolClassId)) return;
		setSchoolClassId(schoolClasses.length > 0 ? String(schoolClasses[0].id) : "");
	}, [schoolClasses, schoolClassId]);

	return [schoolClassId, setSchoolClassId] as const;
}

/** Every class offering with its sections shown immediately - class teacher (the active HOMEROOM assignment) and
 * current student count are looked up per section; a lookup the caller isn't permitted to make just renders "—". */
function SectionsTab() {
	const { schoolClasses, classLabel } = useSchoolClassOptions();
	const campusPicked = useSelectedCampusId() !== null;
	const [details, setDetails] = useState<Map<number, SectionDetail>>(new Map());
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		if (schoolClasses.length === 0) {
			setLoading(false);
			return;
		}
		let cancelled = false;
		(async () => {
			const personNames = new Map<number, string>();
			await listPersons()
				.then((persons) => persons.forEach((p) => personNames.set(p.id, `${p.firstName} ${p.lastName}`)))
				.catch(() => {});
			const sectionLists = await Promise.all(schoolClasses.map((c) => listSections(c.id).catch(() => [] as SectionResponse[])));
			const entries = await Promise.all(
				sectionLists.flat().map(async (section): Promise<[number, SectionDetail]> => {
					const [enrollments, assignments] = await Promise.all([
						listSectionEnrollments(section.id).catch(() => null),
						listTeacherAssignmentsForSection(section.id).catch(() => null),
					]);
					const homeroom = assignments?.find((a) => a.assignmentType === "HOMEROOM" && a.status === "ACTIVE" && !a.endDate);
					return [
						section.id,
						{
							students: enrollments ? enrollments.filter((e) => e.current).length : null,
							classTeacher: homeroom ? (personNames.get(homeroom.teacherPersonId) ?? `Person #${homeroom.teacherPersonId}`) : null,
						},
					];
				}),
			);
			if (!cancelled) {
				setDetails(new Map(entries));
				setLoading(false);
			}
		})();
		return () => {
			cancelled = true;
		};
	}, [schoolClasses]);

	if (schoolClasses.length === 0) {
		return (
			<Alert severity="info">
				{campusPicked ? "No class offerings in the selected campus yet." : "No class offerings yet."} Create one under the Class offerings tab, then add its
				sections here.
			</Alert>
		);
	}

	return (
		<Stack spacing={2}>
			{loading && <CircularProgress size={20} aria-label="Loading section details" />}
			{schoolClasses.map((schoolClass) => (
				<SectionsPanel key={schoolClass.id} classId={schoolClass.id} title={classLabel(schoolClass)} details={details} />
			))}
		</Stack>
	);
}

function ClassOfferingsTab() {
	const { schoolClasses, classLabel } = useSchoolClassOptions();
	const [schoolClassId, setSchoolClassId] = useSelectedClassId(schoolClasses);

	return (
		<Stack spacing={2}>
			<SchoolClassesPanel />

			<TextField select label="Subjects taught in" value={schoolClassId} onChange={(e) => setSchoolClassId(e.target.value)} sx={{ maxWidth: 320 }}>
				{schoolClasses.map((schoolClass) => (
					<MenuItem key={schoolClass.id} value={schoolClass.id}>
						{classLabel(schoolClass)}
					</MenuItem>
				))}
			</TextField>

			{!schoolClassId && <Alert severity="info">Create a class offering above to assign subjects to it.</Alert>}
			{schoolClassId && <ClassSubjectsPanel classId={Number(schoolClassId)} />}
		</Stack>
	);
}

/** Class-offering + section picker around a per-section panel - shared by the Teacher
 * assignments and Timetable tabs, since both are scoped to one section. */
function SectionScopedTab({ noun, render }: { noun: string; render: (sectionId: number, classId: number) => ReactNode }) {
	const { schoolClasses, classLabel } = useSchoolClassOptions();
	// Start on the first class offering and its first section so records show without a picker round-trip.
	const [schoolClassId, setSchoolClassId] = useSelectedClassId(schoolClasses);
	const [sectionId, setSectionId] = useState("");
	const [sections, setSections] = useState<SectionResponse[]>([]);

	useEffect(() => {
		setSectionId("");
		setSections([]);
		if (!schoolClassId) return;
		listSections(Number(schoolClassId))
			.then((list) => {
				setSections(list);
				if (list.length > 0) setSectionId(String(list[0].id));
			})
			.catch(() => {});
	}, [schoolClassId]);

	return (
		<Stack spacing={2}>
			<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
				<TextField
					select
					label="Class offering"
					value={schoolClassId}
					onChange={(e) => setSchoolClassId(e.target.value)}
					sx={{ minWidth: 220 }}
				>
					{schoolClasses.map((schoolClass) => (
						<MenuItem key={schoolClass.id} value={schoolClass.id}>
							{classLabel(schoolClass)}
						</MenuItem>
					))}
				</TextField>
				<TextField
					select
					label="Section"
					value={sectionId}
					onChange={(e) => setSectionId(e.target.value)}
					disabled={!schoolClassId}
					sx={{ minWidth: 160 }}
				>
					{sections.map((section) => (
						<MenuItem key={section.id} value={section.id}>
							{section.name}
						</MenuItem>
					))}
				</TextField>
			</Stack>

			{!sectionId && (
				<Alert severity="info">
					{schoolClassId ? "This class offering has no sections yet - add one under the Sections tab" : "No class offerings yet - create one under the Class offerings tab"} to manage {noun}.
				</Alert>
			)}
			{sectionId && render(Number(sectionId), Number(schoolClassId))}
		</Stack>
	);
}

function TeacherAssignmentsTab() {
	return <SectionScopedTab noun="teacher assignments" render={(sectionId) => <TeacherAssignmentsPanel sectionId={sectionId} />} />;
}

function TimetableTab() {
	return <SectionScopedTab noun="its timetable" render={(sectionId, classId) => <SectionTimetablePanel sectionId={sectionId} classId={classId} />} />;
}

/** Tabbed configuration area (#246) - Grade levels -> Sections -> Subjects -> Class
 * offerings -> Teacher assignments read as one place instead of scattered
 * panels/drill-downs (Timetable is a future tab, tracked separately under #212-#216).
 * Every tab reuses the same panels/APIs SchoolClassSectionsPage/SectionDetailPage/
 * Settings already use - no data-model changes, no duplicated logic. */
export function AcademicsPage() {
	const [tab, setTab] = useState<TabKey>("gradeLevels");

	return (
		<Stack spacing={3}>
			<Tabs value={tab} onChange={(_, value) => setTab(value)}>
				{TABS.map((t) => (
					<Tab key={t} label={TAB_LABELS[t]} value={t} />
				))}
			</Tabs>

			{tab === "gradeLevels" && <GradeLevelsPanel />}
			{tab === "sections" && <SectionsTab />}
			{tab === "subjects" && <SubjectsPanel />}
			{tab === "classOfferings" && <ClassOfferingsTab />}
			{tab === "teacherAssignments" && <TeacherAssignmentsTab />}
			{tab === "periods" && <PeriodsPanel />}
			{tab === "rooms" && <RoomsPanel />}
			{tab === "timetable" && <TimetableTab />}
			{tab === "substitutions" && <SubstitutionsPanel />}
		</Stack>
	);
}
