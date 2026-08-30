import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { ApiError } from "../../api/client";
import { listGradeLevels, type GradeLevelResponse } from "../../api/gradeLevels";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { ClassSubjectsPanel } from "./ClassSubjectsPanel";
import { SectionsPanel } from "./SectionsPanel";

/** No GET-by-id endpoint exists on SchoolClassController - at the scale of "classes in
 * one school" (tens, not thousands), resolving the current class from the already-cheap
 * list call is simpler than adding a single-purpose endpoint for it. Thin composition of
 * ClassSubjectsPanel/SectionsPanel (#246) - the same two panels also appear inline in
 * AcademicsPage's tabbed configuration area; this route stays for direct/bookmarked links
 * (e.g. the Academic Setup wizard's "add sections to this class" flow). */
export function SchoolClassSectionsPage() {
	const { classId } = useParams<{ classId: string }>();
	const navigate = useNavigate();
	const [schoolClass, setSchoolClass] = useState<SchoolClassResponse | null>(null);
	const [gradeLevels, setGradeLevels] = useState<GradeLevelResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		if (!classId) return;
		const id = Number(classId);
		listSchoolClasses()
			.then((classes) => setSchoolClass(classes.find((c) => c.id === id) ?? null))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load class"));
		listGradeLevels().then(setGradeLevels).catch(() => {});
	}, [classId]);

	if (!classId) return null;
	const id = Number(classId);
	const gradeLevelName = schoolClass ? gradeLevels.find((level) => level.id === schoolClass.gradeLevelId)?.name : undefined;

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/academics")}>
					Back to academics
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				{schoolClass?.displayName ?? gradeLevelName ?? `Class #${classId}`}
			</Typography>

			{error && <Alert severity="error">{error}</Alert>}

			<ClassSubjectsPanel classId={id} />
			<SectionsPanel classId={id} onSectionClick={(section) => navigate(`/academics/classes/${classId}/sections/${section.id}`)} />
		</Stack>
	);
}
