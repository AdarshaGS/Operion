// Playbook demo story 4: "Exam results" - create exam (unit test / mid-term / final / other)
// -> schedule subject-wise -> marks DRAFT -> SUBMITTED -> APPROVED -> grading scale & pass
// rules -> publish report card. An unapproved register must not publish a report card.
import { test, expect } from "@playwright/test";
import { createSubject } from "../../../api/organisations";
import { addStudent, createWorld, get, makeUser, must, post, put, patch, uid, type World } from "../../../api/pb-helpers";

let w: World;

test.beforeAll(async () => {
	w = await createWorld();
});

const BANDS = [
	{ grade: "A", minPercentage: 80, remark: "Excellent" },
	{ grade: "B", minPercentage: 60, remark: "Good" },
	{ grade: "C", minPercentage: 40, remark: "Pass" },
	{ grade: "F", minPercentage: 0, remark: "Fail" },
];

async function scale(name = `Scale ${uid("")}`) {
	return must(post("/api/v1/examinations/grading-scales", w.ownerToken, { name, defaultScale: false, bands: BANDS }), "grading scale");
}

/** An exam with one schedule per subject for the world's class (all sections), and `n` students in the world section. */
async function examWorld(subjectCount = 1, n = 2, maxMarks = 100, passMarks = 35) {
	const exam = await must(post("/api/v1/examinations/exams", w.ownerToken, { academicYearId: w.yearId, name: `Exam ${uid("")}`, examType: "UNIT_TEST" }), "exam");
	const schedules = [];
	for (let i = 0; i < subjectCount; i++) {
		const subject = await createSubject(w.ownerToken, `Sub ${uid("")}`, uid("c").toUpperCase());
		schedules.push(
			await must(
				post(`/api/v1/examinations/exams/${exam.id}/schedules`, w.ownerToken, {
					schoolClassId: w.classId,
					subjectId: subject.id,
					examDate: "2026-10-05",
					maxMarks,
					passMarks,
				}),
				"schedule",
			),
		);
	}
	// A section of its own so other tests' students (whole-class schedules) do not interfere.
	const students = [];
	for (let i = 0; i < n; i++) students.push(await addStudent(w, { roll: 500 + Math.floor(Math.random() * 400) }));
	return { exam, schedules, students };
}

const enter = (scheduleId: number, marks: any[], token = w.ownerToken) => post(`/api/v1/examinations/schedules/${scheduleId}/marks`, token, { marks });
const submit = (scheduleId: number, token = w.ownerToken) => post(`/api/v1/examinations/schedules/${scheduleId}/submit`, token);
const approve = (scheduleId: number, token = w.ownerToken) => post(`/api/v1/examinations/schedules/${scheduleId}/approve`, token);

test.describe("Story 4: exam results", () => {
	test("an exam can be created as unit test, mid-term, final or other; anything else is rejected", async () => {
		for (const examType of ["UNIT_TEST", "MID_TERM", "FINAL", "OTHER"]) {
			const res = await post("/api/v1/examinations/exams", w.ownerToken, { academicYearId: w.yearId, name: `${examType} ${uid("")}`, examType });
			expect(res.status, examType).toBe(200);
			expect(res.body.examType).toBe(examType);
		}
		const bad = await post("/api/v1/examinations/exams", w.ownerToken, { academicYearId: w.yearId, name: "Bad", examType: "SURPRISE_QUIZ" });
		expect(bad.status).toBeGreaterThanOrEqual(400);
		expect(bad.status).toBeLessThan(500);
		const list = await get(`/api/v1/examinations/exams?academicYearId=${w.yearId}`, w.ownerToken);
		expect(list.body.length).toBeGreaterThanOrEqual(4);
	});

	test("an exam is scheduled subject-wise; the same subject twice for one class is a conflict", async () => {
		const { exam, schedules } = await examWorld(2, 0);
		const list = await get(`/api/v1/examinations/exams/${exam.id}/schedules`, w.ownerToken);
		expect(list.status).toBe(200);
		expect(list.body).toHaveLength(2);
		const dup = await post(`/api/v1/examinations/exams/${exam.id}/schedules`, w.ownerToken, {
			schoolClassId: w.classId,
			subjectId: schedules[0].subjectId,
			examDate: "2026-10-06",
			maxMarks: 50,
			passMarks: 20,
		});
		expect(dup.status).toBe(409);
	});

	test("marks go DRAFT -> SUBMITTED -> APPROVED, and entry is closed once submitted", async () => {
		const { schedules, students } = await examWorld(1, 2);
		const sid = schedules[0].id;
		expect((await get(`/api/v1/examinations/schedules/${sid}/register`, w.ownerToken)).body.registerStatus).toBe("DRAFT");
		const entered = await enter(sid, students.map((s, i) => ({ studentEnrollmentId: s.enrollmentId, marksObtained: 70 + i, absent: false })));
		expect(entered.status).toBe(200);
		expect(entered.body).toHaveLength(2);
		const submitted = await submit(sid);
		expect(submitted.status).toBe(200);
		expect(submitted.body.registerStatus).toBe("SUBMITTED");
		const lateEntry = await enter(sid, [{ studentEnrollmentId: students[0].enrollmentId, marksObtained: 10, absent: false }]);
		expect(lateEntry.status).toBe(409);
		const approved = await approve(sid);
		expect(approved.status).toBe(200);
		expect(approved.body.registerStatus).toBe("APPROVED");
	});

	test("marks outside 0..max, and missing marks for a present student, are rejected", async () => {
		const { schedules, students } = await examWorld(1, 1, 50, 20);
		const sid = schedules[0].id;
		const over = await enter(sid, [{ studentEnrollmentId: students[0].enrollmentId, marksObtained: 51, absent: false }]);
		expect(over.status).toBe(400);
		const negative = await enter(sid, [{ studentEnrollmentId: students[0].enrollmentId, marksObtained: -1, absent: false }]);
		expect(negative.status).toBe(400);
		const none = await enter(sid, [{ studentEnrollmentId: students[0].enrollmentId, absent: false }]);
		expect(none.status).toBe(400);
	});

	test("the same student cannot have marks entered twice for one schedule", async () => {
		const { schedules, students } = await examWorld(1, 1);
		const sid = schedules[0].id;
		const row = { studentEnrollmentId: students[0].enrollmentId, marksObtained: 60, absent: false };
		expect((await enter(sid, [row])).status).toBe(200);
		expect((await enter(sid, [row])).status).toBe(409);
	});

	test("a grading scale with bands can be created and read back", async () => {
		const created = await scale();
		const read = await get(`/api/v1/examinations/grading-scales/${created.id}`, w.ownerToken);
		expect(read.status).toBe(200);
		expect(read.body.bands.map((b: any) => b.grade).sort()).toEqual(["A", "B", "C", "F"]);
		const list = await get("/api/v1/examinations/grading-scales", w.ownerToken);
		expect(list.body.some((s: any) => s.id === created.id)).toBe(true);
	});

	test("pass rules: strategy and aggregate percentage can be set; invalid values rejected", async () => {
		for (const strategy of ["PASS_EVERY_SUBJECT", "MINIMUM_AGGREGATE_PERCENTAGE", "BOTH"]) {
			const res = await put("/api/v1/examinations/settings", w.ownerToken, { rankingEnabled: false, passFailStrategy: strategy, minimumAggregatePercentage: 40 });
			expect(res.status, strategy).toBe(200);
			expect((await get("/api/v1/examinations/settings", w.ownerToken)).body.passFailStrategy).toBe(strategy);
		}
		const badPct = await put("/api/v1/examinations/settings", w.ownerToken, { rankingEnabled: false, passFailStrategy: "BOTH", minimumAggregatePercentage: 150 });
		expect(badPct.status).toBe(400);
		const badStrategy = await put("/api/v1/examinations/settings", w.ownerToken, { rankingEnabled: false, passFailStrategy: "VIBES", minimumAggregatePercentage: 40 });
		expect(badStrategy.status).toBeGreaterThanOrEqual(400);
		expect(badStrategy.status).toBeLessThan(500);
	});

	test("full story: approved marks publish a report card with total, percentage, grade and pass result", async () => {
		await must(put("/api/v1/examinations/settings", w.ownerToken, { rankingEnabled: false, passFailStrategy: "PASS_EVERY_SUBJECT", minimumAggregatePercentage: 40 }), "settings");
		const { exam, schedules, students } = await examWorld(2, 1, 100, 35);
		const gs = await scale();
		const marks = [85, 75];
		for (let i = 0; i < schedules.length; i++) {
			await must(enter(schedules[i].id, [{ studentEnrollmentId: students[0].enrollmentId, marksObtained: marks[i], absent: false }]), "enter");
			await must(submit(schedules[i].id), "submit");
			await must(approve(schedules[i].id), "approve");
		}
		const card = await post(`/api/v1/examinations/exams/${exam.id}/report-cards`, w.ownerToken, { studentEnrollmentId: students[0].enrollmentId, gradingScaleId: gs.id });
		expect(card.status).toBe(200);
		expect(card.body).toMatchObject({ totalMarksObtained: 160, totalMaxMarks: 200, percentage: 80, overallGrade: "A", passed: true, status: "PUBLISHED" });
		const listed = await get(`/api/v1/examinations/report-cards?studentEnrollmentId=${students[0].enrollmentId}`, w.ownerToken);
		expect(listed.body.some((r: any) => r.id === card.body.id)).toBe(true);
		const again = await post(`/api/v1/examinations/exams/${exam.id}/report-cards`, w.ownerToken, { studentEnrollmentId: students[0].enrollmentId, gradingScaleId: gs.id });
		expect(again.status).toBe(409);
	});

	test("negative: a register that is not APPROVED cannot publish a report card (DRAFT, SUBMITTED, not started)", async () => {
		const { exam, schedules, students } = await examWorld(1, 1);
		const gs = await scale();
		const publish = () => post(`/api/v1/examinations/exams/${exam.id}/report-cards`, w.ownerToken, { studentEnrollmentId: students[0].enrollmentId, gradingScaleId: gs.id });
		expect((await publish()).status).toBe(409);
		await must(enter(schedules[0].id, [{ studentEnrollmentId: students[0].enrollmentId, marksObtained: 80, absent: false }]), "enter");
		const draft = await publish();
		expect(draft.status).toBe(409);
		expect(draft.body.error).toMatch(/not yet approved|DRAFT/i);
		await must(submit(schedules[0].id), "submit");
		expect((await publish()).status).toBe(409);
		await must(approve(schedules[0].id), "approve");
		expect((await publish()).status).toBe(200);
	});

	test("pass rules apply: PASS_EVERY_SUBJECT fails a student who is below pass marks in one subject despite a high aggregate", async () => {
		await must(put("/api/v1/examinations/settings", w.ownerToken, { rankingEnabled: false, passFailStrategy: "PASS_EVERY_SUBJECT", minimumAggregatePercentage: 40 }), "settings");
		const { exam, schedules, students } = await examWorld(2, 1, 100, 35);
		const gs = await scale();
		const marks = [95, 20];
		for (let i = 0; i < 2; i++) {
			await must(enter(schedules[i].id, [{ studentEnrollmentId: students[0].enrollmentId, marksObtained: marks[i], absent: false }]), "enter");
			await must(submit(schedules[i].id), "submit");
			await must(approve(schedules[i].id), "approve");
		}
		const card = await post(`/api/v1/examinations/exams/${exam.id}/report-cards`, w.ownerToken, { studentEnrollmentId: students[0].enrollmentId, gradingScaleId: gs.id });
		expect(card.status).toBe(200);
		expect(card.body.percentage).toBeCloseTo(57.5);
		expect(card.body.passed).toBe(false);
	});

	test("pre-publish correction of marks works; after publish it is refused on the normal path", async () => {
		const { exam, schedules, students } = await examWorld(1, 1);
		const gs = await scale();
		const entered = await must(enter(schedules[0].id, [{ studentEnrollmentId: students[0].enrollmentId, marksObtained: 50, absent: false }]), "enter");
		const fix = await patch(`/api/v1/examinations/marks/${entered[0].id}`, w.ownerToken, { marksObtained: 55, absent: false, remarks: "recheck" });
		expect(fix.status).toBe(200);
		expect(fix.body.marksObtained).toBe(55);
		await must(submit(schedules[0].id), "submit");
		await must(approve(schedules[0].id), "approve");
		await must(post(`/api/v1/examinations/exams/${exam.id}/report-cards`, w.ownerToken, { studentEnrollmentId: students[0].enrollmentId, gradingScaleId: gs.id }), "publish");
		const late = await patch(`/api/v1/examinations/marks/${entered[0].id}`, w.ownerToken, { marksObtained: 60, absent: false });
		expect(late.status).toBe(409);
	});

	test("an absent student is recorded without marks and the report card still publishes (no server error)", async () => {
		await must(put("/api/v1/examinations/settings", w.ownerToken, { rankingEnabled: false, passFailStrategy: "PASS_EVERY_SUBJECT", minimumAggregatePercentage: 40 }), "settings");
		const { exam, schedules, students } = await examWorld(1, 1);
		const gs = await scale();
		const entered = await enter(schedules[0].id, [{ studentEnrollmentId: students[0].enrollmentId, absent: true }]);
		expect(entered.status).toBe(200);
		await must(submit(schedules[0].id), "submit");
		await must(approve(schedules[0].id), "approve");
		const card = await post(`/api/v1/examinations/exams/${exam.id}/report-cards`, w.ownerToken, { studentEnrollmentId: students[0].enrollmentId, gradingScaleId: gs.id });
		expect(card.status, JSON.stringify(card.body)).toBeLessThan(500);
	});

	test("permissions: MARKS_ENTER / MARKS_SUBMIT / MARKS_APPROVE / REPORT_CARD_PUBLISH / EXAM_MANAGE / GRADING_SCALE_MANAGE are each enforced (403)", async () => {
		const { exam, schedules, students } = await examWorld(1, 1);
		const gs = await scale();
		const viewer = await makeUser(w, "examview", ["EXAM_VIEW"]);
		expect((await post("/api/v1/examinations/exams", viewer.token, { academicYearId: w.yearId, name: "x", examType: "OTHER" })).status).toBe(403);
		expect((await post("/api/v1/examinations/grading-scales", viewer.token, { name: "x", defaultScale: false, bands: BANDS })).status).toBe(403);
		expect((await enter(schedules[0].id, [{ studentEnrollmentId: students[0].enrollmentId, marksObtained: 50, absent: false }], viewer.token)).status).toBe(403);
		expect((await submit(schedules[0].id, viewer.token)).status).toBe(403);
		expect((await approve(schedules[0].id, viewer.token)).status).toBe(403);
		expect((await post(`/api/v1/examinations/exams/${exam.id}/report-cards`, viewer.token, { studentEnrollmentId: students[0].enrollmentId, gradingScaleId: gs.id })).status).toBe(403);
		expect((await get(`/api/v1/examinations/exams?academicYearId=${w.yearId}`, viewer.token)).status).toBe(200);

		const teacher = await makeUser(w, "examteach", ["EXAM_VIEW", "MARKS_ENTER", "MARKS_SUBMIT"]);
		expect((await enter(schedules[0].id, [{ studentEnrollmentId: students[0].enrollmentId, marksObtained: 50, absent: false }], teacher.token)).status).toBe(200);
		expect((await submit(schedules[0].id, teacher.token)).status).toBe(200);
		expect((await approve(schedules[0].id, teacher.token)).status).toBe(403);
	});
});
