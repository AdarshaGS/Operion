import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { ApiError } from "../../api/client";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";
import { searchStudents, type StudentListRowResponse } from "../../api/students";
import { ApplicantsPanel } from "./ApplicantsPanel";
import { TransferRequestsInboxPanel } from "./TransferRequestsInboxPanel";

const PAGE_SIZE = 25;
const STUDENT_STATUSES = ["ADMITTED", "ACTIVE", "TRANSFERRED_OUT", "GRADUATED", "WITHDRAWN", "ALUMNI"];

export function StudentListPage() {
	const navigate = useNavigate();
	const [rows, setRows] = useState<StudentListRowResponse[]>([]);
	const [totalElements, setTotalElements] = useState(0);
	const [page, setPage] = useState(0);
	const [loaded, setLoaded] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const [search, setSearch] = useState("");
	const [status, setStatus] = useState("");
	const [schoolClassId, setSchoolClassId] = useState("");
	const [sectionId, setSectionId] = useState("");
	const [admissionDateFrom, setAdmissionDateFrom] = useState("");
	const [admissionDateTo, setAdmissionDateTo] = useState("");

	const [schoolClasses, setSchoolClasses] = useState<SchoolClassResponse[]>([]);
	const [sections, setSections] = useState<SectionResponse[]>([]);

	useEffect(() => {
		listSchoolClasses().then(setSchoolClasses).catch(() => {});
	}, []);

	useEffect(() => {
		setSectionId("");
		setSections([]);
		if (!schoolClassId) return;
		listSections(Number(schoolClassId)).then(setSections).catch(() => {});
	}, [schoolClassId]);

	useEffect(() => {
		searchStudents({
			search: search || null,
			status: status || null,
			schoolClassId: schoolClassId ? Number(schoolClassId) : null,
			sectionId: sectionId ? Number(sectionId) : null,
			admissionDateFrom: admissionDateFrom || null,
			admissionDateTo: admissionDateTo || null,
			page,
			size: PAGE_SIZE,
		})
			.then((result) => {
				setRows(result.content);
				setTotalElements(result.totalElements);
				setLoaded(true);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load students"));
	}, [search, status, schoolClassId, sectionId, admissionDateFrom, admissionDateTo, page]);

	function resetPageAnd<T>(setter: (value: T) => void) {
		return (value: T) => {
			setter(value);
			setPage(0);
		};
	}

	const hasFilters = search !== "" || status !== "" || schoolClassId !== "" || admissionDateFrom !== "" || admissionDateTo !== "";

	function clearFilters() {
		setSearch("");
		setStatus("");
		setSchoolClassId("");
		setAdmissionDateFrom("");
		setAdmissionDateTo("");
		setPage(0);
	}

	return (
		<Stack spacing={2}>
			<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
				<Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate("/students/new")}>
					Admit student
				</Button>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 2 }}>
				<Stack spacing={2}>
					<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
						<TextField
							label="Search"
							placeholder="Name or admission number"
							size="small"
							value={search}
							onChange={(e) => resetPageAnd(setSearch)(e.target.value)}
							sx={{ minWidth: 220 }}
						/>
						<TextField
							select
							label="Class"
							size="small"
							value={schoolClassId}
							onChange={(e) => resetPageAnd(setSchoolClassId)(e.target.value)}
							sx={{ minWidth: 160 }}
						>
							<MenuItem value="">All</MenuItem>
							{schoolClasses.map((schoolClass) => (
								<MenuItem key={schoolClass.id} value={schoolClass.id}>
									{schoolClass.displayName ?? `Class #${schoolClass.id}`}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Section"
							size="small"
							value={sectionId}
							onChange={(e) => resetPageAnd(setSectionId)(e.target.value)}
							disabled={!schoolClassId}
							sx={{ minWidth: 140 }}
						>
							<MenuItem value="">All</MenuItem>
							{sections.map((section) => (
								<MenuItem key={section.id} value={section.id}>
									{section.name}
								</MenuItem>
							))}
						</TextField>
						<TextField
							select
							label="Status"
							size="small"
							value={status}
							onChange={(e) => resetPageAnd(setStatus)(e.target.value)}
							sx={{ minWidth: 160 }}
						>
							<MenuItem value="">All</MenuItem>
							{STUDENT_STATUSES.map((s) => (
								<MenuItem key={s} value={s}>
									{s.replaceAll("_", " ")}
								</MenuItem>
							))}
						</TextField>
						<TextField
							label="Admitted from"
							type="date"
							size="small"
							value={admissionDateFrom}
							onChange={(e) => resetPageAnd(setAdmissionDateFrom)(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
						/>
						<TextField
							label="Admitted to"
							type="date"
							size="small"
							value={admissionDateTo}
							onChange={(e) => resetPageAnd(setAdmissionDateTo)(e.target.value)}
							slotProps={{ inputLabel: { shrink: true } }}
						/>
						{hasFilters && (
							<Button size="small" onClick={clearFilters} sx={{ alignSelf: "center" }}>
								Clear filters
							</Button>
						)}
					</Stack>

					<Typography variant="body2" color="text.secondary">
						{totalElements} student{totalElements === 1 ? "" : "s"}
					</Typography>
				</Stack>
			</Paper>

			{loaded && rows.length === 0 && (
				<Alert severity="info">{hasFilters ? "No students match these filters." : "No students admitted yet."}</Alert>
			)}

			{rows.length > 0 && (
				<TableContainer component={Paper}>
					<Table>
						<TableHead>
							<TableRow>
								<TableCell>Admission #</TableCell>
								<TableCell>Name</TableCell>
								<TableCell>Class / section</TableCell>
								<TableCell>Guardian contact</TableCell>
								<TableCell>Admission date</TableCell>
								<TableCell>Status</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{rows.map((student) => (
								<TableRow key={student.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/students/${student.id}`)}>
									<TableCell>{student.admissionNumber}</TableCell>
									<TableCell>
										{student.firstName} {student.lastName ?? ""}
									</TableCell>
									<TableCell>
										{student.schoolClassDisplayName || student.sectionName
											? `${student.schoolClassDisplayName ?? "—"} ${student.sectionName ?? ""}`.trim()
											: "—"}
									</TableCell>
									<TableCell>
										{student.primaryGuardianName
											? `${student.primaryGuardianName}${student.primaryGuardianPhone ? ` (${student.primaryGuardianPhone})` : ""}`
											: "—"}
									</TableCell>
									<TableCell>{student.admissionDate}</TableCell>
									<TableCell>
										<Chip label={student.status} size="small" />
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
					<TablePagination
						component="div"
						count={totalElements}
						page={page}
						onPageChange={(_, newPage) => setPage(newPage)}
						rowsPerPage={PAGE_SIZE}
						rowsPerPageOptions={[PAGE_SIZE]}
					/>
				</TableContainer>
			)}

			<TransferRequestsInboxPanel />
			<ApplicantsPanel />
		</Stack>
	);
}
