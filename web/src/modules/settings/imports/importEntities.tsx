import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import BadgeIcon from "@mui/icons-material/Badge";
import ClassIcon from "@mui/icons-material/Class";
import DirectionsBusIcon from "@mui/icons-material/DirectionsBus";
import FamilyRestroomIcon from "@mui/icons-material/FamilyRestroom";
import HowToRegIcon from "@mui/icons-material/HowToReg";
import Inventory2Icon from "@mui/icons-material/Inventory2";
import LocalLibraryIcon from "@mui/icons-material/LocalLibrary";
import MenuBookIcon from "@mui/icons-material/MenuBook";
import PaidIcon from "@mui/icons-material/Paid";
import SchoolIcon from "@mui/icons-material/School";
import { api } from "../../../api/client";
import { postImportFile, type ImportRowResult, type ImportRowStatus } from "../../../api/imports";
import { exportStudents, importStudents, STUDENT_IMPORT_TEMPLATE_HEADERS } from "../../../api/students";
import { downloadCsvFile, toCsv } from "../../../utils/csv";
import type { ImportEntityConfig } from "./ImportConfig";

/** Students predates validateOnly/ImportRowResult (see StudentImportRowResult) - adapted
 * here at the frontend boundary so it can still use the shared dialog, per the "leave
 * StudentImportService's backend untouched" decision. */
async function importStudentsAdapted(file: File): Promise<ImportRowResult[]> {
	const rows = await importStudents(file);
	return rows.map((r) => ({
		row: r.row,
		status: (r.success ? "IMPORTED" : "ERROR") as ImportRowStatus,
		message: r.message,
		id: r.studentId,
	}));
}

async function exportStudentsAsCsv() {
	const rows = await exportStudents();
	downloadCsvFile(
		"students.csv",
		toCsv(["id", "firstName", "lastName", "email", "phone", "admissionNumber", "admissionDate", "bloodGroup", "category", "status"], rows as unknown as Record<string, unknown>[]),
	);
}

function csvExporter<T extends Record<string, unknown>>(url: string, fileName: string, columns: string[]) {
	return async () => {
		const rows = await api.get<T[]>(url);
		downloadCsvFile(fileName, toCsv(columns, rows as unknown as Record<string, unknown>[]));
	};
}

export const IMPORT_ENTITIES: ImportEntityConfig[] = [
	{
		key: "staff",
		label: "Staff",
		description: "Import staff/employee details from Excel.",
		group: "people",
		icon: <BadgeIcon color="primary" />,
		templateHeaders: [
			"firstName", "lastName", "dateOfBirth", "gender", "email", "phone", "employeeCode", "dateOfJoining",
			"employmentType", "department", "designation", "campus",
		],
		templateExampleRow: [
			"Ravi", "Menon", "1985-01-01", "MALE", "ravi@example.com", "9876500000", "EMP-100", "2020-06-01",
			"PERMANENT", "Science", "Teacher", "Main Campus",
		],
		templateFileName: "staff-import-template.csv",
		supportsPreview: true,
		runImport: (file, validateOnly) => postImportFile("/api/v1/hr/staff/import", file, validateOnly),
		exportData: csvExporter("/api/v1/hr/staff/export", "staff.csv",
			["id", "employeeCode", "firstName", "lastName", "email", "phone", "department", "designation", "employmentType", "dateOfJoining", "status"]),
	},
	{
		key: "students",
		label: "Students",
		description: "Import student details from Excel.",
		group: "people",
		icon: <SchoolIcon color="primary" />,
		templateHeaders: STUDENT_IMPORT_TEMPLATE_HEADERS,
		templateExampleRow: [
			"Asha", "Rao", "2012-04-18", "FEMALE", "asha.rao@example.com", "9876500000", "ADM-2026-001", "2026-06-01",
			"WALK_IN", "", "", "", "O+", "General", "Indian", "",
		],
		templateFileName: "students-import-template.csv",
		supportsPreview: false,
		runImport: (file) => importStudentsAdapted(file),
		exportData: exportStudentsAsCsv,
	},
	{
		key: "guardians",
		label: "Parents / Guardians",
		description: "Import parent or guardian details.",
		group: "people",
		icon: <FamilyRestroomIcon color="primary" />,
		templateHeaders: [
			"studentAdmissionNumber", "firstName", "lastName", "email", "phone", "occupation", "relationshipType",
			"isPrimary", "isEmergencyContact", "canPickup", "canReceiveCommunication", "contactPriority",
		],
		templateExampleRow: ["ADM-2026-001", "Sunita", "Rao", "sunita.rao@example.com", "9876511111", "Engineer", "MOTHER", "true", "true", "true", "true", "1"],
		templateFileName: "guardians-import-template.csv",
		supportsPreview: true,
		runImport: (file, validateOnly) => postImportFile("/api/v1/guardians/import", file, validateOnly),
		exportData: csvExporter("/api/v1/guardians/export", "guardians.csv",
			["guardianId", "firstName", "lastName", "email", "phone", "occupation", "studentAdmissionNumber", "relationshipType"]),
	},
	{
		key: "class-sections",
		label: "Classes & sections",
		description: "Import classes and sections.",
		group: "academics",
		icon: <ClassIcon color="primary" />,
		templateHeaders: [
			"academicYear", "campus", "gradeLevel", "gradeSequenceOrder", "stage", "className", "sectionName",
			"sectionCapacity", "sectionRoom",
		],
		templateExampleRow: ["2026-2027", "Main Campus", "Grade 5", "5", "", "Grade 5", "A", "40", ""],
		templateFileName: "class-sections-import-template.csv",
		supportsPreview: true,
		runImport: (file, validateOnly) => postImportFile("/api/v1/class-sections/import", file, validateOnly),
		exportData: csvExporter("/api/v1/class-sections/export", "class-sections.csv",
			["sectionId", "academicYear", "campus", "gradeLevel", "className", "sectionName", "capacity", "room"]),
	},
	{
		key: "subjects",
		label: "Subjects",
		description: "Import subject list.",
		group: "academics",
		icon: <MenuBookIcon color="primary" />,
		templateHeaders: ["name", "code"],
		templateExampleRow: ["Mathematics", "MATH"],
		templateFileName: "subjects-import-template.csv",
		supportsPreview: true,
		runImport: (file, validateOnly) => postImportFile("/api/v1/subjects/import", file, validateOnly),
		exportData: csvExporter("/api/v1/subjects/export", "subjects.csv", ["id", "name", "code", "status"]),
	},
	{
		key: "student-enrolments",
		label: "Student enrolments",
		description: "Import student class assignments.",
		group: "academics",
		icon: <HowToRegIcon color="primary" />,
		templateHeaders: ["studentAdmissionNumber", "academicYear", "className", "sectionName", "rollNumber", "enrolledDate"],
		templateExampleRow: ["ADM-2026-001", "2026-2027", "Grade 5", "A", "12", "2026-06-01"],
		templateFileName: "student-enrolments-import-template.csv",
		supportsPreview: true,
		runImport: (file, validateOnly) => postImportFile("/api/v1/student-enrollments/import", file, validateOnly),
		exportData: csvExporter("/api/v1/student-enrollments/export", "student-enrolments.csv",
			["id", "admissionNumber", "academicYear", "className", "sectionName", "rollNumber", "enrolledDate", "current"]),
	},
	{
		key: "fee-structures",
		label: "Fee structures",
		description: "Import fee heads and structures.",
		group: "finance",
		icon: <AccountBalanceIcon color="primary" />,
		templateHeaders: ["academicYear", "className", "feeCategory", "amount", "installmentDueDate", "paymentFrequency"],
		templateExampleRow: ["2026-2027", "Grade 5", "Tuition Fee", "10000.00", "2026-06-15", "ONE_SHOT"],
		templateFileName: "fee-structures-import-template.csv",
		supportsPreview: true,
		runImport: (file, validateOnly) => postImportFile("/api/v1/fees/structures/import", file, validateOnly),
		exportData: csvExporter("/api/v1/fees/structures/export", "fee-structures.csv",
			["id", "academicYear", "className", "feeCategory", "amount", "paymentFrequency", "status"]),
	},
	{
		key: "opening-balances",
		label: "Opening balances",
		description: "Import student fee balances.",
		group: "finance",
		icon: <PaidIcon color="primary" />,
		templateHeaders: ["studentAdmissionNumber", "amount", "asOfDate"],
		templateExampleRow: ["ADM-2026-001", "5000.00", "2026-04-01"],
		templateFileName: "opening-balances-import-template.csv",
		supportsPreview: true,
		runImport: (file, validateOnly) => postImportFile("/api/v1/fees/opening-balances/import", file, validateOnly),
		exportData: csvExporter("/api/v1/fees/opening-balances/export", "opening-balances.csv",
			["id", "studentAdmissionNumber", "studentName", "amount", "status"]),
	},
	{
		key: "inventory-items",
		label: "Inventory items",
		description: "Import inventory items and opening stock.",
		group: "operations",
		icon: <Inventory2Icon color="primary" />,
		templateHeaders: ["category", "code", "name", "unit", "description", "reorderLevel"],
		templateExampleRow: ["Stationery", "ITM-001", "A4 Paper Ream", "ream", "", "20"],
		templateFileName: "inventory-items-import-template.csv",
		supportsPreview: true,
		runImport: (file, validateOnly) => postImportFile("/api/v1/inventory/items/import", file, validateOnly),
		exportData: csvExporter("/api/v1/inventory/items/export", "inventory-items.csv",
			["id", "categoryName", "code", "name", "unit", "description", "reorderLevel", "status"]),
	},
	{
		key: "vehicles-routes",
		label: "Vehicles & routes",
		description: "Import vehicles, routes and stops.",
		group: "operations",
		icon: <DirectionsBusIcon color="primary" />,
		templateHeaders: ["campus", "registrationNumber", "vehicleType", "capacity", "driverName", "attendantName", "routeName", "routeCode"],
		templateExampleRow: ["Main Campus", "KA-01-AB-1234", "BUS", "40", "", "", "Route 1", "RT-1"],
		templateFileName: "vehicles-routes-import-template.csv",
		supportsPreview: true,
		runImport: (file, validateOnly) => postImportFile("/api/v1/transport/vehicles/import", file, validateOnly),
		exportData: csvExporter("/api/v1/transport/vehicles/export", "vehicles.csv",
			["id", "registrationNumber", "vehicleType", "capacity", "campusName", "driverName", "attendantName", "status"]),
	},
	{
		key: "books",
		label: "Books",
		description: "Import library books from Excel.",
		group: "library",
		icon: <LocalLibraryIcon color="primary" />,
		templateHeaders: ["isbn", "title", "author", "publisher", "category", "edition"],
		templateExampleRow: ["9780130313630", "The C Programming Language", "Kernighan & Ritchie", "Prentice Hall", "Reference", "2nd"],
		templateFileName: "books-import-template.csv",
		supportsPreview: true,
		runImport: (file, validateOnly) => postImportFile("/api/v1/library/books/import", file, validateOnly),
		exportData: csvExporter("/api/v1/library/books/export", "books.csv",
			["id", "isbn", "title", "author", "publisher", "category", "edition", "status"]),
	},
];
