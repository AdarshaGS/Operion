// Playbook demo story 2: "School day" - class register -> mark attendance (Present/Absent/
// Late/Half day/Leave) -> submit -> lock -> correction and its history. A locked register
// needs unlocking by authorised staff; corrections keep history.
import { test, expect } from "@playwright/test";
import { createSection } from "../../../api/organisations";
import { addStudent, createWorld, get, makeUser, must, patch, post, uid, type World } from "../../../api/pb-helpers";

let w: World;
let dayCounter = 0;
let sectionCounter = 0;

test.beforeAll(async () => {
	w = await createWorld();
});

/** A section of its own with `n` students, plus a unique weekday-ish date inside the year. */
async function setup(n = 5) {
	const section = await createSection(w.ownerToken, w.classId, `S${++sectionCounter}`, 40, "R");
	const students = [];
	for (let i = 0; i < n; i++) students.push(await addStudent(w, { sectionId: section.id, roll: i + 1 }));
	dayCounter += 1;
	const date = new Date(Date.UTC(2026, 8, 1 + dayCounter)).toISOString().slice(0, 10);
	return { sectionId: section.id, students, date };
}

const STATUSES = ["PRESENT", "ABSENT", "LATE", "HALF_DAY", "LEAVE"];

async function markAll(s: Awaited<ReturnType<typeof setup>>, token = w.ownerToken) {
	return post(`/api/v1/attendance/sections/${s.sectionId}/register`, token, {
		attendanceDate: s.date,
		marks: s.students.map((st, i) => ({ studentEnrollmentId: st.enrollmentId, status: STATUSES[i % STATUSES.length], excused: false, remarks: null })),
	});
}

test.describe("Story 2: school day", () => {
	test("register accepts all five statuses (Present/Absent/Late/Half day/Leave) as a DRAFT", async () => {
		const s = await setup(5);
		const res = await markAll(s);
		expect(res.status).toBe(200);
		expect(res.body.register.registerStatus).toBe("DRAFT");
		expect(res.body.entries.map((e: any) => e.attendanceStatus).sort()).toEqual([...STATUSES].sort());
	});

	test("register -> submit -> lock walks DRAFT, SUBMITTED, LOCKED", async () => {
		const s = await setup(2);
		const reg = await must(markAll(s), "mark");
		const id = reg.register.id;
		const submitted = await post(`/api/v1/attendance/register/${id}/submit`, w.ownerToken);
		expect(submitted.status).toBe(200);
		expect(JSON.stringify(submitted.body)).toContain("SUBMITTED");
		const locked = await post(`/api/v1/attendance/register/${id}/lock`, w.ownerToken);
		expect(locked.status).toBe(200);
		const fetched = await get(`/api/v1/attendance/sections/${s.sectionId}/register?date=${s.date}`, w.ownerToken);
		expect(fetched.status).toBe(200);
		expect(JSON.stringify(fetched.body)).toContain("LOCKED");
	});

	test("a locked register cannot be corrected until unlocked; correction keeps history", async () => {
		const s = await setup(2);
		const reg = await must(markAll(s), "mark");
		const regId = reg.register.id;
		const entry = reg.entries[0];
		await must(post(`/api/v1/attendance/register/${regId}/submit`, w.ownerToken), "submit");
		await must(post(`/api/v1/attendance/register/${regId}/lock`, w.ownerToken), "lock");

		const blocked = await patch(`/api/v1/attendance/students/${entry.id}`, w.ownerToken, { newStatus: "LEAVE", reason: "should be blocked" });
		expect(blocked.status).toBe(409);

		const reMark = await markAll(s);
		expect(reMark.status).toBeGreaterThanOrEqual(400);

		await must(post(`/api/v1/attendance/register/${regId}/unlock`, w.ownerToken), "unlock");
		const target = entry.attendanceStatus === "LEAVE" ? "ABSENT" : "LEAVE";
		const fixed = await patch(`/api/v1/attendance/students/${entry.id}`, w.ownerToken, { newStatus: target, reason: "Parent called" });
		expect(fixed.status).toBe(200);
		const history = await get(`/api/v1/attendance/students/${entry.id}/corrections`, w.ownerToken);
		expect(history.status).toBe(200);
		expect(history.body).toHaveLength(1);
		expect(history.body[0]).toMatchObject({ previousStatus: entry.attendanceStatus, newStatus: target, reason: "Parent called" });
		expect(history.body[0].correctedBy).toBeTruthy();
	});

	test("successive corrections each add a history row", async () => {
		const s = await setup(1);
		const reg = await must(markAll(s), "mark");
		await must(post(`/api/v1/attendance/register/${reg.register.id}/submit`, w.ownerToken), "submit");
		const entry = reg.entries[0];
		expect((await patch(`/api/v1/attendance/students/${entry.id}`, w.ownerToken, { newStatus: "ABSENT", reason: "one" })).status).toBe(200);
		expect((await patch(`/api/v1/attendance/students/${entry.id}`, w.ownerToken, { newStatus: "LATE", reason: "two" })).status).toBe(200);
		const history = await get(`/api/v1/attendance/students/${entry.id}/corrections`, w.ownerToken);
		expect(history.body.map((h: any) => h.reason)).toEqual(expect.arrayContaining(["one", "two"]));
		expect(history.body).toHaveLength(2);
	});

	test("correcting to the same status is rejected", async () => {
		const s = await setup(1);
		const reg = await must(markAll(s), "mark");
		await must(post(`/api/v1/attendance/register/${reg.register.id}/submit`, w.ownerToken), "submit");
		const entry = reg.entries[0];
		const same = await patch(`/api/v1/attendance/students/${entry.id}`, w.ownerToken, { newStatus: entry.attendanceStatus, reason: "noop" });
		expect(same.status).toBe(409);
	});

	test("the same student cannot be marked twice for one day", async () => {
		const s = await setup(2);
		expect((await markAll(s)).status).toBe(200);
		const second = await markAll(s);
		expect(second.status).toBeGreaterThanOrEqual(400);
		expect(second.status).toBeLessThan(500);
	});

	test("a calendar holiday blocks marking", async () => {
		const s = await setup(1);
		await must(
			post("/api/v1/academic/working-calendar", w.ownerToken, { academicYearId: w.yearId, date: s.date, label: "Pb Holiday", type: "HOLIDAY" }),
			"holiday",
		);
		const res = await markAll(s);
		expect(res.status).toBeGreaterThanOrEqual(400);
		expect(res.status).toBeLessThan(500);
	});

	test("marking outside the academic year is rejected", async () => {
		const s = await setup(1);
		const res = await post(`/api/v1/attendance/sections/${s.sectionId}/register`, w.ownerToken, {
			attendanceDate: "2024-01-10",
			marks: [{ studentEnrollmentId: s.students[0].enrollmentId, status: "PRESENT", excused: false }],
		});
		expect(res.status).toBeGreaterThanOrEqual(400);
		expect(res.status).toBeLessThan(500);
	});

	test("permissions: mark needs ATTENDANCE_MARK, lock needs ATTENDANCE_LOCK, unlock needs ATTENDANCE_UNLOCK, correct needs ATTENDANCE_CORRECT", async () => {
		const s = await setup(1);
		const viewer = await makeUser(w, "attview", ["ATTENDANCE_VIEW"]);
		expect((await markAll(s, viewer.token)).status).toBe(403);

		const marker = await makeUser(w, "attmark", ["ATTENDANCE_VIEW", "ATTENDANCE_MARK_ANY_SECTION"]);
		const reg = await must(markAll(s, marker.token), "mark as marker");
		const id = reg.register.id;
		expect((await post(`/api/v1/attendance/register/${id}/submit`, marker.token)).status).toBe(200);
		expect((await post(`/api/v1/attendance/register/${id}/lock`, marker.token)).status).toBe(403);
		await must(post(`/api/v1/attendance/register/${id}/lock`, w.ownerToken), "owner lock");
		expect((await post(`/api/v1/attendance/register/${id}/unlock`, marker.token)).status).toBe(403);
		const entry = reg.entries[0];
		expect((await patch(`/api/v1/attendance/students/${entry.id}`, marker.token, { newStatus: "LEAVE", reason: "x" })).status).toBe(403);
	});

	test("a staff member holding only ATTENDANCE_LOCK/UNLOCK (the 'authorised staff') can unlock", async () => {
		const s = await setup(1);
		const reg = await must(markAll(s), "mark");
		const id = reg.register.id;
		await must(post(`/api/v1/attendance/register/${id}/submit`, w.ownerToken), "submit");
		const locker = await makeUser(w, "locker", ["ATTENDANCE_VIEW", "ATTENDANCE_LOCK", "ATTENDANCE_UNLOCK"]);
		expect((await post(`/api/v1/attendance/register/${id}/lock`, locker.token)).status).toBe(200);
		expect((await post(`/api/v1/attendance/register/${id}/unlock`, locker.token)).status).toBe(200);
	});

	test("student attendance range and monthly summary reflect what was marked", async () => {
		const s = await setup(2);
		await must(markAll(s), "mark");
		const range = await get(`/api/v1/attendance/enrollments/${s.students[0].enrollmentId}?from=${s.date}&to=${s.date}`, w.ownerToken);
		expect(range.status).toBe(200);
		expect(JSON.stringify(range.body)).toContain("PRESENT");
		const [y, m] = s.date.split("-");
		const summary = await get(`/api/v1/attendance/enrollments/${s.students[0].enrollmentId}/summary?year=${y}&month=${Number(m)}`, w.ownerToken);
		expect(summary.status).toBe(200);
	});
});
