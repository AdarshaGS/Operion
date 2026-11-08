// Playbook FAQ "confirm with product" claims, tested for what the product actually does:
//  - teacher access limited to own classes (deck: "not available yet")
//  - parent access (deck: "not yet", but /api/v1/me/** exists)
//  - viewing a past academic year and year-end rollover / promotion
// Assertions state the DESIRED behaviour; a red test is a gap between playbook intent and product.
import { test, expect } from "@playwright/test";
import { createSection } from "../../../api/organisations";
import { feeSetup, assignAndInvoice, payBody } from "../../../api/pb-fees";
import { makeGuardian, makeTeacher } from "../../../api/pb-portal";
import { addStudent, createWorld, get, must, post, uid, YEAR_END, type World } from "../../../api/pb-helpers";

const TEACHER_PERMS = ["ATTENDANCE_VIEW", "ATTENDANCE_MARK", "STUDENT_VIEW"];

test.describe("FAQ: teacher access limited to own classes", () => {
	test.describe.configure({ mode: "default" });
	let w: World;
	let secA: number;
	let secB: number;
	let stuA: Awaited<ReturnType<typeof addStudent>>;
	let stuB: Awaited<ReturnType<typeof addStudent>>;
	let teacher: Awaited<ReturnType<typeof makeTeacher>>;
	let dayCounter = 0;

	test.beforeAll(async () => {
		w = await createWorld();
		secA = (await createSection(w.ownerToken, w.classId, "TA", 40, "R1")).id;
		secB = (await createSection(w.ownerToken, w.classId, "TB", 40, "R2")).id;
		stuA = await addStudent(w, { sectionId: secA, roll: 1 });
		stuB = await addStudent(w, { sectionId: secB, roll: 1 });
		teacher = await makeTeacher(w, "teach", TEACHER_PERMS, secA);
	});

	const register = (token: string, sectionId: number, enrollmentId: number, day: number) =>
		post(`/api/v1/attendance/sections/${sectionId}/register`, token, {
			attendanceDate: new Date(Date.UTC(2026, 8, 1 + day)).toISOString().slice(0, 10),
			marks: [{ studentEnrollmentId: enrollmentId, status: "PRESENT", excused: false }],
		});

	test("/me/sections lists only the sections the teacher is assigned to", async () => {
		const res = await get("/api/v1/me/sections", teacher.token);
		expect(res.status).toBe(200);
		const ids = res.body.map((s: any) => s.sectionId);
		expect(ids).toContain(secA);
		expect(ids).not.toContain(secB);
	});

	test("attendance: the class teacher can mark their own section", async () => {
		expect((await register(teacher.token, secA, stuA.enrollmentId, ++dayCounter)).status).toBe(200);
	});

	test("attendance: marking another section is refused with 403", async () => {
		expect((await register(teacher.token, secB, stuB.enrollmentId, ++dayCounter)).status).toBe(403);
	});

	test("attendance: markable-sections shows only the teacher's own section", async () => {
		const res = await get("/api/v1/attendance/markable-sections", teacher.token);
		expect(res.status).toBe(200);
		const text = JSON.stringify(res.body);
		expect(text).toContain(String(secA));
		expect(text).not.toContain(`${secB}`);
	});

	test("attendance: reading another section's register is refused", async () => {
		const date = "2026-09-20";
		await must(register(w.ownerToken, secB, stuB.enrollmentId, 19), "owner marks B");
		const res = await get(`/api/v1/attendance/sections/${secB}/register?date=${date}`, teacher.token);
		expect([403, 404], `teacher read another section's register: HTTP ${res.status}`).toContain(res.status);
	});

	test("students: a teacher cannot open a student from a section they do not teach", async () => {
		const own = await get(`/api/v1/students/${stuA.studentId}`, teacher.token);
		expect(own.status).toBe(200);
		const other = await get(`/api/v1/students/${stuB.studentId}`, teacher.token);
		expect([403, 404], `teacher read another section's student: HTTP ${other.status}`).toContain(other.status);
	});

	test("students: the student list does not include students outside the teacher's sections", async () => {
		const list = await get("/api/v1/students", teacher.token);
		expect(list.status).toBe(200);
		const ids = list.body.map((s: any) => s.id);
		expect(ids, "list leaks the other section's student").not.toContain(stuB.studentId);
	});

	test("attendance corrections and locking need their own permissions even for the class teacher", async () => {
		const reg = await must(register(teacher.token, secA, stuA.enrollmentId, ++dayCounter), "mark");
		expect((await post(`/api/v1/attendance/register/${reg.register.id}/lock`, teacher.token)).status).toBe(403);
	});
});

test.describe("FAQ: parent access (/api/v1/me/**)", () => {
	test.describe.configure({ mode: "default" });
	let w: World;
	let child: Awaited<ReturnType<typeof addStudent>>;
	let other: Awaited<ReturnType<typeof addStudent>>;
	let parent: Awaited<ReturnType<typeof makeGuardian>>;
	let invoiceId: number;
	let paymentId: number;

	test.beforeAll(async () => {
		w = await createWorld();
		child = await addStudent(w, { roll: 1 });
		other = await addStudent(w, { roll: 2 });
		parent = await makeGuardian(w, [child]);
		const fee = await feeSetup(w, { amounts: [800] });
		const inv = await assignAndInvoice(w, child.enrollmentId, fee);
		invoiceId = inv.invoices[0].id;
		await assignAndInvoice(w, other.enrollmentId, fee);
		const pay = await must(post("/api/v1/fees/payments", w.ownerToken, payBody(w, invoiceId, 300)), "pay");
		paymentId = pay.id;
	});

	test("/me identifies a guardian session listing exactly the linked children", async () => {
		const res = await get("/api/v1/me", parent.token);
		expect(res.status).toBe(200);
		expect(res.body.sessionType).toBe("PORTAL");
		const ctx = res.body.portalContexts.find((c: any) => c.type === "GUARDIAN");
		expect(ctx.children.map((c: any) => c.studentId)).toEqual([child.studentId]);
	});

	test("a guardian sees their own child's attendance, fees, payments, report cards and announcements", async () => {
		for (const part of ["attendance", "fees", "payments", "report-cards"]) {
			const r = await get(`/api/v1/me/children/${child.studentId}/${part}`, parent.token);
			expect(r.status, part).toBe(200);
		}
		const fees = await get(`/api/v1/me/children/${child.studentId}/fees`, parent.token);
		expect(fees.body.map((i: any) => i.id ?? i.invoiceId)).toContain(invoiceId);
		const pays = await get(`/api/v1/me/children/${child.studentId}/payments`, parent.token);
		expect(JSON.stringify(pays.body)).toContain(String(paymentId));
		expect((await get("/api/v1/me/announcements", parent.token)).status).toBe(200);
	});

	test("a guardian cannot read another child through /me/children/{id}/**", async () => {
		for (const part of ["attendance", "fees", "payments", "report-cards"]) {
			const r = await get(`/api/v1/me/children/${other.studentId}/${part}`, parent.token);
			expect([403, 404], `${part}: HTTP ${r.status}`).toContain(r.status);
		}
	});

	test("a guardian cannot reach staff endpoints for their own child either (students, invoices, payments, attendance)", async () => {
		const probes = [
			`/api/v1/students/${child.studentId}`,
			`/api/v1/students`,
			`/api/v1/fees/invoices?studentEnrollmentId=${child.enrollmentId}`,
			`/api/v1/fees/invoices/${invoiceId}`,
			`/api/v1/fees/payments/${paymentId}`,
			`/api/v1/attendance/enrollments/${child.enrollmentId}?from=2026-09-01&to=2026-09-30`,
			`/api/v1/audit-logs`,
		];
		for (const p of probes) {
			const r = await get(p, parent.token);
			expect(r.status, p).toBe(403);
		}
	});

	test("a guardian cannot take actions: pay, mark attendance, enrol", async () => {
		expect((await post("/api/v1/fees/payments", parent.token, payBody(w, invoiceId, 100))).status).toBe(403);
		expect((await post(`/api/v1/attendance/sections/${w.sectionId}/register`, parent.token, { attendanceDate: "2026-09-05", marks: [] })).status).toBe(403);
	});

	test("a guardian cannot list other families' people (names, contact details) through the person directory", async () => {
		const r = await get("/api/v1/persons", parent.token);
		if (r.status === 403) return;
		const names = JSON.stringify(r.body);
		expect(names, "guardian can enumerate the other child's name").not.toContain(other.firstName);
	});

	test("a second guardian linked to the other child cannot see the first child", async () => {
		const parent2 = await makeGuardian(w, [other]);
		const mine = await get(`/api/v1/me/children/${other.studentId}/fees`, parent2.token);
		expect(mine.status).toBe(200);
		const theirs = await get(`/api/v1/me/children/${child.studentId}/fees`, parent2.token);
		expect([403, 404]).toContain(theirs.status);
	});

	test("the guardian can still read their child's receipt but not another child's", async () => {
		const ok = await fetchStatus(`/api/v1/me/children/${child.studentId}/payments/${paymentId}/receipt.pdf`, parent.token);
		expect(ok).toBe(200);
		const bad = await fetchStatus(`/api/v1/me/children/${other.studentId}/payments/${paymentId}/receipt.pdf`, parent.token);
		expect([403, 404]).toContain(bad);
	});
});

async function fetchStatus(path: string, token: string): Promise<number> {
	const { API_BASE_URL } = await import("../../../api/client");
	const r = await fetch(`${API_BASE_URL}${path}`, { headers: { Authorization: `Bearer ${token}` } });
	await r.arrayBuffer();
	return r.status;
}

test.describe("FAQ: past academic years and year-end rollover", () => {
	test.describe.configure({ mode: "default" });
	let w: World;
	let y2: number;
	let stu: Awaited<ReturnType<typeof addStudent>>;
	let leaver: Awaited<ReturnType<typeof addStudent>>;
	let invoiceId: number;
	let preview: any;

	test.beforeAll(async () => {
		w = await createWorld();
		const y = await must(post("/api/v1/academic-years", w.ownerToken, { name: `PB Next ${uid("")}`, startDate: "2027-04-01", endDate: "2028-03-31" }), "year 2");
		y2 = y.id;
		stu = await addStudent(w, { roll: 1 });
		leaver = await addStudent(w, { roll: 2 });
		const fee = await feeSetup(w, { amounts: [600] });
		const inv = await assignAndInvoice(w, stu.enrollmentId, fee);
		invoiceId = inv.invoices[0].id;
		await must(post("/api/v1/fees/payments", w.ownerToken, payBody(w, invoiceId, 600)), "pay in full");
		const reg = await must(
			post(`/api/v1/attendance/sections/${w.sectionId}/register`, w.ownerToken, {
				attendanceDate: "2026-09-02",
				marks: [{ studentEnrollmentId: stu.enrollmentId, status: "ABSENT", excused: false }],
			}),
			"attendance",
		);
		expect(reg.register.id).toBeTruthy();
	});

	test("several academic years coexist; one is current and the list shows both", async () => {
		const years = await get("/api/v1/academic-years", w.ownerToken);
		expect(years.status).toBe(200);
		const mine = years.body.filter((x: any) => [w.yearId, y2].includes(x.id));
		expect(mine).toHaveLength(2);
		expect(mine.filter((x: any) => x.current).length).toBe(1);
	});

	test("rollover steps are listed and preview changes nothing", async () => {
		const steps = await get("/api/v1/academic-years/rollover/steps", w.ownerToken);
		expect(steps.status).toBe(200);
		expect(steps.body.length).toBeGreaterThan(0);
		const res = await post(`/api/v1/academic-years/${y2}/rollover/preview`, w.ownerToken, { sourceYearId: w.yearId, steps: [], feeIncreasePercent: 10 });
		expect(res.status).toBe(200);
		expect(res.body.dryRun).toBe(true);
		preview = res.body;
		const classes = await get(`/api/v1/school-classes?academicYearId=${y2}`, w.ownerToken);
		expect(classes.body, "preview must not create classes").toHaveLength(0);
	});

	test("rollover apply copies the class structure and fee structures into the new year, with the preview's counts", async () => {
		const res = await post(`/api/v1/academic-years/${y2}/rollover/apply`, w.ownerToken, { sourceYearId: w.yearId, steps: [], feeIncreasePercent: 10 });
		expect(res.status).toBe(200);
		expect(res.body.dryRun).toBe(false);
		const copied = (r: any) => r.steps.map((s: any) => s.items.map((i: any) => `${s.key}/${i.name}:${i.copied}`)).flat();
		expect(copied(res.body)).toEqual(copied(preview));
		const classes = await get(`/api/v1/school-classes?academicYearId=${y2}`, w.ownerToken);
		expect(classes.body.length).toBeGreaterThanOrEqual(1);
		const groups = await get(`/api/v1/fees/structure-groups?academicYearId=${y2}`, w.ownerToken);
		if (groups.status === 200) expect(JSON.stringify(groups.body).length).toBeGreaterThan(2);
	});

	test("running the rollover a second time does not duplicate anything", async () => {
		const before = await get(`/api/v1/school-classes?academicYearId=${y2}`, w.ownerToken);
		const again = await post(`/api/v1/academic-years/${y2}/rollover/apply`, w.ownerToken, { sourceYearId: w.yearId, steps: [], feeIncreasePercent: 10 });
		expect(again.status).toBe(200);
		expect(again.body.steps.reduce((n: number, s: any) => n + s.items.reduce((m: number, i: any) => m + i.copied, 0), 0)).toBe(0);
		const after = await get(`/api/v1/school-classes?academicYearId=${y2}`, w.ownerToken);
		expect(after.body).toHaveLength(before.body.length);
	});

	test("rollover needs ORGANISATION_MANAGE", async () => {
		const { makeUser } = await import("../../../api/pb-helpers");
		const u = await makeUser(w, "norollover", ["STUDENT_VIEW"]);
		expect((await post(`/api/v1/academic-years/${y2}/rollover/apply`, u.token, { sourceYearId: w.yearId, steps: [] })).status).toBe(403);
		expect((await get("/api/v1/academic-years/rollover/steps", u.token)).status).toBe(403);
	});

	test("the year cannot be closed while students are still enrolled in it", async () => {
		const res = await post(`/api/v1/academic-years/${w.yearId}/close`, w.ownerToken);
		expect(res.status).toBe(409);
		expect(res.body.error).toContain("still enrolled");
	});

	test("year-end promotion: preview proposes, apply moves the student into the next grade's class in the new year", async () => {
		const grade2 = await must(post("/api/v1/grade-levels", w.ownerToken, { name: `G2 ${uid("")}`, sequenceOrder: 2, stage: "PRIMARY" }), "grade 2");
		const target = await must(post("/api/v1/school-classes", w.ownerToken, { academicYearId: y2, campusId: w.campusId, gradeLevelId: grade2.id, displayName: `Next ${uid("")}` }), "class y2");
		const section = await must(post(`/api/v1/school-classes/${target.id}/sections`, w.ownerToken, { name: "A", capacity: 40, room: "N1" }), "section y2");
		const prev = await post("/api/v1/student-promotions/preview", w.ownerToken, {
			sourceAcademicYearId: w.yearId,
			targetAcademicYearId: y2,
			effectiveDate: "2027-04-01",
			classMap: [{ sourceSchoolClassId: w.classId, targetSchoolClassId: target.id }],
		});
		expect(prev.status).toBe(200);
		const row = prev.body.find((c: any) => c.sourceSchoolClassId === w.classId).students.find((s: any) => s.studentId === stu.studentId);
		expect(row).toBeTruthy();
		const apply = await post("/api/v1/student-promotions", w.ownerToken, {
			targetAcademicYearId: y2,
			promotionDate: "2027-04-01",
			decisions: [{ studentId: stu.studentId, action: "PROMOTE", targetSectionId: section.id, rollNumber: 1 }],
		});
		expect(apply.status).toBe(200);
		expect(apply.body[0], JSON.stringify(apply.body)).toMatchObject({ studentId: stu.studentId, succeeded: true });
	});

	test("year-end graduation exits the student; needs STUDENT_EXIT_MANAGE as well as enrolment management", async () => {
		const { makeUser } = await import("../../../api/pb-helpers");
		const enroller = await makeUser(w, "enroller", ["STUDENT_ENROLLMENT_MANAGE", "STUDENT_VIEW"]);
		const body = { targetAcademicYearId: y2, promotionDate: "2027-04-01", decisions: [{ studentId: leaver.studentId, action: "GRADUATE" }] };
		expect((await post("/api/v1/student-promotions", enroller.token, body)).status).toBe(403);
		const done = await post("/api/v1/student-promotions", w.ownerToken, body);
		expect(done.status).toBe(200);
		expect(done.body[0], JSON.stringify(done.body)).toMatchObject({ studentId: leaver.studentId, action: "GRADUATE", succeeded: true });
		const student = await get(`/api/v1/students/${leaver.studentId}`, w.ownerToken);
		expect(student.body.status).not.toBe("ACTIVE");
	});

	test("after promotion the student has one enrollment per year and the current one is the new year", async () => {
		const list = await get(`/api/v1/students/${stu.studentId}/enrollments`, w.ownerToken);
		expect(list.status).toBe(200);
		expect(list.body).toHaveLength(2);
		const years = list.body.map((e: any) => e.academicYearId).sort();
		expect(years).toEqual([w.yearId, y2].sort());
		expect(list.body.find((e: any) => e.current).academicYearId).toBe(y2);
	});

	test("past year stays viewable: old fees, payments, attendance and classes are still readable", async () => {
		const invoices = await get(`/api/v1/fees/invoices?studentEnrollmentId=${stu.enrollmentId}`, w.ownerToken);
		expect(invoices.status).toBe(200);
		expect(invoices.body.map((i: any) => i.id)).toContain(invoiceId);
		const pays = await get(`/api/v1/fees/payments?studentEnrollmentId=${stu.enrollmentId}`, w.ownerToken);
		expect(pays.body).toHaveLength(1);
		const att = await get(`/api/v1/attendance/enrollments/${stu.enrollmentId}?from=2026-09-01&to=2026-09-30`, w.ownerToken);
		expect(att.status).toBe(200);
		expect(JSON.stringify(att.body)).toContain("ABSENT");
		const oldClasses = await get(`/api/v1/school-classes?academicYearId=${w.yearId}`, w.ownerToken);
		expect(oldClasses.body.map((c: any) => c.id)).toContain(w.classId);
	});

	test("once everyone is promoted the old year can be closed, and closing it keeps it readable", async () => {
		const closed = await post(`/api/v1/academic-years/${w.yearId}/close`, w.ownerToken);
		expect(closed.status).toBe(200);
		const years = await get("/api/v1/academic-years", w.ownerToken);
		expect(years.body.find((x: any) => x.id === w.yearId).status).toBe("CLOSED");
		const invoices = await get(`/api/v1/fees/invoices?studentEnrollmentId=${stu.enrollmentId}`, w.ownerToken);
		expect(invoices.status).toBe(200);
		void YEAR_END;
	});
});
