import { useState, type SyntheticEvent } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Typography from "@mui/material/Typography";
import { ChildHomeworkPanel } from "../modules/learning/ChildHomeworkPanel";

type ChildTab = "homework";

/** Everything a guardian does for a linked child under the learning features (students have no
 * login of their own). The studentId is verified server-side against the caller's StudentGuardian links. */
export function ChildLearningPage() {
	const { studentId } = useParams();
	const id = Number(studentId);
	const [tab, setTab] = useState<ChildTab>("homework");

	function handleChange(_event: SyntheticEvent, value: ChildTab) {
		setTab(value);
	}

	return (
		<Stack spacing={2}>
			<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
				<Typography variant="h5">Homework &amp; learning</Typography>
				<Button size="small" component={RouterLink} to="/portal/guardian">
					Back to dashboard
				</Button>
			</Stack>
			<Box sx={{ borderBottom: 1, borderColor: "divider" }}>
				<Tabs value={tab} onChange={handleChange} variant="scrollable" scrollButtons="auto">
					<Tab label="Homework" value="homework" />
				</Tabs>
			</Box>
			{tab === "homework" && <ChildHomeworkPanel studentId={id} />}
		</Stack>
	);
}
