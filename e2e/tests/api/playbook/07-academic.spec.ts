// Playbook module: Academic - academic years, grade levels, classes and sections, subjects
// mapped to classes, teacher assignment, working calendar / holidays / events, school timing.
import { test, expect } from "@playwright/test";
import { createPerson } from "../../../api/organisations";
import { addStudent, createWorld, del, get, makeUser, must, patch, post, uid, type World } from "../../../api/pb-helpers";

let w: World;
let gradeSeq = 10;

test.beforeAll(async () => {
	w = await createWorld();
});

async function newClass(label = "C") {
	const grade = await must(post("/api/v1/grade-levels", w.ownerToken, { name: `${label} ${uid("")}`, sequenceOrder: ++gradeSeq, stage: "PRIMARY" }), "grade");
	const klass = await must(post("/api/v1/school-classes", w.ownerToken, { academicYearId: w.yearId, campusId: w.campusId, gradeLevelId: grade.id, displayName: `${label} class ${uid("")}` }), "class");
	return { grade, klass };
}

test.describe("Academic years", () => {
	test("create a year, list it, and mark it current (only one current at a time)", async () => {
		const y = await post("/api/v1/academic-years", w.ownerToken, { name: `PB Y ${uid("")}`, startDate: "2028-04-01", endDate: "2029-03-31" });
		expect(y.status).toBe(200);
		expect(y.body.status).toBe("DRAFT");
		expect((await post(`/api/v1/academic-years/${y.body.id}/mark-current`, w.ownerToken)).status).toBe(200);
		const list = await get("/api/v1/academic-years", w.ownerToken);
		expect(list.body.filter((x: any) => x.current).map((x: any) => x.id)).toEqual([y.body.id]);
		await must(post(`/api/v1/academic-years/${w.yearId}/mark-current`, w.ownerToken), "restore current");
	});

	test("a year whose end is before its start, or with a blank name, is rejected", async () => {
		expect((await post("/api/v1/academic-years", w.ownerToken, { name: `Bad ${uid("")}`, startDate: "2030-04-01", endDate: "2030-01-01" })).status).toBe(400);
		expect((await post("/api/v1/academic-years", w.ownerToken, { name: " ", startDate: "2030-04-01", endDate: "2031-03-31" })).status).toBe(400);
	});

	test("an overlapping year or a duplicate name is refused", async () => {
		const overlap = await post("/api/v1/academic-years", w.ownerToken, { name: `Over ${uid("")}`, startDate: "2026-09-01", endDate: "2027-08-31" });
		expect(overlap.status, "overlapping the current year").toBeGreaterThanOrEqual(400);
		expect(overlap.status).toBeLessThan(500);
		const dup = await post("/api/v1/academic-years", w.ownerToken, { name: w.yearName, startDate: "2031-04-01", endDate: "2032-03-31" });
		expect(dup.status).toBeGreaterThanOrEqual(400);
		expect(dup.status).toBeLessThan(500);
	});

	test("a year with enrolled students cannot be closed (409), an empty one can", async () => {
		await addStudent(w);
		const blocked = await post(`/api/v1/academic-years/${w.yearId}/close`, w.ownerToken);
		expect(blocked.status).toBe(409);
		const empty = await must(post("/api/v1/academic-years", w.ownerToken, { name: `Empty ${uid("")}`, startDate: "2033-04-01", endDate: "2034-03-31" }), "empty year");
		const closed = await post(`/api/v1/academic-years/${empty.id}/close`, w.ownerToken);
		expect(closed.status).toBe(200);
		expect(closed.body.status).toBe("CLOSED");
	});

	test("creating or changing years needs ORGANISATION_MANAGE", async () => {
		const u = await makeUser(w, "yearview", ["CLASS_VIEW"]);
		expect((await post("/api/v1/academic-years", u.token, { name: `X ${uid("")}`, startDate: "2035-04-01", endDate: "2036-03-31" })).status).toBe(403);
		expect((await post(`/api/v1/academic-years/${w.yearId}/mark-current`, u.token)).status).toBe(403);
		expect((await post(`/api/v1/academic-years/${w.yearId}/close`, u.token)).status).toBe(403);
	});
});

test.describe("Grade levels, classes and sections", () => {
	test("grade levels are listed in sequence and a duplicate name is refused", async () => {
		const name = `GL ${uid("")}`;
		const g = await post("/api/v1/grade-levels", w.ownerToken, { name, sequenceOrder: ++gradeSeq, stage: "PRIMARY" });
		expect(g.status).toBe(200);
		const dup = await post("/api/v1/grade-levels", w.ownerToken, { name, sequenceOrder: ++gradeSeq, stage: "PRIMARY" });
		expect(dup.status).toBeGreaterThanOrEqual(400);
		expect(dup.status).toBeLessThan(500);
		const list = await get("/api/v1/grade-levels", w.ownerToken);
		expect(list.body.map((x: any) => x.id)).toContain(g.body.id);
	});

	test("a class belongs to a year, campus and grade; the same grade cannot be used twice in one year and campus", async () => {
		const { grade, klass } = await newClass();
		const again = await post("/api/v1/school-classes", w.ownerToken, { academicYearId: w.yearId, campusId: w.campusId, gradeLevelId: grade.id, displayName: `Dup ${uid("")}` });
		expect(again.status).toBe(409);
		const list = await get(`/api/v1/school-classes?academicYearId=${w.yearId}`, w.ownerToken);
		expect(list.body.map((c: any) => c.id)).toContain(klass.id);
	});

	test("sections have a capacity and a name unique within the class", async () => {
		const { klass } = await newClass();
		const a = await post(`/api/v1/school-classes/${klass.id}/sections`, w.ownerToken, { name: "A", capacity: 2, room: "R1" });
		expect(a.status).toBe(200);
		expect(a.body.capacity).toBe(2);
		expect((await post(`/api/v1/school-classes/${klass.id}/sections`, w.ownerToken, { name: "A", capacity: 30, room: "R2" })).status).toBe(409);
		const bad = await post(`/api/v1/school-classes/${klass.id}/sections`, w.ownerToken, { name: "B", capacity: 0, room: "R3" });
		expect(bad.status).toBe(400);
		const list = await get(`/api/v1/school-classes/${klass.id}/sections`, w.ownerToken);
		expect(list.body.map((s: any) => s.name)).toContain("A");
	});

	test("a section at capacity refuses another enrolment", async () => {
		const { klass } = await newClass();
		const sec = await must(post(`/api/v1/school-classes/${klass.id}/sections`, w.ownerToken, { name: "A", capacity: 1, room: "R" }), "section");
		await addStudent(w, { sectionId: sec.id, roll: 1 });
		const second = await addStudent(w, { sectionId: w.sectionId, roll: 5 }); // admitted elsewhere
		const res = await post(`/api/v1/students/${second.studentId}/enrollments/reassign-section`, w.ownerToken, { sectionId: sec.id });
		expect(res.status, "capacity 1 section accepted a second student").toBeGreaterThanOrEqual(400);
		expect(res.status).toBeLessThan(500);
	});

	test("a class or section with current students cannot be deactivated", async () => {
		const { klass } = await newClass();
		const sec = await must(post(`/api/v1/school-classes/${klass.id}/sections`, w.ownerToken, { name: "A", capacity: 10, room: "R" }), "section");
		await addStudent(w, { sectionId: sec.id, roll: 1 });
		expect((await post(`/api/v1/school-classes/${klass.id}/status`, w.ownerToken, { status: "INACTIVE" })).status).toBe(409);
		expect((await post(`/api/v1/school-classes/${klass.id}/sections/${sec.id}/status`, w.ownerToken, { status: "INACTIVE" })).status).toBe(409);
	});

	test("an empty class can be renamed and deactivated", async () => {
		const { klass } = await newClass();
		const renamed = await patch(`/api/v1/school-classes/${klass.id}`, w.ownerToken, { displayName: `Renamed ${uid("")}` });
		expect(renamed.status).toBe(200);
		const off = await post(`/api/v1/school-classes/${klass.id}/status`, w.ownerToken, { status: "INACTIVE" });
		expect(off.status).toBe(200);
	});

	test("class and grade management need CLASS_MANAGE / GRADE_LEVEL_MANAGE (403 otherwise)", async () => {
		const u = await makeUser(w, "acadview", ["CLASS_VIEW", "GRADE_LEVEL_VIEW", "SUBJECT_VIEW"]);
		expect((await post("/api/v1/grade-levels", u.token, { name: `X ${uid("")}`, sequenceOrder: 99, stage: "PRIMARY" })).status).toBe(403);
		expect((await post(`/api/v1/school-classes/${w.classId}/sections`, u.token, { name: "Z", capacity: 5, room: "R" })).status).toBe(403);
		expect((await post("/api/v1/subjects", u.token, { name: `S ${uid("")}`, code: uid("s").toUpperCase() })).status).toBe(403);
		expect((await get("/api/v1/grade-levels", u.token)).status).toBe(200);
	});
});

test.describe("Subjects mapped to classes, and teacher assignment", () => {
	test("a subject is created once per code, mapped to a class, and cannot be mapped twice", async () => {
		const code = uid("sub").toUpperCase();
		const s = await post("/api/v1/subjects", w.ownerToken, { name: `Sci ${code}`, code });
		expect(s.status).toBe(200);
		expect((await post("/api/v1/subjects", w.ownerToken, { name: `Other ${code}`, code })).status).toBe(409);
		const { klass } = await newClass();
		const map = await post(`/api/v1/school-classes/${klass.id}/subjects`, w.ownerToken, { subjectId: s.body.id, mandatory: true, weeklyPeriods: 5 });
		expect(map.status).toBe(200);
		expect(map.body).toMatchObject({ subjectId: s.body.id, mandatory: true, weeklyPeriods: 5, status: "ACTIVE" });
		const again = await post(`/api/v1/school-classes/${klass.id}/subjects`, w.ownerToken, { subjectId: s.body.id, mandatory: true, weeklyPeriods: 5 });
		expect(again.status).toBe(409);
		const list = await get(`/api/v1/school-classes/${klass.id}/subjects`, w.ownerToken);
		expect(list.body.map((x: any) => x.subjectId)).toContain(s.body.id);
	});

	test("a subject can be mapped as elective, edited and switched off for a class", async () => {
		const s = await must(post("/api/v1/subjects", w.ownerToken, { name: `El ${uid("")}`, code: uid("el").toUpperCase() }), "subject");
		const { klass } = await newClass();
		const map = await must(post(`/api/v1/school-classes/${klass.id}/subjects`, w.ownerToken, { subjectId: s.id, mandatory: false, weeklyPeriods: 2 }), "map");
		expect(map.mandatory).toBe(false);
		const upd = await patch(`/api/v1/school-classes/${klass.id}/subjects/${map.id}`, w.ownerToken, { mandatory: true, weeklyPeriods: 4 });
		expect(upd.status).toBe(200);
		expect(upd.body).toMatchObject({ mandatory: true, weeklyPeriods: 4 });
		const off = await post(`/api/v1/school-classes/${klass.id}/subjects/${map.id}/status`, w.ownerToken, { status: "INACTIVE" });
		expect(off.status).toBe(200);
	});

	test("a teacher is assigned to a section (homeroom and subject), listed both ways, and the assignment can be ended", async () => {
		const teacher = await createPerson(w.ownerToken, { firstName: "Tea", lastName: uid("") });
		const home = await post(`/api/v1/sections/${w.sectionId}/teacher-assignments`, w.ownerToken, { teacherPersonId: teacher.id, assignmentType: "HOMEROOM", startDate: "2026-06-01" });
		expect(home.status).toBe(200);
		const subj = await post(`/api/v1/sections/${w.sectionId}/teacher-assignments`, w.ownerToken, { teacherPersonId: teacher.id, subjectId: w.subjectId, assignmentType: "SUBJECT", startDate: "2026-06-01" });
		expect(subj.status).toBe(200);
		const bySection = await get(`/api/v1/sections/${w.sectionId}/teacher-assignments`, w.ownerToken);
		expect(bySection.body.filter((a: any) => a.teacherPersonId === teacher.id)).toHaveLength(2);
		const byTeacher = await get(`/api/v1/teachers/${teacher.id}/assignments`, w.ownerToken);
		expect(byTeacher.body).toHaveLength(2);
		const ended = await post(`/api/v1/teacher-assignments/${subj.body.id}/end`, w.ownerToken, { endDate: "2026-09-30" });
		expect(ended.status).toBe(200);
	});

	test("assigning a new homeroom teacher replaces the previous one (one ACTIVE homeroom per section, history kept)", async () => {
		const { klass } = await newClass();
		const sec = await must(post(`/api/v1/school-classes/${klass.id}/sections`, w.ownerToken, { name: "H", capacity: 10, room: "R" }), "section");
		const t1 = await createPerson(w.ownerToken, { firstName: "H1", lastName: uid("") });
		const t2 = await createPerson(w.ownerToken, { firstName: "H2", lastName: uid("") });
		expect((await post(`/api/v1/sections/${sec.id}/teacher-assignments`, w.ownerToken, { teacherPersonId: t1.id, assignmentType: "HOMEROOM", startDate: "2026-06-01" })).status).toBe(200);
		const second = await post(`/api/v1/sections/${sec.id}/teacher-assignments`, w.ownerToken, { teacherPersonId: t2.id, assignmentType: "HOMEROOM", startDate: "2026-07-01" });
		expect(second.status).toBe(200);
		const rows = (await get(`/api/v1/sections/${sec.id}/teacher-assignments`, w.ownerToken)).body.filter((a: any) => a.assignmentType === "HOMEROOM");
		expect(rows.filter((a: any) => a.status === "ACTIVE").map((a: any) => a.teacherPersonId)).toEqual([t2.id]);
		expect(rows).toHaveLength(2);
	});

	test("teacher assignment needs TEACHER_ASSIGNMENT_MANAGE (403 otherwise)", async () => {
		const u = await makeUser(w, "tview", ["TEACHER_ASSIGNMENT_VIEW"]);
		const t = await createPerson(w.ownerToken, { firstName: "T", lastName: uid("") });
		expect((await post(`/api/v1/sections/${w.sectionId}/teacher-assignments`, u.token, { teacherPersonId: t.id, assignmentType: "HOMEROOM", startDate: "2026-06-01" })).status).toBe(403);
	});
});

test.describe("Working calendar, holidays, events and school timing", () => {
	test("a holiday and a special working day are recorded for the year; one rule per date", async () => {
		const hol = await post("/api/v1/academic/working-calendar", w.ownerToken, { academicYearId: w.yearId, date: "2026-12-25", label: "Christmas", type: "HOLIDAY" });
		expect(hol.status).toBe(200);
		const swd = await post("/api/v1/academic/working-calendar", w.ownerToken, { academicYearId: w.yearId, date: "2026-12-26", label: "Make-up day", type: "SPECIAL_WORKING_DAY" });
		expect(swd.status).toBe(200);
		expect((await post("/api/v1/academic/working-calendar", w.ownerToken, { academicYearId: w.yearId, date: "2026-12-25", label: "Again", type: "HOLIDAY" })).status).toBe(409);
		const list = await get(`/api/v1/academic/working-calendar?academicYearId=${w.yearId}`, w.ownerToken);
		expect(list.body.map((e: any) => e.date)).toEqual(expect.arrayContaining(["2026-12-25", "2026-12-26"]));
	});

	test("a calendar rule outside the academic year is rejected; a deleted holiday disappears", async () => {
		const out = await post("/api/v1/academic/working-calendar", w.ownerToken, { academicYearId: w.yearId, date: "2030-01-01", label: "Far", type: "HOLIDAY" });
		expect(out.status).toBe(400);
		const h = await must(post("/api/v1/academic/working-calendar", w.ownerToken, { academicYearId: w.yearId, date: "2026-10-02", label: "Gandhi Jayanti", type: "HOLIDAY" }), "holiday");
		expect((await del(`/api/v1/academic/working-calendar/${h.id}`, w.ownerToken)).status).toBe(204);
		const list = await get(`/api/v1/academic/working-calendar?academicYearId=${w.yearId}`, w.ownerToken);
		expect(list.body.map((e: any) => e.id)).not.toContain(h.id);
	});

	test("school events can be added and listed", async () => {
		const ev = await post("/api/v1/academic/school-events", w.ownerToken, { date: "2026-11-14", title: `Sports day ${uid("")}`, description: "pb" });
		expect(ev.status).toBe(200);
		const list = await get("/api/v1/academic/school-events?from=2026-11-01&to=2026-11-30", w.ownerToken);
		expect(list.status).toBe(200);
		expect(JSON.stringify(list.body)).toContain(ev.body.title);
	});

	test("school timing can be set; an end before the start is rejected", async () => {
		const ok = await patch("/api/v1/academic/settings", w.ownerToken, { schoolStartTime: "08:30", schoolEndTime: "15:00" });
		expect(ok.status).toBe(200);
		expect(ok.body).toMatchObject({ schoolStartTime: "08:30", schoolEndTime: "15:00" });
		expect((await patch("/api/v1/academic/settings", w.ownerToken, { schoolStartTime: "16:00", schoolEndTime: "09:00" })).status).toBe(400);
		expect((await patch("/api/v1/academic/settings", w.ownerToken, { schoolStartTime: "later" })).status).toBe(400);
	});

	test("calendar, events and timing writes need their MANAGE permissions", async () => {
		const u = await makeUser(w, "calview", ["WORKING_CALENDAR_VIEW", "SCHOOL_EVENT_VIEW", "ACADEMIC_CONFIGURATION_VIEW"]);
		expect((await post("/api/v1/academic/working-calendar", u.token, { academicYearId: w.yearId, date: "2026-08-15", label: "x", type: "HOLIDAY" })).status).toBe(403);
		expect((await post("/api/v1/academic/school-events", u.token, { date: "2026-11-15", title: "x" })).status).toBe(403);
		expect((await patch("/api/v1/academic/settings", u.token, { schoolStartTime: "09:00" })).status).toBe(403);
		expect((await get("/api/v1/academic/settings", u.token)).status).toBe(200);
	});
});
