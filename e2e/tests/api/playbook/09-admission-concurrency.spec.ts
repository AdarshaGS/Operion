// Finding probe (not a playbook claim): schools being set up at the same moment. The first
// admission in an organisation (per year) inserts its student_id_counters row lazily
// (StudentIdGenerator.next: select, then insert). Concurrent first inserts were seen to
// deadlock in MySQL (HTTP 500), including between two DIFFERENT organisations.
import { test, expect } from "@playwright/test";
import { createPerson } from "../../../api/organisations";
import { call, createWorld, uid } from "../../../api/pb-helpers";

async function personFor(token: string) {
	return createPerson(token, { firstName: `Cc${uid("")}`, lastName: "Pb", dateOfBirth: "2015-04-10", gender: "FEMALE" });
}

const admit = (token: string, personId: number) =>
	call("POST", "/api/v1/students", token, { personId, admissionNumber: `A-${uid("")}`.toUpperCase(), admissionDate: "2026-06-01" });

test("concurrent first admissions in one new school all succeed (no deadlock 500)", async () => {
	const w = await createWorld({ warmStudentCounter: false });
	const people = await Promise.all(Array.from({ length: 6 }, () => personFor(w.ownerToken)));
	const results = await Promise.all(people.map((p) => admit(w.ownerToken, p.id)));
	expect(results.map((r) => r.status)).toEqual(people.map(() => 200));
});

test("first admissions in several different new schools at the same moment all succeed", async () => {
	const worlds = await Promise.all(Array.from({ length: 5 }, () => createWorld({ warmStudentCounter: false })));
	const people = await Promise.all(worlds.map((w) => personFor(w.ownerToken)));
	const results = await Promise.all(worlds.map((w, i) => admit(w.ownerToken, people[i].id)));
	expect(results.map((r) => r.status)).toEqual(worlds.map(() => 200));
});
