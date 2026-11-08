import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Radio from "@mui/material/Radio";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import type { AcademicYearResponse } from "../../api/academicYears";
import {
	applySchoolTemplate,
	getSchoolTemplatePreview,
	type SchoolTemplateApplyResponse,
	type SchoolTemplateCategoryStatus,
	type SchoolTemplatePreviewResponse,
} from "../../api/schoolTemplate";
import { colors } from "../../theme";

type Choice = "template" | "copy";

interface Props {
	/** The year being set up; null keeps the dialog closed. */
	year: AcademicYearResponse | null;
	/** True when another academic year exists to copy forward from. */
	canCopyForward: boolean;
	onClose: () => void;
	/** Hand the year over to the copy-forward (rollover) dialog. */
	onCopyForward: (year: AcademicYearResponse) => void;
}

function pending(rows: SchoolTemplateCategoryStatus[]): number {
	return rows.filter((row) => !row.alreadyExists).length;
}

/** What setting up a year offers: a new year starts with no classes or sections, so promotion and
 * attendance would have nothing to land in. Either run the school starter template into that year
 * (previewed first, skip-if-exists on the backend so it is safe to repeat) or hand over to the
 * copy-forward dialog. Dismissable at every step - "Skip for now" leaves the year as it is. */
export function NewYearSetupDialog({ year, canCopyForward, onClose, onCopyForward }: Props) {
	const navigate = useNavigate();
	const [choice, setChoice] = useState<Choice>("template");
	const [preview, setPreview] = useState<SchoolTemplatePreviewResponse | null>(null);
	const [result, setResult] = useState<SchoolTemplateApplyResponse | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const yearId = year?.id;

	useEffect(() => {
		setChoice("template");
		setPreview(null);
		setResult(null);
		setError(null);
		setBusy(false);
		if (yearId === undefined) {
			return;
		}
		let cancelled = false;
		getSchoolTemplatePreview(yearId)
			.then((response) => {
				if (!cancelled) {
					setPreview(response);
				}
			})
			.catch((err) => {
				if (!cancelled) {
					setError(err instanceof ApiError ? err.message : "Failed to load the starter template preview");
				}
			});
		return () => {
			cancelled = true;
		};
	}, [yearId]);

	async function handleApply() {
		if (yearId === undefined) {
			return;
		}
		setBusy(true);
		setError(null);
		try {
			setResult(await applySchoolTemplate(undefined, yearId));
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to apply the starter template");
		} finally {
			setBusy(false);
		}
	}

	const classesPending = preview ? pending(preview.classes) : 0;
	const sectionsPending = preview ? pending(preview.sections) : 0;
	const nothingToAdd = preview !== null && classesPending + sectionsPending === 0;

	function renderChoice(value: Choice, title: string, description: string, disabled = false) {
		const selected = choice === value;
		return (
			<Box
				onClick={() => !disabled && !busy && setChoice(value)}
				sx={{
					display: "flex",
					alignItems: "flex-start",
					gap: 0.5,
					p: 1.5,
					border: `1px solid ${selected ? colors.accent : colors.rule}`,
					borderRadius: 1,
					bgcolor: selected ? colors.accentSoft : "transparent",
					opacity: disabled ? 0.6 : 1,
					cursor: disabled ? "default" : "pointer",
				}}
			>
				<Radio
					checked={selected}
					disabled={disabled || busy}
					onChange={() => setChoice(value)}
					slotProps={{ input: { "aria-label": title } }}
					sx={{ p: 0.5 }}
				/>
				<Box>
					<Typography variant="body2" sx={{ fontWeight: 600 }}>
						{title}
					</Typography>
					<Typography variant="body2" color="text.secondary">
						{description}
					</Typography>
				</Box>
			</Box>
		);
	}

	function renderSummary(applied: SchoolTemplateApplyResponse) {
		const otherCreated =
			applied.gradesCreated.length +
			applied.subjectsCreated.length +
			applied.departmentsCreated.length +
			applied.designationsCreated.length +
			applied.rolesCreated.length +
			applied.feeCategoriesCreated.length +
			applied.itemCategoriesCreated.length +
			applied.itemsCreated.length +
			applied.assetTypesCreated.length +
			applied.leaveTypesCreated.length +
			applied.certificateTypesCreated.length;
		return (
			<Stack spacing={1.5}>
				<Alert severity="success">
					{year?.name} now has {applied.classesCreated.length} new {applied.classesCreated.length === 1 ? "class" : "classes"} and{" "}
					{applied.sectionsCreated.length} new {applied.sectionsCreated.length === 1 ? "section" : "sections"}.
				</Alert>
				{applied.classesNote && <Alert severity="warning">{applied.classesNote}</Alert>}
				<Stack>
					<Typography variant="body2" color="text.secondary">
						Classes: {applied.classesCreated.length} created, {applied.classesSkipped.length} already there
					</Typography>
					<Typography variant="body2" color="text.secondary">
						Sections: {applied.sectionsCreated.length} created, {applied.sectionsSkipped.length} already there
					</Typography>
					{otherCreated > 0 && (
						<Typography variant="body2" color="text.secondary">
							Other starter items added: {otherCreated}
						</Typography>
					)}
				</Stack>
			</Stack>
		);
	}

	function renderPreview() {
		if (!preview) {
			return error ? null : (
				<Typography variant="body2" color="text.secondary">
					Checking what the template would add to {year?.name}...
				</Typography>
			);
		}
		return (
			<Stack spacing={0.75}>
				<Typography variant="subtitle2">What it would do in {year?.name}</Typography>
				<Typography variant="body2" color="text.secondary">
					Classes: {classesPending} to create, {preview.classes.length - classesPending} already exist
				</Typography>
				<Typography variant="body2" color="text.secondary">
					Sections: {sectionsPending} to create, {preview.sections.length - sectionsPending} already exist
				</Typography>
				<Typography variant="caption" color="text.secondary">
					Any other starter items that are still missing (grades, subjects, ...) are added too. Anything that already exists is left
					alone, so it is safe to run again.
				</Typography>
				{nothingToAdd && (
					<Alert severity="info">
						{preview.classes.length === 0
							? "The template has no classes to add (the Academics module may be switched off, or no class rows are selected)."
							: "This year already has every class and section from the template."}
					</Alert>
				)}
			</Stack>
		);
	}

	function goToAcademicsSetup() {
		onClose();
		navigate("/academics/setup");
	}

	return (
		<Dialog open={year !== null} onClose={busy ? undefined : onClose} fullWidth maxWidth="sm">
			<DialogTitle>Set up {year?.name}</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ pt: 0.5 }}>
					<Typography variant="body2" color="text.secondary">
						A new academic year starts empty - no classes or sections - so promotion and attendance have nothing to land in until
						you set it up.
					</Typography>
					{error && <Alert severity="error">{error}</Alert>}
					{result ? (
						renderSummary(result)
					) : (
						<>
							<Stack spacing={1}>
								{renderChoice(
									"template",
									"Run the school starter template",
									"Creates the template's classes and sections in this year.",
								)}
								{renderChoice(
									"copy",
									"Copy forward from an earlier year",
									canCopyForward
										? "Copies the structure of another year into this one (classes, assignments, timetable, fee amounts)."
										: "There is no other academic year to copy from yet.",
									!canCopyForward,
								)}
							</Stack>
							{choice === "template" && renderPreview()}
						</>
					)}
				</Stack>
			</DialogContent>
			<DialogActions>
				{result ? (
					<>
						<Button onClick={onClose}>Close</Button>
						<Button variant="contained" onClick={goToAcademicsSetup}>
							Go to Academics setup
						</Button>
					</>
				) : (
					<>
						<Button onClick={onClose} disabled={busy}>
							Skip for now
						</Button>
						{choice === "copy" ? (
							<Button variant="contained" onClick={() => year && onCopyForward(year)} disabled={busy || !canCopyForward}>
								Continue to copy forward
							</Button>
						) : (
							<Button variant="contained" onClick={handleApply} disabled={busy || !preview || nothingToAdd}>
								Apply template
							</Button>
						)}
					</>
				)}
			</DialogActions>
		</Dialog>
	);
}
