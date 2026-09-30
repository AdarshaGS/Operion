import { useState, type SyntheticEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import { AnswerSheetsPanel } from "./AnswerSheetsPanel";
import { HomeworkPanel } from "./HomeworkPanel";
import { LessonPlansPanel } from "./LessonPlansPanel";
import { MaterialsPanel } from "./MaterialsPanel";
import { QuizzesPanel } from "./QuizzesPanel";
import { useLearningScope } from "./useLearningScope";

type LearningTab = "homework" | "quizzes" | "materials" | "lesson-plans" | "answer-sheets";

/** Homework, quizzes, materials, lesson plans, answer sheets and leaderboards. Mounted both in the
 * admin shell (/learning) and the teacher portal (/portal/teaching) - the API scopes each caller. */
export function LearningPage() {
	const [tab, setTab] = useState<LearningTab>("homework");
	const { scope, error } = useLearningScope();

	function handleChange(_event: SyntheticEvent, value: LearningTab) {
		setTab(value);
	}

	if (error) {
		return <Alert severity="error">{error}</Alert>;
	}
	if (!scope) {
		return <CircularProgress />;
	}

	return (
		<Stack spacing={2}>
			<Box sx={{ borderBottom: 1, borderColor: "divider" }}>
				<Tabs value={tab} onChange={handleChange} variant="scrollable" scrollButtons="auto">
					<Tab label="Homework" value="homework" />
					<Tab label="Quizzes" value="quizzes" />
					<Tab label="Materials" value="materials" />
					<Tab label="Lesson plans" value="lesson-plans" />
					<Tab label="Answer sheets" value="answer-sheets" />
				</Tabs>
			</Box>
			{tab === "homework" && <HomeworkPanel scope={scope} />}
			{tab === "quizzes" && <QuizzesPanel scope={scope} />}
			{tab === "materials" && <MaterialsPanel scope={scope} />}
			{tab === "lesson-plans" && <LessonPlansPanel scope={scope} />}
			{tab === "answer-sheets" && <AnswerSheetsPanel />}
		</Stack>
	);
}
