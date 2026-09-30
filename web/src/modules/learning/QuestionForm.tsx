import { useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import Radio from "@mui/material/Radio";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import DeleteIcon from "@mui/icons-material/Delete";
import type { OptionInput } from "../../api/learning";

interface Props {
	submitLabel: string;
	onSubmit: (text: string, marks: number, options: OptionInput[]) => Promise<void>;
}

/** Question text, marks and 2-6 options with a radio marking the single correct one. Shared by quiz
 * authoring and the question bank. */
export function QuestionForm({ submitLabel, onSubmit }: Props) {
	const [text, setText] = useState("");
	const [marks, setMarks] = useState("1");
	const [options, setOptions] = useState<OptionInput[]>([
		{ text: "", correct: true },
		{ text: "", correct: false },
	]);
	const [error, setError] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);

	function updateOption(index: number, patch: Partial<OptionInput>) {
		setOptions((prev) => prev.map((o, i) => (i === index ? { ...o, ...patch } : o)));
	}

	async function handleSubmit() {
		setSaving(true);
		setError(null);
		try {
			await onSubmit(text, Number(marks), options);
			setText("");
			setMarks("1");
			setOptions([
				{ text: "", correct: true },
				{ text: "", correct: false },
			]);
		} catch (err) {
			setError(err instanceof Error ? err.message : "Failed to save the question");
		} finally {
			setSaving(false);
		}
	}

	return (
		<Stack spacing={1.5}>
			{error && <Alert severity="error">{error}</Alert>}
			<TextField label="Question" multiline minRows={2} size="small" value={text} onChange={(e) => setText(e.target.value)} />
			<TextField label="Marks" type="number" size="small" value={marks} onChange={(e) => setMarks(e.target.value)} sx={{ width: 120 }} slotProps={{ htmlInput: { min: 1 } }} />
			{options.map((option, i) => (
				<Stack key={i} direction="row" spacing={1} sx={{ alignItems: "center" }}>
					<Radio checked={option.correct} onChange={() => setOptions((prev) => prev.map((o, j) => ({ ...o, correct: j === i })))} slotProps={{ input: { "aria-label": `Option ${i + 1} is correct` } }} />
					<TextField size="small" fullWidth label={`Option ${i + 1}`} value={option.text} onChange={(e) => updateOption(i, { text: e.target.value })} />
					{options.length > 2 && (
						<IconButton aria-label="Remove option" onClick={() => setOptions((prev) => prev.filter((_, j) => j !== i).map((o, j, arr) => (arr.some((x) => x.correct) ? o : { ...o, correct: j === 0 })))}>
							<DeleteIcon fontSize="small" />
						</IconButton>
					)}
				</Stack>
			))}
			<Stack direction="row" spacing={1}>
				{options.length < 6 && <Button size="small" onClick={() => setOptions((prev) => [...prev, { text: "", correct: false }])}>Add option</Button>}
				<Button size="small" variant="contained" disabled={saving} onClick={handleSubmit}>{submitLabel}</Button>
			</Stack>
		</Stack>
	);
}
