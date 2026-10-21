import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Tabs from "@mui/material/Tabs";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { ApiError } from "../../api/client";
import {
  addSchedule,
  getExam,
  listSchedules,
  type ExamResponse,
  type ExamScheduleResponse,
} from "../../api/exams";
import { listStudentEnrollments } from "../../api/enrollments";
import { getRegister, type MarksEntryRegisterResponse } from "../../api/marks";
import {
  listGradingScales,
  type GradingScaleResponse,
} from "../../api/gradingScales";
import { type PersonResponse, listPersons } from "../../api/persons";
import {
  downloadReportCardPdf,
  downloadSectionReportCardsPdf,
  publishReportCard,
  type ReportCardResponse,
} from "../../api/reportCards";
import {
  listSchoolClasses,
  type SchoolClassResponse,
} from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { listStudents, type StudentResponse } from "../../api/students";
import { listSubjects, type SubjectResponse } from "../../api/subjects";
import { useAuth } from "../../auth/AuthContext";
import { useSchoolClassLabel } from "../../utils/useSchoolClassLabel";

const TABS = ["overview", "schedule", "marks", "results", "reportCards"] as const;
type TabKey = (typeof TABS)[number];
const TAB_LABELS: Record<TabKey, string> = {
  overview: "Overview",
  schedule: "Schedule",
  marks: "Marks entry",
  results: "Results",
  reportCards: "Report cards",
};

/** Register state per schedule: a missing register row (id null) means nobody has started entering marks. */
function registerLabel(register: MarksEntryRegisterResponse | undefined): string {
  if (!register) return "Loading…";
  if (register.id == null) return "Not started";
  return { DRAFT: "In progress", SUBMITTED: "Awaiting approval", APPROVED: "Approved" }[register.registerStatus];
}

export function ExamDetailPage() {
  const { examId } = useParams<{ examId: string }>();
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const canManage = hasPermission("EXAM_MANAGE");
  const canPublish = hasPermission("REPORT_CARD_PUBLISH");

  const [exam, setExam] = useState<ExamResponse | null>(null);
  const [schedules, setSchedules] = useState<ExamScheduleResponse[]>([]);
  const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
  const classLabel = useSchoolClassLabel();
  const [subjects, setSubjects] = useState<SubjectResponse[]>([]);
  const [sectionsByClass, setSectionsByClass] = useState<
    Record<number, SectionResponse[]>
  >({});
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabKey>("overview");
  const [registers, setRegisters] = useState<Record<number, MarksEntryRegisterResponse>>({});

  const [scheduleDialogOpen, setScheduleDialogOpen] = useState(false);
  const [scheduleError, setScheduleError] = useState<string | null>(null);
  const [schoolClassId, setSchoolClassId] = useState("");
  const [dialogSections, setDialogSections] = useState<SectionResponse[]>([]);
  const [sectionId, setSectionId] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [examDate, setExamDate] = useState("");
  const [maxMarks, setMaxMarks] = useState("100");
  const [passMarks, setPassMarks] = useState("35");

  const [students, setStudents] = useState<StudentResponse[]>([]);
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [gradingScales, setGradingScales] = useState<GradingScaleResponse[]>(
    [],
  );
  const [reportDialogOpen, setReportDialogOpen] = useState(false);
  const [reportError, setReportError] = useState<string | null>(null);
  const [pdfSectionId, setPdfSectionId] = useState("");
  const [pdfError, setPdfError] = useState<string | null>(null);
  const [pdfBusy, setPdfBusy] = useState(false);

  async function handleSectionPdf() {
    if (!examId || !pdfSectionId) return;
    setPdfError(null);
    setPdfBusy(true);
    try {
      await downloadSectionReportCardsPdf(Number(examId), Number(pdfSectionId));
    } catch (err) {
      setPdfError(err instanceof ApiError ? err.message : "Failed to download the report cards");
    } finally {
      setPdfBusy(false);
    }
  }
  const [reportStudentId, setReportStudentId] = useState("");
  const [gradingScaleId, setGradingScaleId] = useState("");
  const [publishedReport, setPublishedReport] =
    useState<ReportCardResponse | null>(null);

  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!examId) return;
    getExam(Number(examId))
      .then(setExam)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Failed to load exam"),
      );
    refreshSchedules();
    listSchoolClasses()
      .then(setClasses)
      .catch(() => {});
    listSubjects()
      .then(setSubjects)
      .catch(() => {});
    listStudents()
      .then(setStudents)
      .catch(() => {});
    listPersons()
      .then(setPersons)
      .catch(() => {});
    listGradingScales()
      .then(setGradingScales)
      .catch(() => {});
  }, [examId]);

  function refreshSchedules() {
    if (!examId) return;
    listSchedules(Number(examId))
      .then((result) => {
        setSchedules(result);
        result.forEach((schedule) =>
          getRegister(schedule.id)
            .then((register) => setRegisters((prev) => ({ ...prev, [schedule.id]: register })))
            .catch(() => {}),
        );
        const classIds = [...new Set(result.map((s) => s.schoolClassId))];
        classIds.forEach((classId) => {
          if (sectionsByClass[classId]) return;
          listSections(classId)
            .then((sections) =>
              setSectionsByClass((prev) => ({ ...prev, [classId]: sections })),
            )
            .catch(() => {});
        });
      })
      .catch((err) =>
        setError(
          err instanceof ApiError ? err.message : "Failed to load schedules",
        ),
      );
  }

  function className(id: number): string {
    const schoolClass = classes.find((c) => c.id === id);
    return schoolClass ? classLabel(schoolClass) : `Class #${id}`;
  }

  function subjectName(id: number): string {
    return subjects.find((s) => s.id === id)?.name ?? `Subject #${id}`;
  }

  function sectionName(schoolClassId: number, secId: number | null): string {
    if (secId == null) return "All sections";
    return (
      sectionsByClass[schoolClassId]?.find((s) => s.id === secId)?.name ??
      `Section #${secId}`
    );
  }

  function studentLabel(student: StudentResponse): string {
    const person = persons.find((p) => p.id === student.personId);
    return person
      ? `${person.firstName} ${person.lastName} (${student.admissionNumber})`
      : student.admissionNumber;
  }

  function handleSchoolClassChange(value: string) {
    setSchoolClassId(value);
    setSectionId("");
    setDialogSections([]);
    if (value) {
      listSections(Number(value))
        .then(setDialogSections)
        .catch(() => {});
    }
  }

  async function handleAddSchedule(event: FormEvent) {
    event.preventDefault();
    if (!examId) return;
    setSubmitting(true);
    setScheduleError(null);
    try {
      await addSchedule(Number(examId), {
        schoolClassId: Number(schoolClassId),
        sectionId: sectionId ? Number(sectionId) : null,
        subjectId: Number(subjectId),
        examDate,
        maxMarks: Number(maxMarks),
        passMarks: Number(passMarks),
      });
      setSchoolClassId("");
      setSectionId("");
      setDialogSections([]);
      setSubjectId("");
      setExamDate("");
      setScheduleDialogOpen(false);
      refreshSchedules();
    } catch (err) {
      setScheduleError(
        err instanceof ApiError ? err.message : "Failed to add schedule",
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function handlePublishReportCard(event: FormEvent) {
    event.preventDefault();
    if (!examId) return;
    setSubmitting(true);
    setPublishedReport(null);
    setReportError(null);
    try {
      const enrollments = await listStudentEnrollments(Number(reportStudentId));
      const current = enrollments.find((e) => e.current);
      if (!current) {
        setReportError("This student has no current enrollment.");
        return;
      }
      const reportCard = await publishReportCard(
        Number(examId),
        current.id,
        Number(gradingScaleId),
      );
      setPublishedReport(reportCard);
      setReportDialogOpen(false);
    } catch (err) {
      setReportError(
        err instanceof ApiError ? err.message : "Failed to publish report card",
      );
    } finally {
      setSubmitting(false);
    }
  }

  const approvedCount = schedules.filter((s) => registers[s.id]?.registerStatus === "APPROVED" && registers[s.id]?.id != null).length;
  const startedCount = schedules.filter((s) => registers[s.id]?.id != null).length;
  const dates = schedules.map((s) => s.examDate).sort();
  const classCount = new Set(schedules.map((s) => s.schoolClassId)).size;

  if (!exam) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate("/examinations")}
        >
          Back to examinations
        </Button>
      </Box>

      <Typography variant="h4" component="h1">
        {exam.name} — {exam.examType}
      </Typography>

      {error && <Alert severity="error">{error}</Alert>}

      <Tabs value={tab} onChange={(_, value) => setTab(value)}>
        {TABS.map((t) => (
          <Tab key={t} label={TAB_LABELS[t]} value={t} />
        ))}
      </Tabs>

      {tab === "overview" && (
        <Paper sx={{ p: 3 }}>
          <Stack spacing={1.5}>
            <Typography variant="h6">Overview</Typography>
            <Typography variant="body2">
              {exam.examType} · {exam.status === "ACTIVE" ? "Active" : exam.status}
            </Typography>
            <Typography variant="body2">
              {schedules.length === 0
                ? "No schedules yet — add them under the Schedule tab."
                : `${schedules.length} schedule${schedules.length === 1 ? "" : "s"} across ${classCount} class${classCount === 1 ? "" : "es"}, ${dates[0]}${dates[0] !== dates[dates.length - 1] ? ` to ${dates[dates.length - 1]}` : ""}.`}
            </Typography>
            {schedules.length > 0 && (
              <Typography variant="body2">
                Marks entry: {startedCount} of {schedules.length} started, {approvedCount} approved.
              </Typography>
            )}
          </Stack>
        </Paper>
      )}

      {tab === "schedule" && (
      <Paper sx={{ p: 3 }}>
        <Stack spacing={2}>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            }}
          >
            <Typography variant="h6">Schedules</Typography>
            {canManage && (
              <Button
                size="small"
                startIcon={<AddIcon />}
                onClick={() => {
                  setScheduleError(null);
                  setScheduleDialogOpen(true);
                }}
              >
                Add schedule
              </Button>
            )}
          </Box>

          {schedules.length === 0 && (
            <Alert severity="info">No schedules yet.</Alert>
          )}

          {schedules.length > 0 && (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Class</TableCell>
                    <TableCell>Section</TableCell>
                    <TableCell>Subject</TableCell>
                    <TableCell>Date</TableCell>
                    <TableCell>Max marks</TableCell>
                    <TableCell>Pass marks</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {schedules.map((schedule) => (
                    <TableRow
                      key={schedule.id}
                      hover
                      sx={{ cursor: "pointer" }}
                      onClick={() =>
                        navigate(
                          `/examinations/exams/${examId}/schedules/${schedule.id}`,
                        )
                      }
                    >
                      <TableCell>{className(schedule.schoolClassId)}</TableCell>
                      <TableCell>
                        {sectionName(
                          schedule.schoolClassId,
                          schedule.sectionId,
                        )}
                      </TableCell>
                      <TableCell>{subjectName(schedule.subjectId)}</TableCell>
                      <TableCell>{schedule.examDate}</TableCell>
                      <TableCell>{schedule.maxMarks}</TableCell>
                      <TableCell>{schedule.passMarks}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Stack>
      </Paper>
      )}

      {tab === "marks" && (
        <Paper sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Typography variant="h6">Marks entry</Typography>
            {schedules.length === 0 && <Alert severity="info">No schedules yet — add them under the Schedule tab before entering marks.</Alert>}
            {schedules.length > 0 && (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Class</TableCell>
                      <TableCell>Section</TableCell>
                      <TableCell>Subject</TableCell>
                      <TableCell>Date</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {schedules.map((schedule) => (
                      <TableRow key={schedule.id} hover>
                        <TableCell>{className(schedule.schoolClassId)}</TableCell>
                        <TableCell>{sectionName(schedule.schoolClassId, schedule.sectionId)}</TableCell>
                        <TableCell>{subjectName(schedule.subjectId)}</TableCell>
                        <TableCell>{schedule.examDate}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={registerLabel(registers[schedule.id])}
                            color={registers[schedule.id]?.registerStatus === "APPROVED" && registers[schedule.id]?.id != null ? "success" : "default"}
                          />
                        </TableCell>
                        <TableCell align="right">
                          <Button size="small" onClick={() => navigate(`/examinations/exams/${examId}/schedules/${schedule.id}`)}>
                            Open
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Stack>
        </Paper>
      )}

      {tab === "results" && (
        <Paper sx={{ p: 3 }}>
          <Stack spacing={1.5}>
            <Typography variant="h6">Results</Typography>
            {schedules.length === 0 ? (
              <Alert severity="info">Results need at least one schedule with approved marks.</Alert>
            ) : approvedCount === schedules.length ? (
              <Alert severity="success">All {schedules.length} marks registers are approved — report cards can be published.</Alert>
            ) : (
              <Alert severity="warning">
                {schedules.length - approvedCount} of {schedules.length} marks registers still need approval before results are final.
              </Alert>
            )}
            <Box>
              <Button size="small" onClick={() => setTab("marks")}>
                Review marks entry
              </Button>
              <Button size="small" onClick={() => setTab("reportCards")}>
                Go to report cards
              </Button>
            </Box>
          </Stack>
        </Paper>
      )}

      {tab === "reportCards" && (
      <Paper sx={{ p: 3 }}>
        <Stack spacing={2}>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            }}
          >
            <Typography variant="h6">Report card</Typography>
            {canPublish && (
              <Button
                size="small"
                startIcon={<AddIcon />}
                onClick={() => {
                  setReportError(null);
                  setReportDialogOpen(true);
                }}
              >
                Publish report card
              </Button>
            )}
          </Box>

          {publishedReport && (
            <Stack spacing={1}>
              <Alert severity={publishedReport.passed ? "success" : "warning"}>
                {publishedReport.totalMarksObtained}/
                {publishedReport.totalMaxMarks} (
                {publishedReport.percentage.toFixed(1)}%) — grade{" "}
                {publishedReport.overallGrade} —{" "}
                <Chip
                  size="small"
                  color={publishedReport.passed ? "success" : "error"}
                  label={publishedReport.passed ? "PASS" : "FAIL"}
                />
                {publishedReport.classRank != null &&
                  ` · Class rank #${publishedReport.classRank}`}
                {publishedReport.stale &&
                  " · stale (marks corrected since publish — republish to update)"}
              </Alert>
              <Typography variant="caption" color="text.secondary">
                Published by user #{publishedReport.publishedBy ?? "—"} on{" "}
                {new Date(publishedReport.publishedAt).toLocaleString()}
              </Typography>
              <Box>
                <Button
                  size="small"
                  onClick={() =>
                    navigate(
                      `/examinations/report-cards/${publishedReport.id}/print`,
                    )
                  }
                >
                  View / print
                </Button>
                <Button
                  size="small"
                  onClick={() =>
                    downloadReportCardPdf(publishedReport.id).catch((err) =>
                      setPdfError(err instanceof ApiError ? err.message : "Failed to download the PDF"),
                    )
                  }
                >
                  Download PDF
                </Button>
              </Box>
            </Stack>
          )}
          {!publishedReport && <Alert severity="info">Publish a report card for a student to see the result here.</Alert>}

          <Typography variant="subtitle1">Print a whole section</Typography>
          <Box sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}>
            <TextField
              select
              size="small"
              label="Section"
              value={pdfSectionId}
              onChange={(e) => setPdfSectionId(e.target.value)}
              sx={{ minWidth: 240 }}
            >
              {Object.entries(sectionsByClass).flatMap(([classId, sections]) =>
                sections.map((section) => (
                  <MenuItem key={section.id} value={section.id}>
                    {className(Number(classId))} - {section.name}
                  </MenuItem>
                )),
              )}
            </TextField>
            <Button variant="outlined" disabled={!pdfSectionId || pdfBusy} onClick={handleSectionPdf}>
              {pdfBusy ? "Preparing…" : "Download section report cards (PDF)"}
            </Button>
          </Box>
          {pdfError && <Alert severity="error">{pdfError}</Alert>}
        </Stack>
      </Paper>
      )}

      <Dialog
        open={scheduleDialogOpen}
        onClose={() => setScheduleDialogOpen(false)}
        component="form"
        onSubmit={handleAddSchedule}
        fullWidth
        maxWidth="xs"
      >
        <DialogTitle>Add schedule</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {scheduleError && <Alert severity="error">{scheduleError}</Alert>}
            <TextField
              select
              label="Class"
              value={schoolClassId}
              onChange={(e) => handleSchoolClassChange(e.target.value)}
              required
              fullWidth
            >
              {classes.map((schoolClass) => (
                <MenuItem key={schoolClass.id} value={schoolClass.id}>
                  {classLabel(schoolClass)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Section (optional — leave blank for all sections)"
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
              disabled={!schoolClassId}
              fullWidth
            >
              <MenuItem value="">All sections</MenuItem>
              {dialogSections.map((section) => (
                <MenuItem key={section.id} value={section.id}>
                  {section.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Subject"
              value={subjectId}
              onChange={(e) => setSubjectId(e.target.value)}
              required
              fullWidth
            >
              {subjects.map((subject) => (
                <MenuItem key={subject.id} value={subject.id}>
                  {subject.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Exam date"
              type="date"
              value={examDate}
              onChange={(e) => setExamDate(e.target.value)}
              required
              slotProps={{ inputLabel: { shrink: true } }}
              fullWidth
            />
            <Box sx={{ display: "flex", gap: 2 }}>
              <TextField
                label="Max marks"
                type="number"
                value={maxMarks}
                onChange={(e) => setMaxMarks(e.target.value)}
                required
                fullWidth
              />
              <TextField
                label="Pass marks"
                type="number"
                value={passMarks}
                onChange={(e) => setPassMarks(e.target.value)}
                required
                fullWidth
              />
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setScheduleDialogOpen(false)}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            Add
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={reportDialogOpen}
        onClose={() => setReportDialogOpen(false)}
        component="form"
        onSubmit={handlePublishReportCard}
        fullWidth
        maxWidth="xs"
      >
        <DialogTitle>Publish report card</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {reportError && <Alert severity="error">{reportError}</Alert>}
            <TextField
              select
              label="Student"
              value={reportStudentId}
              onChange={(e) => setReportStudentId(e.target.value)}
              required
              fullWidth
            >
              {students.map((student) => (
                <MenuItem key={student.id} value={student.id}>
                  {studentLabel(student)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Grading scale"
              value={gradingScaleId}
              onChange={(e) => setGradingScaleId(e.target.value)}
              required
              fullWidth
            >
              {gradingScales.map((scale) => (
                <MenuItem key={scale.id} value={scale.id}>
                  {scale.name}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReportDialogOpen(false)}>Cancel</Button>
          <Button
            type="submit"
            variant="contained"
            disabled={submitting || !reportStudentId || !gradingScaleId}
          >
            Publish
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
