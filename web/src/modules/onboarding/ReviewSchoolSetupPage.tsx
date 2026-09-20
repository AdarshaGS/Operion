import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import RadioButtonUncheckedIcon from "@mui/icons-material/RadioButtonUnchecked";
import { ApiError } from "../../api/client";
import { getSchoolTemplatePreview, type SchoolTemplatePreviewResponse } from "../../api/schoolTemplate";
import { colors } from "../../theme";

interface ReviewRow {
	label: string;
	done: boolean;
	path: string;
	ctaLabel: string;
}

function rowsFrom(preview: SchoolTemplatePreviewResponse): ReviewRow[] {
	const anyPresent = (items: { alreadyExists: boolean }[]) => items.some((item) => item.alreadyExists);
	return [
		{ label: "Grades", done: anyPresent(preview.grades), path: "/academics", ctaLabel: "Manage grades" },
		{ label: "Departments", done: anyPresent(preview.departments), path: "/settings/departments", ctaLabel: "Manage departments" },
		{ label: "Designations", done: anyPresent(preview.designations), path: "/settings/designations", ctaLabel: "Manage designations" },
		{ label: "Roles", done: anyPresent(preview.roles), path: "/settings/roles", ctaLabel: "Manage roles" },
		{ label: "Fee categories", done: anyPresent(preview.feeCategories), path: "/fees/setup", ctaLabel: "Manage fee categories" },
		{ label: "Inventory categories", done: anyPresent(preview.itemCategories), path: "/inventory", ctaLabel: "Manage inventory categories" },
		{ label: "Grading scale", done: preview.gradingScaleExists, path: "/examinations", ctaLabel: "Manage grading scales" },
	];
}

/** Read-only summary across every category the School setup template can pre-fill (see
 * SchoolTemplatePanel, Organisation Settings). Reuses the same preview endpoint the
 * template panel uses instead of re-deriving status per category the way
 * StructureSetupPage/AcademicSetupPage's own ReviewStep components each do independently.
 * Guidance, not a gate - every linked screen is reachable normally regardless of progress,
 * same philosophy as the Dashboard's SetupProgress card. */
export function ReviewSchoolSetupPage() {
	const navigate = useNavigate();
	const [preview, setPreview] = useState<SchoolTemplatePreviewResponse | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getSchoolTemplatePreview()
			.then(setPreview)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load school setup status"));
	}, []);

	const rows = preview ? rowsFrom(preview) : [];

	return (
		<Stack spacing={3}>
			<Box>
				<Typography variant="h4" sx={{ color: colors.ink }}>
					Review school setup
				</Typography>
				<Typography variant="body1" sx={{ color: colors.inkSoft, mt: 0.5 }}>
					A quick check across everything the school setup template can pre-fill. Nothing here is required -
					every item is reachable from Settings at any time.
				</Typography>
			</Box>

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					{error && (
						<Typography variant="body2" color="error">
							{error}
						</Typography>
					)}
					<Stack spacing={0}>
						{rows.map((row) => (
							<Box
								key={row.label}
								sx={{
									display: "flex",
									alignItems: "center",
									justifyContent: "space-between",
									gap: 1.5,
									py: 1.25,
									borderBottom: `1px solid ${colors.rule}`,
								}}
							>
								<Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
									{row.done ? (
										<CheckCircleIcon fontSize="small" sx={{ color: colors.ok }} />
									) : (
										<RadioButtonUncheckedIcon fontSize="small" sx={{ color: colors.inkFaint }} />
									)}
									<Typography variant="body2" sx={{ color: colors.ink }}>
										{row.label}
									</Typography>
								</Stack>
								<Button size="small" onClick={() => navigate(row.path)}>
									{row.ctaLabel}
								</Button>
							</Box>
						))}
					</Stack>
				</Stack>
			</Paper>

			<Box>
				<Button onClick={() => navigate("/settings/school-template")} sx={{ color: colors.inkSoft }}>
					Back to school setup template
				</Button>
			</Box>
		</Stack>
	);
}
