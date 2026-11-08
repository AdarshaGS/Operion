// Playbook: bulk imports - students, guardians, classes/sections, subjects and student enrolments
// (fee structures + opening balances are in 03-fees-story, library books in 05-library).
import { test, expect } from "@playwright/test";
import { createWorld, csv, get, makeUser, upload, uid, type World } from "../../../api/pb-helpers";

let w: World;
const phone = () => `9${Math.floor(Math.random() * 1e9).toString().padStart(9, "0")}`;

test.describe.configure({ mode: "default" });

test.beforeAll(async () => {
	w = await createWorld();
});

const adm = (n: number) => `IMP-${w.slug}-${n}`.toUpperCase();
const STUDENT_HEADERS = ["firstName", "lastName", "dateOfBirth", "gender", "admissionNumber", "admissionDate"];

async function exportedAdmissions(): Promise<string[]> {
	const res = await get("/api/v1/students/export", w.ownerToken);
	expect(res.status).toBe(200);
	return res.body.map((s: any) => s.admissionNumber);
}

test.describe("Student import", () => {
	test("a clean file admits every student and they appear in the export", async () => {
		const body = csv(STUDENT_HEADERS, [
			["Asha", "Imp", "2015-04-10", "FEMALE", adm(1), "2026-06-01"],
			["Ravi", "Imp", "2014-02-11", "MALE", adm(2), "2026-06-01"],
			["Meera", "Imp", "2013-09-30", "FEMALE", adm(3), "2026-06-01"],
		]);
		const res = await upload("/api/v1/students/import", w.ownerToken, body);
		expect(res.status).toBe(200);
		expect(res.body).toHaveLength(3);
		expect(res.body.every((r: any) => r.success && r.studentId)).toBe(true);
		expect(await exportedAdmissions()).toEqual(expect.arrayContaining([adm(1), adm(2), adm(3)]));
	});

	test("a bad row fails alone: duplicate admission number and missing gender are reported per row, the good row still lands", async () => {
		const body = csv(STUDENT_HEADERS, [
			["Dup", "Imp", "2015-04-10", "FEMALE", adm(1), "2026-06-01"],
			["NoGender", "Imp", "2015-04-10", "", adm(4), "2026-06-01"],
			["Good", "Imp", "2015-04-10", "MALE", adm(5), "2026-06-01"],
		]);
		const res = await upload("/api/v1/students/import", w.ownerToken, body);
		expect(res.status).toBe(200);
		expect(res.body.map((r: any) => r.success)).toEqual([false, false, true]);
		expect(res.body[0].message).toBeTruthy();
		expect(res.body[1].message).toMatch(/gender/i);
		const adms = await exportedAdmissions();
		expect(adms).toContain(adm(5));
		expect(adms).not.toContain(adm(4));
	});

	test("a misspelt or missing column is rejected up front and nothing is created", async () => {
		const before = (await exportedAdmissions()).length;
		const res = await upload("/api/v1/students/import", w.ownerToken, csv(["firstName", "admissionDate", "gendr"], [["X", "2026-06-01", "MALE"]]));
		expect(res.status).toBe(200);
		expect(res.body).toHaveLength(1);
		expect(res.body[0]).toMatchObject({ row: 1, success: false });
		const missing = await upload("/api/v1/students/import", w.ownerToken, csv(["lastName", "gender"], [["X", "MALE"]]));
		expect(missing.body[0].success).toBe(false);
		expect((await exportedAdmissions()).length).toBe(before);
	});

	test("an auto-generated admission number is issued when the column is left blank", async () => {
		const res = await upload("/api/v1/students/import", w.ownerToken, csv(["firstName", "gender", "admissionDate"], [[`Auto${uid("")}`, "MALE", "2026-06-01"]]));
		expect(res.body[0].success).toBe(true);
	});

	test("a non-CSV/XLSX file and an empty upload are refused with 400", async () => {
		expect((await upload("/api/v1/students/import", w.ownerToken, "x", "students.txt")).status).toBe(400);
		expect((await upload("/api/v1/students/import", w.ownerToken, "", "students.csv")).status).toBeGreaterThanOrEqual(400);
	});

	test("importing needs STUDENT_MANAGE", async () => {
		const u = await makeUser(w, "impview", ["STUDENT_VIEW"]);
		expect((await upload("/api/v1/students/import", u.token, csv(STUDENT_HEADERS, [["N", "I", "2015-04-10", "MALE", adm(90), "2026-06-01"]]))).status).toBe(403);
	});
});

test.describe("Guardian import", () => {
	test("validate-only reports VALID and writes nothing; the real import links the guardian to the student; a re-run is a DUPLICATE", async () => {
		const head = ["studentAdmissionNumber", "firstName", "lastName", "phone", "relationshipType", "isPrimary"];
		const ph = phone();
		const body = csv(head, [[adm(1), "Priya", "Imp", ph, "MOTHER", "true"]]);
		const dry = await upload("/api/v1/guardians/import?validateOnly=true", w.ownerToken, body);
		expect(dry.status).toBe(200);
		expect(dry.body[0].status).toBe("VALID");
		expect((await get("/api/v1/guardians/export", w.ownerToken)).body.filter((g: any) => g.studentAdmissionNumber === adm(1))).toHaveLength(0);
		const real = await upload("/api/v1/guardians/import", w.ownerToken, body);
		expect(real.body[0].status).toBe("IMPORTED");
		const links = (await get("/api/v1/guardians/export", w.ownerToken)).body.filter((g: any) => g.studentAdmissionNumber === adm(1));
		expect(links).toHaveLength(1);
		expect(links[0]).toMatchObject({ firstName: "Priya", relationshipType: "MOTHER" });
		const again = await upload("/api/v1/guardians/import", w.ownerToken, body);
		expect(again.body[0].status).toBe("DUPLICATE");
		expect((await get("/api/v1/guardians/export", w.ownerToken)).body.filter((g: any) => g.studentAdmissionNumber === adm(1))).toHaveLength(1);
	});

	test("an unknown student, an invalid relationship and a missing name are per-row errors; the valid row still imports", async () => {
		const head = ["studentAdmissionNumber", "firstName", "lastName", "phone", "relationshipType"];
		const body = csv(head, [
			["NO-SUCH-STUDENT", "A", "B", phone(), "FATHER"],
			[adm(2), "Bad", "Rel", phone(), "NEIGHBOUR"],
			[adm(2), "", "NoName", phone(), "FATHER"],
			[adm(2), "Raj", "Imp", phone(), "FATHER"],
		]);
		const res = await upload("/api/v1/guardians/import", w.ownerToken, body);
		expect(res.status).toBe(200);
		expect(res.body.map((r: any) => r.status)).toEqual(["ERROR", "ERROR", "ERROR", "IMPORTED"]);
	});

	test("importing guardians needs GUARDIAN_MANAGE", async () => {
		const u = await makeUser(w, "gview", ["GUARDIAN_VIEW"]);
		const body = csv(["studentAdmissionNumber", "firstName", "relationshipType"], [[adm(3), "X", "FATHER"]]);
		expect((await upload("/api/v1/guardians/import", u.token, body)).status).toBe(403);
	});
});

test.describe("Class and section import", () => {
	const head = ["academicYear", "campus", "gradeLevel", "gradeSequenceOrder", "stage", "className", "sectionName", "sectionCapacity", "sectionRoom"];
	let className: string;
	let gradeName: string;
	let campusName: string;

	test.beforeAll(async () => {
		const campuses = await get("/api/v1/campuses", w.ownerToken);
		campusName = campuses.body.find((c: any) => c.id === w.campusId).name;
		className = `ImpClass ${uid("")}`;
		gradeName = `ImpGrade ${uid("")}`;
	});

	test("validate-only reports VALID without creating; the real import creates grade, class and sections", async () => {
		const body = csv(head, [
			[w.yearName, campusName, gradeName, 70, "PRIMARY", className, "A", 40, "R1"],
			[w.yearName, campusName, gradeName, 70, "PRIMARY", className, "B", 35, "R2"],
		]);
		const dry = await upload("/api/v1/class-sections/import?validateOnly=true", w.ownerToken, body);
		expect(dry.body.map((r: any) => r.status)).toEqual(["VALID", "VALID"]);
		expect((await get("/api/v1/grade-levels", w.ownerToken)).body.map((g: any) => g.name)).not.toContain(gradeName);
		const real = await upload("/api/v1/class-sections/import", w.ownerToken, body);
		expect(real.body.map((r: any) => r.status)).toEqual(["IMPORTED", "IMPORTED"]);
		const classes = (await get(`/api/v1/school-classes?academicYearId=${w.yearId}`, w.ownerToken)).body;
		const klass = (classes.content ?? classes).find((c: any) => c.displayName === className);
		expect(klass).toBeTruthy();
		const sections = (await get(`/api/v1/school-classes/${klass.id}/sections`, w.ownerToken)).body;
		expect(sections.map((s: any) => s.name).sort()).toEqual(["A", "B"]);
		expect(sections.find((s: any) => s.name === "B").capacity).toBe(35);
	});

	test("a repeated section is a DUPLICATE, an unknown year or campus an ERROR, and a new grade without a sequence an ERROR", async () => {
		const body = csv(head, [
			[w.yearName, campusName, gradeName, 70, "PRIMARY", className, "A", 40, "R1"],
			["No Such Year", campusName, gradeName, 70, "PRIMARY", className, "C", 40, "R1"],
			[w.yearName, "No Such Campus", gradeName, 70, "PRIMARY", className, "C", 40, "R1"],
			[w.yearName, campusName, `Fresh ${uid("")}`, "", "PRIMARY", `FreshC ${uid("")}`, "A", 40, "R1"],
		]);
		const res = await upload("/api/v1/class-sections/import", w.ownerToken, body);
		expect(res.body.map((r: any) => r.status)).toEqual(["DUPLICATE", "ERROR", "ERROR", "ERROR"]);
	});

	test("importing classes needs CLASS_MANAGE", async () => {
		const u = await makeUser(w, "cview", ["CLASS_VIEW"]);
		expect((await upload("/api/v1/class-sections/import", u.token, csv(head, [[w.yearName, campusName, gradeName, 70, "PRIMARY", className, "Z", 10, "R"]]))).status).toBe(403);
	});
});

test.describe("Subject import", () => {
	test("validate-only, real import, duplicate code and missing name", async () => {
		const code = uid("sj").toUpperCase();
		const body = csv(["name", "code"], [[`Imp Subject ${code}`, code]]);
		const dry = await upload("/api/v1/subjects/import?validateOnly=true", w.ownerToken, body);
		expect(dry.body[0].status).toBe("VALID");
		expect((await get("/api/v1/subjects", w.ownerToken)).body.map((s: any) => s.code)).not.toContain(code);
		const real = await upload("/api/v1/subjects/import", w.ownerToken, body);
		expect(real.body[0].status).toBe("IMPORTED");
		expect((await get("/api/v1/subjects", w.ownerToken)).body.map((s: any) => s.code)).toContain(code);
		const again = await upload("/api/v1/subjects/import", w.ownerToken, body);
		expect(again.body[0].status).toBe("DUPLICATE");
		const noName = await upload("/api/v1/subjects/import", w.ownerToken, csv(["name", "code"], [["", uid("z").toUpperCase()]]));
		expect(noName.body[0].status).toBe("ERROR");
	});

	test("importing subjects needs SUBJECT_MANAGE", async () => {
		const u = await makeUser(w, "sview", ["SUBJECT_VIEW"]);
		expect((await upload("/api/v1/subjects/import", u.token, csv(["name", "code"], [["X", uid("q").toUpperCase()]]))).status).toBe(403);
	});
});

test.describe("Student enrolment import (admitted students placed into sections)", () => {
	test("imported students are enrolled into a section; a second run is a DUPLICATE; unknown student and section are errors", async () => {
		const classes = (await get(`/api/v1/school-classes?academicYearId=${w.yearId}`, w.ownerToken)).body;
		const klass = (classes.content ?? classes).find((c: any) => c.id === w.classId);
		const head = ["studentAdmissionNumber", "academicYear", "className", "sectionName", "rollNumber", "enrolledDate"];
		const body = csv(head, [
			[adm(1), w.yearName, klass.displayName, "A", 11, "2026-06-01"],
			["NO-SUCH", w.yearName, klass.displayName, "A", 12, "2026-06-01"],
			[adm(2), w.yearName, klass.displayName, "Q", 13, "2026-06-01"],
		]);
		const dry = await upload("/api/v1/student-enrollments/import?validateOnly=true", w.ownerToken, body);
		expect(dry.body.map((r: any) => r.status)).toEqual(["VALID", "ERROR", "ERROR"]);
		const real = await upload("/api/v1/student-enrollments/import", w.ownerToken, body);
		expect(real.body.map((r: any) => r.status)).toEqual(["IMPORTED", "ERROR", "ERROR"]);
		const exp = await get("/api/v1/student-enrollments/export", w.ownerToken);
		expect(exp.status).toBe(200);
		expect(JSON.stringify(exp.body)).toContain(adm(1));
		const again = await upload("/api/v1/student-enrollments/import", w.ownerToken, csv(head, [[adm(1), w.yearName, klass.displayName, "A", 11, "2026-06-01"]]));
		expect(again.body[0].status).toBe("DUPLICATE");
	});
});

test.describe("Import history", () => {
	test("each completed import is listed with its entity type and counts", async () => {
		const res = await get("/api/v1/imports/history", w.ownerToken);
		expect(res.status).toBe(200);
		const types = res.body.map((r: any) => r.entityType);
		expect(types).toEqual(expect.arrayContaining(["Guardian", "ClassSection", "Subject"]));
		const sub = res.body.find((r: any) => r.entityType === "Subject");
		expect(sub.totalRows).toBeGreaterThanOrEqual(1);
	});
});
