import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import PrintIcon from "@mui/icons-material/Print";
import { resolveAssetUrl } from "../../api/assets";
import { ApiError } from "../../api/client";
import { renderReportCard, type ReportCardRenderResponse } from "../../api/reportCards";

const PAGE_WIDTH: Record<string, string> = { A4: "210mm", LETTER: "216mm", LEGAL: "216mm" };

/** Printable/branded report card document (#243) - lays out the resolved render data from
 * the backend (branding + template style + subject marks), printed via the browser. No
 * PDF library exists in this project - same rendering-scope call as ID cards / Letter
 * Formats (#31). */
export function ReportCardPrintPage() {
	const { reportCardId } = useParams<{ reportCardId: string }>();
	const navigate = useNavigate();

	const [data, setData] = useState<ReportCardRenderResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		if (!reportCardId) return;
		renderReportCard(Number(reportCardId))
			.then(setData)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load report card"))
			.finally(() => setLoading(false));
	}, [reportCardId]);

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	if (error || !data) {
		return <Alert severity="error">{error ?? "Report card not found"}</Alert>;
	}

	const bordered = data.templateStyle !== "MINIMAL";
	const doubleBorder = data.templateStyle === "ELEGANT";
	const fontFamily = data.fontStyle === "Serif" ? "Georgia, 'Times New Roman', serif" : `${data.fontStyle}, system-ui, sans-serif`;

	return (
		<Stack spacing={2}>
			<Box className="no-print" sx={{ display: "flex", justifyContent: "space-between" }}>
				<Button startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)}>
					Back
				</Button>
				<Button variant="contained" startIcon={<PrintIcon />} onClick={() => window.print()}>
					Print
				</Button>
			</Box>

			<style>{`
				@media print {
					.no-print { display: none !important; }
					@page { size: ${data.pageSize === "A4" ? "A4" : "letter"}; margin: 12mm; }
				}
			`}</style>

			<Paper
				sx={{
					maxWidth: PAGE_WIDTH[data.pageSize] ?? "210mm",
					mx: "auto",
					p: 4,
					fontFamily,
					fontSize: `${data.fontSize}px`,
					border: bordered ? (doubleBorder ? "4px double #333" : "1px solid #333") : "none",
				}}
			>
				<Stack spacing={0.5} sx={{ mb: 2, alignItems: "center", textAlign: "center" }}>
					{data.logoRef && (
						<Box component="img" src={resolveAssetUrl(data.logoRef)} alt="Logo" sx={{ maxHeight: 64, objectFit: "contain", mb: 1 }} />
					)}
					<Typography variant="h5" sx={{ fontWeight: 700 }}>
						{data.schoolNameOverride ?? "Report Card"}
					</Typography>
					{data.addressLine && <Typography variant="body2">{data.addressLine}</Typography>}
					{data.affiliationText && <Typography variant="body2">{data.affiliationText}</Typography>}
					{data.headerSubtext && <Typography variant="subtitle2">{data.headerSubtext}</Typography>}
					<Typography variant="h6" sx={{ mt: 1 }}>
						{data.examName} ({data.examType}) — {data.academicYearName}
					</Typography>
				</Stack>

				<Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
					<Typography>
						<strong>Student:</strong> {data.studentName} ({data.admissionNumber})
					</Typography>
					<Typography>
						<strong>Class:</strong> {data.className} - {data.sectionName}
					</Typography>
				</Box>

				<Table size="small">
					<TableHead>
						<TableRow>
							<TableCell>Subject</TableCell>
							<TableCell align="right">Max marks</TableCell>
							<TableCell align="right">Pass marks</TableCell>
							<TableCell align="right">Marks obtained</TableCell>
							<TableCell>Result</TableCell>
							{data.subjects.some((s) => s.rank != null) && <TableCell align="right">Rank</TableCell>}
						</TableRow>
					</TableHead>
					<TableBody>
						{data.subjects.map((subject) => (
							<TableRow key={subject.subjectName}>
								<TableCell>{subject.subjectName}</TableCell>
								<TableCell align="right">{subject.maxMarks}</TableCell>
								<TableCell align="right">{subject.passMarks}</TableCell>
								<TableCell align="right">{subject.absent ? "Absent" : subject.marksObtained}</TableCell>
								<TableCell>
									<Chip size="small" color={subject.passed ? "success" : "error"} label={subject.passed ? "Pass" : "Fail"} />
								</TableCell>
								{data.subjects.some((s) => s.rank != null) && <TableCell align="right">{subject.rank ?? "—"}</TableCell>}
							</TableRow>
						))}
					</TableBody>
				</Table>

				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mt: 2 }}>
					<Typography>
						<strong>Total:</strong> {data.totalMarksObtained}/{data.totalMaxMarks} ({data.percentage.toFixed(1)}%)
					</Typography>
					<Typography>
						<strong>Grade:</strong> {data.overallGrade}
					</Typography>
					<Chip color={data.passed ? "success" : "error"} label={data.passed ? "PASS" : "FAIL"} />
					{data.classRank != null && (
						<Typography>
							<strong>Class rank:</strong> #{data.classRank}
						</Typography>
					)}
				</Box>

				{data.stale && (
					<Alert severity="warning" sx={{ mt: 2 }} className="no-print">
						This report card is stale - marks have been corrected since it was published.
					</Alert>
				)}

				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", mt: 6 }}>
					<Box sx={{ textAlign: "center" }}>
						{data.signatureRef && (
							<Box component="img" src={resolveAssetUrl(data.signatureRef)} alt="Signature" sx={{ maxHeight: 48, objectFit: "contain" }} />
						)}
						<Typography variant="caption" sx={{ display: "block" }}>
							Signature
						</Typography>
					</Box>
					<Box sx={{ textAlign: "center" }}>
						{data.stampRef && <Box component="img" src={resolveAssetUrl(data.stampRef)} alt="Stamp" sx={{ maxHeight: 48, objectFit: "contain" }} />}
						<Typography variant="caption" sx={{ display: "block" }}>
							Stamp
						</Typography>
					</Box>
				</Box>

				{data.footerText && (
					<Typography variant="caption" sx={{ display: "block", mt: 3, textAlign: "center" }}>
						{data.footerText}
					</Typography>
				)}
			</Paper>
		</Stack>
	);
}
