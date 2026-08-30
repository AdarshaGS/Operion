import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import TextField from "@mui/material/TextField";
import { type GradeLevelResponse, listGradeLevels } from "../../api/gradeLevels";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { ClassSubjectsPanel } from "./ClassSubjectsPanel";
import { GradeLevelsPanel } from "./GradeLevelsPanel";
import { SchoolClassesPanel } from "./SchoolClassesPanel";
import { SectionsPanel } from "./SectionsPanel";
import { SubjectsPanel } from "./SubjectsPanel";
import { TeacherAssignmentsPanel } from "./TeacherAssignmentsPanel";

const TABS = ["gradeLevels", "sections", "subjects", "classOfferings", "teacherAssignments"] as const;
type TabKey = (typeof TABS)[number];

const TAB_LABELS: Record<TabKey, string> = {
	gradeLevels: "Grade levels",
	sections: "Sections",
	subjects: "Subjects",
	classOfferings: "Class offerings",
	teacherAssignments: "Teacher assignments",
};

function classLabel(schoolClass: SchoolClassResponse, gradeLevelsById: Map<number, GradeLevelResponse>): string {
	return schoolClass.displayName ?? gradeLevelsById.get(schoolClass.gradeLevelId)?.name ?? `Class #${schoolClass.id}`;
}

/** Class-offering selector shared by the Sections and Teacher assignments tabs (#246) -
 * both concepts are inherently scoped to a class (a section belongs to one, a teacher
 * assignment belongs to one of its sections), so each tab asks "which class" via this
 * selector instead of the old drill-down-to-a-separate-route navigation. */
function useSchoolClassOptions() {
	const [schoolClasses, setSchoolClasses] = useState<SchoolClassResponse[]>([]);
	const [gradeLevels, setGradeLevels] = useState<GradeLevelResponse[]>([]);

	useEffect(() => {
		listSchoolClasses().then(setSchoolClasses).catch(() => {});
		listGradeLevels().then(setGradeLevels).catch(() => {});
	}, []);

	const gradeLevelsById = new Map(gradeLevels.map((level) => [level.id, level]));
	return { schoolClasses, gradeLevelsById };
}

function SectionsTab() {
	const { schoolClasses, gradeLevelsById } = useSchoolClassOptions();
	const [schoolClassId, setSchoolClassId] = useState("");

	return (
		<Stack spacing={2}>
			<TextField select label="Class offering" value={schoolClassId} onChange={(e) => setSchoolClassId(e.target.value)} sx={{ maxWidth: 320 }}>
				{schoolClasses.map((schoolClass) => (
					<MenuItem key={schoolClass.id} value={schoolClass.id}>
						{classLabel(schoolClass, gradeLevelsById)}
					</MenuItem>
				))}
			</TextField>

			{!schoolClassId && <Alert severity="info">Select a class offering to view and manage its sections.</Alert>}
			{schoolClassId && <SectionsPanel classId={Number(schoolClassId)} />}
		</Stack>
	);
}

function ClassOfferingsTab() {
	const { schoolClasses, gradeLevelsById } = useSchoolClassOptions();
	const [schoolClassId, setSchoolClassId] = useState("");

	return (
		<Stack spacing={2}>
			<SchoolClassesPanel />

			<TextField select label="Class offering" value={schoolClassId} onChange={(e) => setSchoolClassId(e.target.value)} sx={{ maxWidth: 320 }}>
				{schoolClasses.map((schoolClass) => (
					<MenuItem key={schoolClass.id} value={schoolClass.id}>
						{classLabel(schoolClass, gradeLevelsById)}
					</MenuItem>
				))}
			</TextField>

			{!schoolClassId && <Alert severity="info">Select a class offering above to manage its subject assignments.</Alert>}
			{schoolClassId && <ClassSubjectsPanel classId={Number(schoolClassId)} />}
		</Stack>
	);
}

function TeacherAssignmentsTab() {
	const { schoolClasses, gradeLevelsById } = useSchoolClassOptions();
	const [schoolClassId, setSchoolClassId] = useState("");
	const [sectionId, setSectionId] = useState("");
	const [sections, setSections] = useState<SectionResponse[]>([]);

	useEffect(() => {
		setSectionId("");
		setSections([]);
		if (!schoolClassId) return;
		listSections(Number(schoolClassId)).then(setSections).catch(() => {});
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
							{classLabel(schoolClass, gradeLevelsById)}
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

			{!sectionId && <Alert severity="info">Select a class offering and section to view and manage teacher assignments.</Alert>}
			{sectionId && <TeacherAssignmentsPanel sectionId={Number(sectionId)} />}
		</Stack>
	);
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
		</Stack>
	);
}
