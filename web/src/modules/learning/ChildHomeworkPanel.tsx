import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Link from "@mui/material/Link";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { resolveAssetUrl } from "../../api/assets";
import { ApiError } from "../../api/client";
import { getChildHomework, submitChildHomework, type ChildHomework } from "../../api/learning";
import { AttachmentField } from "./AttachmentField";

/** Homework for one linked child: view assignments and submit answers on the child's behalf (#226). */
export function ChildHomeworkPanel({ studentId }: { studentId: number }) {
	const [items, setItems] = useState<ChildHomework[] | null>(null);
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		getChildHomework(studentId)
			.then(setItems)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load homework"));
	}

	// eslint-disable-next-line react-hooks/exhaustive-deps
	useEffect(refresh, [studentId]);

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
			{items?.length === 0 && <Alert severity="info">No homework has been assigned yet.</Alert>}
			{items?.map((hw) => (
				<HomeworkCard key={hw.id} studentId={studentId} homework={hw} onChanged={refresh} onError={setError} />
			))}
		</Stack>
	);
}

function HomeworkCard({ studentId, homework, onChanged, onError }: { studentId: number; homework: ChildHomework; onChanged: () => void; onError: (m: string) => void }) {
	const [text, setText] = useState(homework.submissionText ?? "");
	const [attachmentRef, setAttachmentRef] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);
	const reviewed = homework.submissionStatus === "REVIEWED";

	async function handleSubmit() {
		setSaving(true);
		try {
			await submitChildHomework(studentId, homework.id, text, attachmentRef);
			setAttachmentRef(null);
			onChanged();
		} catch (err) {
			onError(err instanceof ApiError ? err.message : "Failed to submit");
		} finally {
			setSaving(false);
		}
	}

	return (
		<Paper sx={{ p: 2 }}>
			<Stack spacing={1.5}>
				<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }} spacing={1}>
					<Typography variant="subtitle1">
						{homework.title} <Typography component="span" variant="body2" color="text.secondary">· {homework.subjectName}</Typography>
					</Typography>
					<Stack direction="row" spacing={0.5}>
						{homework.submissionStatus === "NOT_SUBMITTED" && <Chip label="To do" size="small" color="warning" />}
						{homework.submissionStatus === "SUBMITTED" && <Chip label="Submitted" size="small" color="info" />}
						{reviewed && <Chip label="Reviewed" size="small" color="success" />}
						{homework.late && <Chip label="Late" size="small" color="warning" variant="outlined" />}
					</Stack>
				</Stack>
				<Typography variant="body2" color="text.secondary">
					Due {homework.dueDate} · set by {homework.assignedByName}
				</Typography>
				{homework.description && <Typography variant="body2">{homework.description}</Typography>}
				{homework.attachmentUrl && (
					<Link href={resolveAssetUrl(homework.attachmentUrl)} target="_blank" rel="noreferrer" variant="body2">
						Attachment
					</Link>
				)}
				{homework.feedback && <Alert severity="success">Teacher feedback: {homework.feedback}</Alert>}
				{!reviewed && (
					<Stack spacing={1}>
						<TextField label="Answer" multiline minRows={2} size="small" value={text} onChange={(e) => setText(e.target.value)} />
						<AttachmentField label="Attach answer" value={attachmentRef} onChange={setAttachmentRef} onError={onError} />
						<div>
							<Button variant="contained" size="small" disabled={saving || (!text.trim() && !attachmentRef)} onClick={handleSubmit}>
								{homework.submissionStatus === "SUBMITTED" ? "Update submission" : "Submit"}
							</Button>
						</div>
					</Stack>
				)}
				{reviewed && homework.submissionText && <Typography variant="body2">Your answer: {homework.submissionText}</Typography>}
			</Stack>
		</Paper>
	);
}
