// Playbook FAQ: "Is one school's data separate from another's?" Two organisations are built;
// the owner of org B tries to read and change org A's records by id and through lists.
import { test, expect } from "@playwright/test";
import { createPerson, admitStudent } from "../../../api/organisations";
import { feeSetup, assignAndInvoice, payBody } from "../../../api/pb-fees";
import { makeGuardian } from "../../../api/pb-portal";
import { addStudent, call, createWorld, get, must, patch, post, PB_PASSWORD, uid, type World } from "../../../api/pb-helpers";

test.describe("Tenant isolation between two organisations", () => {
	test.describe.configure({ mode: "default" });
	let a: World;
	let b: World;
	let stuA: Awaited<ReturnType<typeof addStudent>>;
	let invoiceA: number;
	let paymentA: number;
	let bookA: number;
	let roleA: number;
	let stuB: Awaited<ReturnType<typeof addStudent>>;

	test.beforeAll(async () => {
		a = await createWorld({ modules: ["LIBRARY"] });
		b = await createWorld({ modules: ["LIBRARY"] });
		stuA = await addStudent(a);
		stuB = await addStudent(b);
		const fee = await feeSetup(a, { amounts: [700] });
		const inv = await assignAndInvoice(a, stuA.enrollmentId, fee);
		invoiceA = inv.invoices[0].id;
		paymentA = (await must(post("/api/v1/fees/payments", a.ownerToken, payBody(a, invoiceA, 200)), "pay A")).id;
		const book = await must(post("/api/v1/library/books", a.ownerToken, { title: `Iso ${uid("")}`, author: "A", publisher: "P", category: "Fiction", edition: "1" }), "book A");
		bookA = book.id;
		await must(post(`/api/v1/library/books/${bookA}/copies`, a.ownerToken, { campusId: a.campusId, accessionNumber: `ISO-${uid("")}`, acquiredDate: "2026-06-15", shelfLocation: "S" }), "copy A");
		roleA = (await must(post("/api/v1/roles", a.ownerToken, { name: `isorole-${uid("")}`, description: "x", permissionCodes: ["STUDENT_VIEW"], portal: false }), "role A")).id;
	});

	const notVisible = (status: number, what: string) => expect([403, 404], `${what}: HTTP ${status}`).toContain(status);

	test("by-id reads of org A records from org B's owner give 404/403 (student, person, invoice, payment, role, section register)", async () => {
		notVisible((await get(`/api/v1/students/${stuA.studentId}`, b.ownerToken)).status, "student");
		notVisible((await get(`/api/v1/persons/${stuA.personId}`, b.ownerToken)).status, "person");
		notVisible((await get(`/api/v1/fees/invoices/${invoiceA}`, b.ownerToken)).status, "invoice");
		notVisible((await get(`/api/v1/fees/payments/${paymentA}`, b.ownerToken)).status, "payment");
		notVisible((await get(`/api/v1/roles/${roleA}`, b.ownerToken)).status, "role");
		notVisible((await get(`/api/v1/attendance/sections/${a.sectionId}/register?date=2026-09-02`, b.ownerToken)).status, "section register");
	});

	test("receipt PDF of org A's payment is not downloadable by org B", async () => {
		const { API_BASE_URL } = await import("../../../api/client");
		const r = await fetch(`${API_BASE_URL}/api/v1/fees/payments/${paymentA}/receipt.pdf`, { headers: { Authorization: `Bearer ${b.ownerToken}` } });
		await r.arrayBuffer();
		notVisible(r.status, "receipt.pdf");
	});

	test("library: org B cannot read org A's book copies and does not see its books in the catalogue", async () => {
		const copies = await get(`/api/v1/library/books/${bookA}/copies`, b.ownerToken);
		expect(copies.status === 200 ? copies.body.length : 0, `copies leaked: HTTP ${copies.status}`).toBe(0);
		const list = await get("/api/v1/library/books", b.ownerToken);
		expect(list.body.map((x: any) => x.id)).not.toContain(bookA);
	});

	test("lists in org B never contain org A's rows", async () => {
		const students = await get("/api/v1/students", b.ownerToken);
		expect(students.body.map((s: any) => s.id)).not.toContain(stuA.studentId);
		const search = await get(`/api/v1/students/search?search=${encodeURIComponent(stuA.firstName)}`, b.ownerToken);
		expect(JSON.stringify(search.body)).not.toContain(stuA.admissionNumber);
		const classes = await get("/api/v1/school-classes", b.ownerToken);
		expect(classes.body.map((c: any) => c.id)).not.toContain(a.classId);
		const years = await get("/api/v1/academic-years", b.ownerToken);
		expect(years.body.map((y: any) => y.id)).not.toContain(a.yearId);
		const roles = await get("/api/v1/roles", b.ownerToken);
		expect(roles.body.map((r: any) => r.id)).not.toContain(roleA);
		const invs = await get(`/api/v1/fees/invoices?studentEnrollmentId=${stuA.enrollmentId}`, b.ownerToken);
		expect(invs.status === 200 ? invs.body : []).toHaveLength(0);
		const pays = await get(`/api/v1/fees/payments?studentEnrollmentId=${stuA.enrollmentId}`, b.ownerToken);
		expect(pays.status === 200 ? pays.body : []).toHaveLength(0);
		const overdue = await get("/api/v1/fees/invoices/overdue", b.ownerToken);
		expect(JSON.stringify(overdue.body)).not.toContain(stuA.admissionNumber);
	});

	test("org B cannot change org A's records (patch student, withdraw book, pay invoice, enrol or mark attendance in its section)", async () => {
		expect([403, 404]).toContain((await patch(`/api/v1/students/${stuA.studentId}`, b.ownerToken, { remarks: "hijack" })).status);
		expect([403, 404]).toContain((await post(`/api/v1/library/books/${bookA}/withdraw`, b.ownerToken)).status);
		const pay = await post("/api/v1/fees/payments", b.ownerToken, payBody(b, invoiceA, 100));
		expect(pay.status, "payment against another org's invoice").toBeGreaterThanOrEqual(400);
		expect(pay.status).toBeLessThan(500);
		const enrol = await post(`/api/v1/students/${stuB.studentId}/enrollments`, b.ownerToken, { academicYearId: b.yearId, sectionId: a.sectionId, rollNumber: 9, enrolledDate: "2026-06-01" });
		expect(enrol.status).toBeGreaterThanOrEqual(400);
		expect(enrol.status).toBeLessThan(500);
		const mark = await post(`/api/v1/attendance/sections/${a.sectionId}/register`, b.ownerToken, { attendanceDate: "2026-09-03", marks: [{ studentEnrollmentId: stuA.enrollmentId, status: "PRESENT", excused: false }] });
		expect(mark.status).toBeGreaterThanOrEqual(400);
		expect(mark.status).toBeLessThan(500);
		const check = await get(`/api/v1/fees/invoices/${invoiceA}`, a.ownerToken);
		expect(Number(check.body.amountPaid)).toBe(200);
		const stu = await get(`/api/v1/students/${stuA.studentId}`, a.ownerToken);
		expect(JSON.stringify(stu.body)).not.toContain("hijack");
	});

	test("the same admission number and the same email are usable in two organisations (uniqueness is per school)", async () => {
		const person = await createPerson(b.ownerToken, { firstName: "Twin", lastName: "Pb" });
		const twin = await admitStudent(b.ownerToken, { personId: person.id, admissionNumber: stuA.admissionNumber, admissionDate: "2026-06-01" });
		expect(twin.id).toBeTruthy();
		const sameEmail = `shared@${uid("")}.test`;
		const mk = async (w: World) => post("/api/v1/users/invite", w.ownerToken, { email: sameEmail });
		expect((await mk(a)).status).toBe(200);
		expect((await mk(b)).status).toBe(200);
	});

	test("an org A login cannot be used against org B's slug", async () => {
		const wrong = await call("POST", "/api/v1/auth/login", undefined, { organisationSlug: b.slug, email: a.ownerEmail, password: PB_PASSWORD });
		expect(wrong.status).toBe(401);
	});

	test("a guardian of org A cannot read a child of org B through /me/children", async () => {
		const parent = await makeGuardian(a, [stuA]);
		const own = await get(`/api/v1/me/children/${stuA.studentId}/fees`, parent.token);
		expect(own.status).toBe(200);
		notVisible((await get(`/api/v1/me/children/${stuB.studentId}/fees`, parent.token)).status, "guardian -> other org child");
	});

	test("a token for org A used on an org B record id (ids are global sequences) still finds nothing", async () => {
		notVisible((await get(`/api/v1/students/${stuB.studentId}`, a.ownerToken)).status, "A owner -> B student");
		notVisible((await get(`/api/v1/fees/invoices/${invoiceA + 100000}`, b.ownerToken)).status, "unknown invoice id");
	});

	test("org B's audit log and role list hold no org A rows", async () => {
		const audit = await get("/api/v1/audit-logs?size=200", b.ownerToken);
		expect(JSON.stringify(audit.body)).not.toContain(stuA.admissionNumber);
		const rows = await get(`/api/v1/audit-logs?entityType=Role&entityId=${roleA}`, b.ownerToken);
		expect(rows.body.content ?? []).toHaveLength(0);
	});
});
