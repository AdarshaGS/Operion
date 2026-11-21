import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { ApiError } from "../../api/client";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { ClassSubjectsPanel } from "./ClassSubjectsPanel";
import { useClassLabel } from "./classLabel";
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
	const classLabel = useClassLabel();
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		if (!classId) return;
		const id = Number(classId);
		listSchoolClasses()
			.then((classes) => setSchoolClass(classes.find((c) => c.id === id) ?? null))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load class"));
	}, [classId]);

	if (!classId) return null;
	const id = Number(classId);

	return (
		<Stack spacing={2}>
			<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
				<Tooltip title="Back to academics">
					<IconButton onClick={() => navigate("/academics")} aria-label="Back to academics" sx={{ ml: -1 }}>
						<ArrowBackIcon />
					</IconButton>
				</Tooltip>
				<Typography variant="h4" component="h1">
					{schoolClass ? classLabel(schoolClass) : `Class #${classId}`}
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			<ClassSubjectsPanel classId={id} />
			<SectionsPanel classId={id} onSectionClick={(section) => navigate(`/academics/classes/${classId}/sections/${section.id}`)} />
		</Stack>
	);
}
