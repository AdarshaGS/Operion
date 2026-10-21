import { useEffect, useMemo, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { listAcademicYears, type AcademicYearResponse } from "../../api/academicYears";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { searchStudents, type StudentListRowResponse } from "../../api/students";
import { applyPromotions, type PromotionAction, type PromotionOutcome } from "../../api/promotions";
import { useAuth } from "../../auth/AuthContext";
import { useSchoolClassLabel } from "../../utils/useSchoolClassLabel";

interface SectionOption {
	id: number;
	label: string;
}

interface RowDecision {
	action: PromotionAction;
	targetSectionId: string;
	rollNumber: string;
}

/** Load every section of a year's classes, labelled "Class - Section", for the target pickers. */
async function loadSectionOptions(
	academicYearId: number,
	classLabel: (schoolClass: SchoolClassResponse) => string,
): Promise<{ classes: SchoolClassResponse[]; sections: SectionResponse[]; options: SectionOption[] }> {
	const classes = await listSchoolClasses(academicYearId);
	const perClass = await Promise.all(classes.map((c) => listSections(c.id)));
	const sections = perClass.flat();
	const options = sections.map((section) => {
		const schoolClass = classes.find((c) => c.id === section.schoolClassId);
		return { id: section.id, label: `${schoolClass ? classLabel(schoolClass) : "Class"} - ${section.name}` };
	});
	return { classes, sections, options };
}

/**
 * Year-end promotion for one section at a time: every student gets an explicit decision
 * (promote / repeat into a section of the next year, or graduate). Nothing is inferred -
 * the default target section is just a starting point the admin can change per student.
 */
export function StudentPromotionPage() {
	const classLabel = useSchoolClassLabel();
	const { hasPermission } = useAuth();
	const canGraduate = hasPermission("STUDENT_EXIT_MANAGE");

	const [years, setYears] = useState<AcademicYearResponse[]>([]);
	const [sourceYearId, setSourceYearId] = useState("");
	const [sourceSectionId, setSourceSectionId] = useState("");
	const [sourceOptions, setSourceOptions] = useState<SectionOption[]>([]);
	const [targetYearId, setTargetYearId] = useState("");
	const [targetOptions, setTargetOptions] = useState<SectionOption[]>([]);
	const [defaultTargetSectionId, setDefaultTargetSectionId] = useState("");
	const [promotionDate, setPromotionDate] = useState("");

	const [students, setStudents] = useState<StudentListRowResponse[]>([]);
	const [decisions, setDecisions] = useState<Record<number, RowDecision>>({});
	const [outcomes, setOutcomes] = useState<PromotionOutcome[] | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [busy, setBusy] = useState(false);

	useEffect(() => {
		listAcademicYears()
			.then((rows) => {
				setYears(rows);
				const current = rows.find((y) => y.current);
				if (current) {
					setSourceYearId(String(current.id));
					setPromotionDate(current.endDate);
					const next = rows
						.filter((y) => y.startDate > current.startDate)
						.sort((a, b) => a.startDate.localeCompare(b.startDate))[0];
					if (next) setTargetYearId(String(next.id));
				}
			})
			.catch(() => setError("Failed to load academic years"));
	}, []);

	useEffect(() => {
		setSourceSectionId("");
		setSourceOptions([]);
		if (!sourceYearId) return;
		loadSectionOptions(Number(sourceYearId), classLabel)
			.then((loaded) => setSourceOptions(loaded.options))
			.catch(() => setError("Failed to load sections"));
	}, [sourceYearId, classLabel]);

	useEffect(() => {
		setDefaultTargetSectionId("");
		setTargetOptions([]);
		if (!targetYearId) return;
		loadSectionOptions(Number(targetYearId), classLabel)
			.then((loaded) => setTargetOptions(loaded.options))
			.catch(() => setError("Failed to load target sections"));
	}, [targetYearId, classLabel]);

	useEffect(() => {
		setStudents([]);
		setDecisions({});
		setOutcomes(null);
		if (!sourceSectionId) return;
		searchStudents({ sectionId: Number(sourceSectionId), status: "ACTIVE", size: 200 })
			.then((page) => {
				const rows = [...page.content].sort((a, b) => a.firstName.localeCompare(b.firstName));
				setStudents(rows);
				setDecisions(Object.fromEntries(rows.map((s) => [s.id, { action: "PROMOTE", targetSectionId: "", rollNumber: "" }])));
			})
			.catch(() => setError("Failed to load students"));
	}, [sourceSectionId]);

	function updateRow(studentId: number, patch: Partial<RowDecision>) {
		setDecisions((prev) => ({ ...prev, [studentId]: { ...prev[studentId], ...patch } }));
	}

	function applyDefaultToAll(sectionId: string) {
		setDefaultTargetSectionId(sectionId);
		setDecisions((prev) =>
			Object.fromEntries(
				Object.entries(prev).map(([id, row]) => [id, row.action === "GRADUATE" ? row : { ...row, targetSectionId: sectionId }]),
			),
		);
	}

	const outcomeByStudent = useMemo(() => new Map((outcomes ?? []).map((o) => [o.studentId, o])), [outcomes]);
	const pendingStudents = students.filter((s) => !outcomeByStudent.get(s.id)?.succeeded);
	const missingTarget = pendingStudents.some((s) => decisions[s.id]?.action !== "GRADUATE" && !decisions[s.id]?.targetSectionId);

	async function submit() {
		if (pendingStudents.length === 0 || !promotionDate) return;
		setBusy(true);
		setError(null);
		try {
			const result = await applyPromotions({
				targetAcademicYearId: targetYearId ? Number(targetYearId) : null,
				promotionDate,
				decisions: pendingStudents.map((s) => {
					const row = decisions[s.id];
					return {
						studentId: s.id,
						action: row.action,
						targetSectionId: row.action === "GRADUATE" || !row.targetSectionId ? null : Number(row.targetSectionId),
						rollNumber: row.rollNumber ? Number(row.rollNumber) : null,
					};
				}),
			});
			// Keep earlier successes so a retry of the failed rows still shows the full picture.
			setOutcomes((prev) => [...(prev ?? []).filter((o) => o.succeeded), ...result]);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to apply promotions");
		} finally {
			setBusy(false);
		}
	}

	const succeeded = outcomes?.filter((o) => o.succeeded).length ?? 0;
	const failed = outcomes?.filter((o) => !o.succeeded).length ?? 0;

	return (
		<Stack spacing={2}>
			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Year-end promotion</Typography>
					<Typography variant="body2" color="text.secondary">
						Pick a section, choose what happens to each student, then apply. Each student is handled on their own, so one
						problem (a full section, a missing target) doesn't stop the rest. Set up next year's classes and sections first.
					</Typography>
					<Stack direction={{ xs: "column", md: "row" }} spacing={2}>
						<TextField select label="From year" value={sourceYearId} onChange={(e) => setSourceYearId(e.target.value)} sx={{ minWidth: 180 }}>
							{years.map((y) => (
								<MenuItem key={y.id} value={y.id}>
									{y.name}
								</MenuItem>
							))}
						</TextField>
						<TextField select label="From section" value={sourceSectionId} onChange={(e) => setSourceSectionId(e.target.value)} sx={{ minWidth: 220 }}>
							{sourceOptions.map((o) => (
								<MenuItem key={o.id} value={o.id}>
									{o.label}
								</MenuItem>
							))}
						</TextField>
						<TextField select label="To year" value={targetYearId} onChange={(e) => setTargetYearId(e.target.value)} sx={{ minWidth: 180 }}>
							{years
								.filter((y) => String(y.id) !== sourceYearId)
								.map((y) => (
									<MenuItem key={y.id} value={y.id}>
										{y.name}
									</MenuItem>
								))}
						</TextField>
						<TextField
							label="Promotion date"
							type="date"
							value={promotionDate}
							onChange={(e) => setPromotionDate(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
						/>
					</Stack>
					{targetYearId && targetOptions.length === 0 && (
						<Alert severity="warning">The target year has no sections yet. Create them (or run the year rollover) before promoting.</Alert>
					)}
				</Stack>
			</Paper>

			{error && <Alert severity="error">{error}</Alert>}
			{outcomes && (
				<Alert severity={failed > 0 ? "warning" : "success"}>
					{succeeded} student{succeeded === 1 ? "" : "s"} moved.
					{failed > 0 && ` ${failed} could not be moved - fix the rows marked below and apply again.`}
				</Alert>
			)}

			{sourceSectionId && (
				<Paper sx={{ p: 2 }}>
					<Stack spacing={2}>
						<Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ alignItems: { sm: "center" } }}>
							<TextField
								select
								label="Move everyone to"
								value={defaultTargetSectionId}
								onChange={(e) => applyDefaultToAll(e.target.value)}
								sx={{ minWidth: 240 }}
								disabled={targetOptions.length === 0}
							>
								{targetOptions.map((o) => (
									<MenuItem key={o.id} value={o.id}>
										{o.label}
									</MenuItem>
								))}
							</TextField>
							<Typography variant="body2" color="text.secondary">
								{students.length} active student{students.length === 1 ? "" : "s"} in this section
							</Typography>
						</Stack>

						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Student</TableCell>
										<TableCell>Decision</TableCell>
										<TableCell>Target section</TableCell>
										<TableCell>Roll no.</TableCell>
										<TableCell>Result</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{students.map((student) => {
										const row = decisions[student.id];
										const outcome = outcomeByStudent.get(student.id);
										const done = outcome?.succeeded === true;
										if (!row) return null;
										return (
											<TableRow key={student.id}>
												<TableCell>
													{student.firstName} {student.lastName ?? ""}
													<Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
														{student.admissionNumber}
													</Typography>
												</TableCell>
												<TableCell>
													<TextField
														select
														size="small"
														value={row.action}
														disabled={done}
														onChange={(e) => updateRow(student.id, { action: e.target.value as PromotionAction })}
														sx={{ minWidth: 130 }}
													>
														<MenuItem value="PROMOTE">Promote</MenuItem>
														<MenuItem value="REPEAT">Repeat year</MenuItem>
														{canGraduate && <MenuItem value="GRADUATE">Graduate</MenuItem>}
													</TextField>
												</TableCell>
												<TableCell>
													{row.action === "GRADUATE" ? (
														<Typography variant="body2" color="text.secondary">
															Leaves the school
														</Typography>
													) : (
														<TextField
															select
															size="small"
															value={row.targetSectionId}
															disabled={done}
															onChange={(e) => updateRow(student.id, { targetSectionId: e.target.value })}
															sx={{ minWidth: 200 }}
															error={!row.targetSectionId}
														>
															{targetOptions.map((o) => (
																<MenuItem key={o.id} value={o.id}>
																	{o.label}
																</MenuItem>
															))}
														</TextField>
													)}
												</TableCell>
												<TableCell>
													{row.action !== "GRADUATE" && (
														<TextField
															size="small"
															type="number"
															value={row.rollNumber}
															disabled={done}
															onChange={(e) => updateRow(student.id, { rollNumber: e.target.value })}
															sx={{ width: 90 }}
														/>
													)}
												</TableCell>
												<TableCell>
													{outcome &&
														(outcome.succeeded ? (
															<Typography variant="body2" color="success.main">
																Done
															</Typography>
														) : (
															<Typography variant="body2" color="error">
																{outcome.error ?? "Failed"}
															</Typography>
														))}
												</TableCell>
											</TableRow>
										);
									})}
								</TableBody>
							</Table>
						</TableContainer>

						<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
							<Button
								variant="contained"
								disabled={busy || pendingStudents.length === 0 || missingTarget || !promotionDate}
								onClick={submit}
							>
								Apply to {pendingStudents.length} student{pendingStudents.length === 1 ? "" : "s"}
							</Button>
							{missingTarget && (
								<Typography variant="body2" color="text.secondary">
									Choose a target section for every student who is promoted or repeating.
								</Typography>
							)}
						</Stack>
					</Stack>
				</Paper>
			)}
		</Stack>
	);
}
