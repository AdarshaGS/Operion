import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { listStudentEnrollments, type StudentEnrollmentResponse } from "../../api/enrollments";
import { type PersonResponse, listPersons } from "../../api/persons";
import { type SectionResponse, listSections } from "../../api/sections";
import { listSchoolClasses } from "../../api/schoolClasses";
import { listStudents, type StudentResponse } from "../../api/students";
import { FeeCategoriesPanel } from "./FeeCategoriesPanel";
import { FeeStructuresPanel } from "./FeeStructuresPanel";
import { StudentFeesPanel } from "./StudentFeesPanel";

export function FeesPage() {
	const [students, setStudents] = useState<StudentResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [studentId, setStudentId] = useState("");

	const [currentEnrollment, setCurrentEnrollment] = useState<StudentEnrollmentResponse | null>(null);
	const [schoolClassId, setSchoolClassId] = useState<number | null>(null);
	const [resolveError, setResolveError] = useState<string | null>(null);

	useEffect(() => {
		listStudents().then(setStudents).catch(() => {});
		listPersons().then(setPersons).catch(() => {});
	}, []);

	useEffect(() => {
		setCurrentEnrollment(null);
		setSchoolClassId(null);
		setResolveError(null);
		if (!studentId) return;

		listStudentEnrollments(Number(studentId))
			.then(async (enrollments) => {
				const current = enrollments.find((e) => e.current);
				if (!current) {
					setResolveError("This student has no current class enrollment yet — enroll them before assigning fees.");
					return;
				}
				setCurrentEnrollment(current);
				// Section only carries schoolClassId, not the reverse - resolve it by scanning this
				// section's class. There's no GET-by-id for either Section or SchoolClass yet (same
				// gap noted in the Academics module write-up), so fall back to the list+find pattern
				// used throughout this frontend at this data scale.
				const classes = await listSchoolClasses();
				for (const schoolClass of classes) {
					const sections: SectionResponse[] = await listSections(schoolClass.id);
					if (sections.some((s) => s.id === current.sectionId)) {
						setSchoolClassId(schoolClass.id);
						return;
					}
				}
				setResolveError("Could not resolve this student's class from their section.");
			})
			.catch((err) => setResolveError(err instanceof ApiError ? err.message : "Failed to load student enrollment"));
	}, [studentId]);

	function studentLabel(student: StudentResponse): string {
		const person = persons.find((p) => p.id === student.personId);
		return person ? `${person.firstName} ${person.lastName} (${student.admissionNumber})` : student.admissionNumber;
	}

	return (
		<Stack spacing={3}>
			<FeeCategoriesPanel />
			<FeeStructuresPanel />

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Student fees</Typography>
					<TextField select label="Student" value={studentId} onChange={(e) => setStudentId(e.target.value)} sx={{ maxWidth: 400 }}>
						{students.map((student) => (
							<MenuItem key={student.id} value={student.id}>
								{studentLabel(student)}
							</MenuItem>
						))}
					</TextField>

					{resolveError && <Alert severity="info">{resolveError}</Alert>}
				</Stack>
			</Paper>

			{currentEnrollment && schoolClassId && (
				<StudentFeesPanel studentEnrollmentId={currentEnrollment.id} academicYearId={currentEnrollment.academicYearId} schoolClassId={schoolClassId} />
			)}
		</Stack>
	);
}
