import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import {
	recommendStarterPack,
	type StarterPackAnswers,
	type StarterPackModule,
	type StarterPackQuestion,
} from "../../api/schoolTemplate";

interface Props {
	open: boolean;
	questions: StarterPackQuestion[];
	applying: boolean;
	onClose: () => void;
	onApply: (answers: StarterPackAnswers) => void;
}

/** The School Starter Pack's "what does your school need?" step. Every change re-asks the
 * backend which modules that implies (nothing is saved until Apply), so the dependency rules
 * - Transport needs Fees, Payroll needs HR - live in one place and the list below can never
 * disagree with what Apply will do. */
export function StarterPackDialog({ open, questions, applying, onClose, onApply }: Props) {
	const [answers, setAnswers] = useState<StarterPackAnswers>({});
	const [modules, setModules] = useState<StarterPackModule[]>([]);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		setAnswers(Object.fromEntries(questions.map((question) => [question.key, question.defaultYes])));
	}, [questions]);

	useEffect(() => {
		if (!open || Object.keys(answers).length === 0) {
			return;
		}
		let current = true;
		recommendStarterPack(answers)
			.then((response) => {
				if (current) {
					setModules(response.modules);
					setError(null);
				}
			})
			.catch((err) => current && setError(err instanceof ApiError ? err.message : "Failed to work out the module selection"));
		return () => {
			current = false;
		};
	}, [open, answers]);

	const enabled = modules.filter((module) => module.enabled);
	const disabled = modules.filter((module) => !module.enabled);

	return (
		<Dialog open={open} onClose={applying ? undefined : onClose} fullWidth maxWidth="md">
			<DialogTitle>Set up your school</DialogTitle>
			<DialogContent>
				<Stack spacing={2.5} sx={{ pt: 0.5 }}>
					<Typography variant="body2" color="text.secondary">
						Tell us what your school does and we will switch on only the modules you need, then create the matching
						grades, roles, fee categories and so on. The platform owner can change modules later.
					</Typography>
					{error && <Alert severity="error">{error}</Alert>}
					<Stack>
						{questions.map((question) => (
							<Box key={question.key}>
								<FormControlLabel
									control={
										<Switch
											checked={answers[question.key] ?? question.defaultYes}
											onChange={(event) => setAnswers((previous) => ({ ...previous, [question.key]: event.target.checked }))}
										/>
									}
									label={question.label}
								/>
								{question.helpText && (
									<Typography variant="caption" color="text.secondary" sx={{ display: "block", ml: 6, mt: -0.5, mb: 0.5 }}>
										{question.helpText}
									</Typography>
								)}
							</Box>
						))}
					</Stack>
					{modules.length > 0 && (
						<Stack spacing={1}>
							<Typography variant="subtitle2">Modules switched on ({enabled.length})</Typography>
							<Stack direction="row" sx={{ flexWrap: "wrap", gap: 0.75 }}>
								{enabled.map((module) => (
									<Chip key={module.code} size="small" color={module.locked ? "default" : "primary"} label={module.label} title={module.reason} />
								))}
							</Stack>
							{enabled.some((module) => module.reason.startsWith("Required by")) && (
								<Alert severity="info" variant="outlined">
									{enabled
										.filter((module) => module.reason.startsWith("Required by"))
										.map((module) => `${module.label}: ${module.reason.toLowerCase()}`)
										.join(" · ")}
								</Alert>
							)}
							<Typography variant="subtitle2" sx={{ pt: 1 }}>
								Hidden for your school ({disabled.length})
							</Typography>
							<Stack direction="row" sx={{ flexWrap: "wrap", gap: 0.75 }}>
								{disabled.map((module) => (
									<Chip key={module.code} size="small" variant="outlined" label={module.label} title={module.reason} />
								))}
							</Stack>
						</Stack>
					)}
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={applying}>
					Cancel
				</Button>
				<Button variant="contained" onClick={() => onApply(answers)} disabled={applying || modules.length === 0}>
					Apply starter pack
				</Button>
			</DialogActions>
		</Dialog>
	);
}
