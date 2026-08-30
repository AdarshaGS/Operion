import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { listSections, type SectionResponse } from "../../api/sections";
import { TeacherAssignmentsPanel } from "./TeacherAssignmentsPanel";

/** No GET-by-id exists for Section - resolving via list+find, same tradeoff documented
 * for BookDetailPage/RouteDetailPage/ItemDetailPage/StaffDetailPage at this data scale.
 * Thin composition of TeacherAssignmentsPanel (#246) - the same panel also appears
 * inline in AcademicsPage's tabbed configuration area; this route stays for direct/
 * bookmarked links. */
export function SectionDetailPage() {
	const { classId, sectionId } = useParams<{ classId: string; sectionId: string }>();
	const navigate = useNavigate();

	const [section, setSection] = useState<SectionResponse | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		if (!classId || !sectionId) return;
		listSections(Number(classId))
			.then((sections) => setSection(sections.find((s) => s.id === Number(sectionId)) ?? null))
			.finally(() => setLoading(false));
	}, [classId, sectionId]);

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	if (!sectionId) return null;

	return (
		<Stack spacing={2}>
			<Box>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate(`/academics/classes/${classId}`)}>
					Back to class
				</Button>
			</Box>

			<Typography variant="h4" component="h1">
				Section {section?.name ?? sectionId}
			</Typography>

			<TeacherAssignmentsPanel sectionId={Number(sectionId)} />
		</Stack>
	);
}
