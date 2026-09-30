import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { getChildAnswerSheets, type AnswerSheet } from "../../api/learning";
import { AnnotatedPage } from "./AnnotatedPage";

/** Marked answer sheets the teacher has released for one linked child (#230). */
export function ChildAnswerSheetsPanel({ studentId }: { studentId: number }) {
	const [sheets, setSheets] = useState<AnswerSheet[] | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getChildAnswerSheets(studentId)
			.then(setSheets)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load answer sheets"));
	}, [studentId]);

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error">{error}</Alert>}
			{sheets?.length === 0 && <Alert severity="info">No marked answer sheets have been shared yet.</Alert>}
			{sheets?.map((sheet) => (
				<Paper key={sheet.id} sx={{ p: 2 }}>
					<Stack spacing={1.5}>
						<Typography variant="h6">{sheet.examName} · {sheet.subjectName}</Typography>
						<Typography variant="subtitle1">Marks: {sheet.totalMarks ?? "—"} / {sheet.maxMarks}</Typography>
						{sheet.overallRemarks && <Typography variant="body2">Teacher's remarks: {sheet.overallRemarks}</Typography>}
						{sheet.pages.map((page) => (
							<AnnotatedPage key={page.id} url={page.url} annotations={sheet.annotations.filter((a) => a.pageNumber === page.pageNumber)} />
						))}
					</Stack>
				</Paper>
			))}
		</Stack>
	);
}
