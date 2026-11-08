// Playbook demo story 3: "Collecting fees" - fee structure (categories, installments) ->
// assign to student -> invoice -> record payment (cash, cheque, UPI, card, bank transfer) ->
// outstanding dues. Also waivers, adjustments, refunds, overdue invoices, opening-balance load.
import { test, expect } from "@playwright/test";
import { createGradeLevel, createSchoolClass, createSection } from "../../../api/organisations";
import { assignAndInvoice, feeSetup, payBody } from "../../../api/pb-fees";
import { addStudent, createWorld, csv, get, makeUser, must, post, upload, uid, type World } from "../../../api/pb-helpers";

let w: World;
let sectionCounter = 0;

test.beforeAll(async () => {
	w = await createWorld();
});

/** An isolated class+section so each test can have its own fee structure group for it. */
async function isolatedStudent() {
	const n = ++sectionCounter;
	const grade = await createGradeLevel(w.ownerToken, `FG${n}-${uid("")}`, 100 + n, "PRIMARY");
	const klass = await createSchoolClass(w.ownerToken, w.yearId, w.campusId, grade.id, `FC${n}-${uid("")}`);
	const section = await createSection(w.ownerToken, klass.id, "A", 40, "R");
	const student = await addStudent(w, { sectionId: section.id, roll: 1 });
	return { classId: klass.id, student };
}

async function invoiceOf(id: number) {
	return (await get(`/api/v1/fees/invoices/${id}`, w.ownerToken)).body;
}

test.describe("Story 3: collecting fees", () => {
	test("categories (general + transport) and a structure with installments can be defined", async () => {
		const general = await feeSetup(w, { classId: (await isolatedStudent()).classId, amounts: [500, 500, 500] });
		expect(general.installments).toHaveLength(3);
		const transport = await feeSetup(w, { classId: (await isolatedStudent()).classId, amounts: [300], categoryType: "TRANSPORT" });
		const cats = await get("/api/v1/fees/categories", w.ownerToken);
		expect(cats.status).toBe(200);
		const names = cats.body.map((c: any) => c.name);
		expect(names).toEqual(expect.arrayContaining([general.categoryName, transport.categoryName]));
		const struct = await get(`/api/v1/fees/structures/${general.structureId}`, w.ownerToken);
		expect(Number(struct.body.amount)).toBe(1500);
	});

	test("structure rules: zero amount and empty installments are rejected", async () => {
		const { classId } = await isolatedStudent();
		const fee = await feeSetup(w, { classId });
		const bad = await post("/api/v1/fees/structures", w.ownerToken, {
			feeStructureGroupId: fee.groupId,
			feeCategoryId: fee.categoryId,
			amount: 0,
			paymentFrequency: "MONTHLY",
			installments: [{ installmentNumber: 1, dueDate: "2026-08-01", amount: 0 }],
		});
		expect(bad.status).toBe(400);
		const none = await post("/api/v1/fees/structures", w.ownerToken, {
			feeStructureGroupId: fee.groupId,
			feeCategoryId: fee.categoryId,
			amount: 100,
			paymentFrequency: "MONTHLY",
			installments: [],
		});
		expect(none.status).toBe(400);
	});

	test("assign -> invoice -> payment settles the invoice and outstanding dues drop to zero", async () => {
		const { classId, student } = await isolatedStudent();
		const fee = await feeSetup(w, { classId, amounts: [1000, 500] });
		const a = await assignAndInvoice(w, student.enrollmentId, fee);
		expect(a.invoices.map((i) => i.totalAmount)).toEqual([1000, 500]);

		const inv0 = await invoiceOf(a.invoices[0].id);
		expect(Number(inv0.outstanding)).toBe(1000);
		expect(inv0.status).toBe("ISSUED");

		const part = await post("/api/v1/fees/payments", w.ownerToken, payBody(w, a.invoices[0].id, 400));
		expect(part.status).toBe(200);
		let after = await invoiceOf(a.invoices[0].id);
		expect(Number(after.outstanding)).toBe(600);
		expect(after.status).toBe("PARTIALLY_PAID");

		const rest = await post("/api/v1/fees/payments", w.ownerToken, payBody(w, a.invoices[0].id, 600));
		expect(rest.status).toBe(200);
		after = await invoiceOf(a.invoices[0].id);
		expect(Number(after.outstanding)).toBe(0);
		expect(after.status).toBe("PAID");

		const list = await get(`/api/v1/fees/invoices?studentEnrollmentId=${student.enrollmentId}`, w.ownerToken);
		const dues = list.body.reduce((sum: number, i: any) => sum + Number(i.outstanding), 0);
		expect(dues).toBe(500);
	});

	test("payment methods: cash, cheque, UPI, card and bank transfer are all accepted", async () => {
		const { classId, student } = await isolatedStudent();
		const fee = await feeSetup(w, { classId, amounts: [100, 100, 100, 100, 100] });
		const a = await assignAndInvoice(w, student.enrollmentId, fee);
		const methods: [string, Record<string, unknown>][] = [
			["CASH", {}],
			["CHEQUE", { paymentReference: "CHQ-1001", paymentBankName: "Pb Bank" }],
			["UPI", { paymentReference: "upi-ref-1" }],
			["CARD", { paymentReference: "card-ref-1" }],
			["BANK_TRANSFER", { paymentReference: "neft-ref-1" }],
		];
		for (let i = 0; i < methods.length; i++) {
			const [method, extra] = methods[i];
			const res = await post("/api/v1/fees/payments", w.ownerToken, payBody(w, a.invoices[i].id, 100, { paymentMethod: method, ...extra }));
			expect(res.status, `${method}: ${JSON.stringify(res.body)}`).toBe(200);
			expect(res.body.paymentMethod ?? method).toBe(method);
		}
		const list = await get(`/api/v1/fees/payments?studentEnrollmentId=${student.enrollmentId}`, w.ownerToken);
		expect(list.body).toHaveLength(5);
	});

	test("negative: a cheque without reference and bank is rejected", async () => {
		const { classId, student } = await isolatedStudent();
		const a = await assignAndInvoice(w, student.enrollmentId, await feeSetup(w, { classId, amounts: [100] }));
		const res = await post("/api/v1/fees/payments", w.ownerToken, payBody(w, a.invoices[0].id, 100, { paymentMethod: "CHEQUE" }));
		expect(res.status).toBe(400);
	});

	test("negative: a payment cannot exceed the invoice outstanding", async () => {
		const { classId, student } = await isolatedStudent();
		const a = await assignAndInvoice(w, student.enrollmentId, await feeSetup(w, { classId, amounts: [100] }));
		const res = await post("/api/v1/fees/payments", w.ownerToken, payBody(w, a.invoices[0].id, 150));
		expect(res.status).toBeGreaterThanOrEqual(400);
		expect(res.status).toBeLessThan(500);
		expect(Number((await invoiceOf(a.invoices[0].id)).outstanding)).toBe(100);
	});

	test("negative: an already-paid invoice cannot be paid again (no duplicate payment)", async () => {
		const { classId, student } = await isolatedStudent();
		const a = await assignAndInvoice(w, student.enrollmentId, await feeSetup(w, { classId, amounts: [100] }));
		expect((await post("/api/v1/fees/payments", w.ownerToken, payBody(w, a.invoices[0].id, 100))).status).toBe(200);
		const dup = await post("/api/v1/fees/payments", w.ownerToken, payBody(w, a.invoices[0].id, 100));
		expect(dup.status).toBeGreaterThanOrEqual(400);
		expect(dup.status).toBeLessThan(500);
		const inv = await invoiceOf(a.invoices[0].id);
		expect(Number(inv.amountPaid)).toBe(100);
	});

	test("negative: allocations must add up to the payment amount; zero/negative amounts rejected", async () => {
		const { classId, student } = await isolatedStudent();
		const a = await assignAndInvoice(w, student.enrollmentId, await feeSetup(w, { classId, amounts: [100] }));
		const mismatch = await post("/api/v1/fees/payments", w.ownerToken, { ...payBody(w, a.invoices[0].id, 100), amount: 90 });
		expect(mismatch.status).toBe(400);
		const zero = await post("/api/v1/fees/payments", w.ownerToken, payBody(w, a.invoices[0].id, 0));
		expect(zero.status).toBe(400);
		const neg = await post("/api/v1/fees/payments", w.ownerToken, payBody(w, a.invoices[0].id, -10));
		expect(neg.status).toBe(400);
	});

	test("assigning the same structure twice, or invoicing an installment twice, is a conflict", async () => {
		const { classId, student } = await isolatedStudent();
		const fee = await feeSetup(w, { classId, amounts: [100] });
		const a = await assignAndInvoice(w, student.enrollmentId, fee);
		const again = await post("/api/v1/fees/assignments", w.ownerToken, { studentEnrollmentId: student.enrollmentId, feeStructureId: fee.structureId });
		expect(again.status).toBe(409);
		const reInvoice = await post(`/api/v1/fees/assignments/${a.assignmentId}/invoices`, w.ownerToken, { feeStructureInstallmentId: fee.installments[0].id });
		expect(reInvoice.status).toBe(409);
	});

	test("a waiver reduces what is outstanding; over-waiving and missing reason are rejected", async () => {
		const { classId, student } = await isolatedStudent();
		const a = await assignAndInvoice(w, student.enrollmentId, await feeSetup(w, { classId, amounts: [1000] }));
		const id = a.invoices[0].id;
		const waiver = await post("/api/v1/fees/waivers", w.ownerToken, { invoiceId: id, amount: 300, reason: "Staff child", approvedBy: w.ownerUserId, waiverDate: "2026-09-02" });
		expect(waiver.status).toBe(200);
		expect(Number((await invoiceOf(id)).outstanding)).toBe(700);
		const tooMuch = await post("/api/v1/fees/waivers", w.ownerToken, { invoiceId: id, amount: 5000, reason: "x", approvedBy: w.ownerUserId, waiverDate: "2026-09-02" });
		expect(tooMuch.status).toBe(400);
		const noReason = await post("/api/v1/fees/waivers", w.ownerToken, { invoiceId: id, amount: 10, reason: "", approvedBy: w.ownerUserId, waiverDate: "2026-09-02" });
		expect(noReason.status).toBe(400);
		const list = await get(`/api/v1/fees/waivers?invoiceId=${id}`, w.ownerToken);
		expect(list.body).toHaveLength(1);
	});

	test("an adjustment can raise or lower the invoice, but not below what was already paid", async () => {
		const { classId, student } = await isolatedStudent();
		const a = await assignAndInvoice(w, student.enrollmentId, await feeSetup(w, { classId, amounts: [1000] }));
		const id = a.invoices[0].id;
		await must(post("/api/v1/fees/payments", w.ownerToken, payBody(w, id, 600)), "pay");
		const down = await post("/api/v1/fees/adjustments", w.ownerToken, { invoiceId: id, amount: -200, reason: "Correction", approvedBy: w.ownerUserId, adjustmentDate: "2026-09-02" });
		expect(down.status).toBe(200);
		expect(Number((await invoiceOf(id)).totalAmount)).toBe(800);
		const up = await post("/api/v1/fees/adjustments", w.ownerToken, { invoiceId: id, amount: 50, reason: "Late fee", approvedBy: w.ownerUserId, adjustmentDate: "2026-09-02" });
		expect(up.status).toBe(200);
		expect(Number((await invoiceOf(id)).totalAmount)).toBe(850);
		const tooLow = await post("/api/v1/fees/adjustments", w.ownerToken, { invoiceId: id, amount: -500, reason: "Too much", approvedBy: w.ownerUserId, adjustmentDate: "2026-09-02" });
		expect(tooLow.status).toBe(400);
	});

	test("a refund returns money against a payment, but never more than was paid", async () => {
		const { classId, student } = await isolatedStudent();
		const a = await assignAndInvoice(w, student.enrollmentId, await feeSetup(w, { classId, amounts: [1000] }));
		const id = a.invoices[0].id;
		const pay = await must(post("/api/v1/fees/payments", w.ownerToken, payBody(w, id, 500)), "pay");
		const refund = await post("/api/v1/fees/refunds", w.ownerToken, { paymentId: pay.id, invoiceId: id, amount: 200, reason: "Over-collected", approvedBy: w.ownerUserId, refundDate: "2026-09-03" });
		expect(refund.status).toBe(200);
		const inv = await invoiceOf(id);
		expect(Number(inv.amountPaid)).toBe(300);
		const tooMuch = await post("/api/v1/fees/refunds", w.ownerToken, { paymentId: pay.id, invoiceId: id, amount: 400, reason: "x", approvedBy: w.ownerUserId, refundDate: "2026-09-03" });
		expect(tooMuch.status).toBe(400);
	});

	test("a discount at assignment needs FEE_DISCOUNT_APPROVE; a fees collector without it gets 403", async () => {
		const { classId, student } = await isolatedStudent();
		const fee = await feeSetup(w, { classId, amounts: [1000] });
		const collector = await makeUser(w, "collector", ["STUDENT_VIEW", "FEE_VIEW", "FEE_COLLECT", "FEE_INVOICE_MANAGE", "FEE_ASSIGNMENT_MANAGE"]);
		const denied = await post("/api/v1/fees/assignments", collector.token, {
			studentEnrollmentId: student.enrollmentId,
			feeStructureId: fee.structureId,
			discountAmount: 100,
			discountReason: "Sibling",
			approvedBy: w.ownerUserId,
		});
		expect(denied.status).toBe(403);
		const ok = await post("/api/v1/fees/assignments", w.ownerToken, {
			studentEnrollmentId: student.enrollmentId,
			feeStructureId: fee.structureId,
			discountAmount: 100,
			discountReason: "Sibling",
			approvedBy: w.ownerUserId,
		});
		expect(ok.status).toBe(200);
	});

	test("permissions: no FEE_COLLECT -> 403 on payments; no FEE_WAIVER_APPROVE -> 403 on waivers; no FEE_REFUND_APPROVE -> 403", async () => {
		const { classId, student } = await isolatedStudent();
		const a = await assignAndInvoice(w, student.enrollmentId, await feeSetup(w, { classId, amounts: [1000] }));
		const id = a.invoices[0].id;
		const viewer = await makeUser(w, "feeviewer", ["FEE_VIEW", "STUDENT_VIEW"]);
		expect((await post("/api/v1/fees/payments", viewer.token, payBody(w, id, 100))).status).toBe(403);
		expect((await post("/api/v1/fees/waivers", viewer.token, { invoiceId: id, amount: 10, reason: "r", approvedBy: w.ownerUserId, waiverDate: "2026-09-02" })).status).toBe(403);
		expect((await post("/api/v1/fees/refunds", viewer.token, { paymentId: 1, invoiceId: id, amount: 10, reason: "r", approvedBy: w.ownerUserId, refundDate: "2026-09-02" })).status).toBe(403);
		expect((await get(`/api/v1/fees/invoices/${id}`, viewer.token)).status).toBe(200);
		const nobody = await makeUser(w, "nofee", []);
		expect((await get(`/api/v1/fees/invoices/${id}`, nobody.token)).status).toBe(403);
	});

	test("a bounced cheque puts the money back on the invoice", async () => {
		const { classId, student } = await isolatedStudent();
		const a = await assignAndInvoice(w, student.enrollmentId, await feeSetup(w, { classId, amounts: [500] }));
		const id = a.invoices[0].id;
		const pay = await must(post("/api/v1/fees/payments", w.ownerToken, payBody(w, id, 500, { paymentMethod: "CHEQUE", paymentReference: "CHQ-9", paymentBankName: "Pb Bank" })), "pay");
		expect(Number((await invoiceOf(id)).outstanding)).toBe(0);
		const bounce = await post(`/api/v1/fees/payments/${pay.id}/bounce`, w.ownerToken, { reason: "Insufficient funds" });
		expect(bounce.status).toBe(200);
		expect(Number((await invoiceOf(id)).outstanding)).toBe(500);
	});

	test("overdue invoices: past-due unpaid invoices are listed, paid ones and future-dated ones are not", async () => {
		const { classId, student } = await isolatedStudent();
		const fee = await feeSetup(w, { classId, amounts: [100, 100], firstDue: "2026-07-01" });
		const a = await assignAndInvoice(w, student.enrollmentId, fee);
		await must(post("/api/v1/fees/payments", w.ownerToken, payBody(w, a.invoices[1].id, 100)), "pay second");
		const future = await feeSetup(w, { classId, amounts: [100], firstDue: "2027-02-01", groupId: fee.groupId });
		const f = await assignAndInvoice(w, student.enrollmentId, future);
		const res = await get(`/api/v1/fees/invoices/overdue?studentEnrollmentId=${student.enrollmentId}`, w.ownerToken);
		expect(res.status).toBe(200);
		const ids = res.body.map((i: any) => i.id);
		expect(ids).toContain(a.invoices[0].id);
		expect(ids).not.toContain(a.invoices[1].id);
		expect(ids).not.toContain(f.invoices[0].id);
	});

	test("bulk invoice generation: dry run reports, real run creates invoices for a whole class", async () => {
		const { classId, student } = await isolatedStudent();
		const fee = await feeSetup(w, { classId, amounts: [100] });
		await must(post("/api/v1/fees/assignments", w.ownerToken, { studentEnrollmentId: student.enrollmentId, feeStructureId: fee.structureId }), "assign");
		const dry = await post("/api/v1/fees/invoices/bulk-generate", w.ownerToken, { academicYearId: w.yearId, schoolClassId: classId, dueOnOrBefore: "2026-12-31", dryRun: true });
		expect(dry.status).toBe(200);
		const none = await get(`/api/v1/fees/invoices?studentEnrollmentId=${student.enrollmentId}`, w.ownerToken);
		expect(none.body).toHaveLength(0);
		const real = await post("/api/v1/fees/invoices/bulk-generate", w.ownerToken, { academicYearId: w.yearId, schoolClassId: classId, dueOnOrBefore: "2026-12-31", dryRun: false });
		expect(real.status).toBe(200);
		const made = await get(`/api/v1/fees/invoices?studentEnrollmentId=${student.enrollmentId}`, w.ownerToken);
		expect(made.body).toHaveLength(1);
	});

	test("bulk import of fee structures from CSV (validate-only, then real)", async () => {
		const { classId } = await isolatedStudent();
		const base = await feeSetup(w, { classId, amounts: [100] }); // gives the class a structure group
		const catName = `ImpCat ${uid("")}`;
		await must(post("/api/v1/fees/categories", w.ownerToken, { code: uid("ic").toUpperCase(), name: catName, categoryType: "GENERAL", requiredAtAdmission: false }), "cat");
		// Class display names are unique per run (see isolatedStudent), look it up for the CSV.
		const classes = await get(`/api/v1/school-classes?academicYearId=${w.yearId}`, w.ownerToken);
		const className = (classes.body.content ?? classes.body).find((c: any) => c.id === classId).displayName;
		const body = csv(["academicYear", "className", "feeCategory", "amount", "installmentDueDate", "paymentFrequency"], [[w.yearName, className, catName, 750, "2026-10-01", "ONE_SHOT"]]);
		const dry = await upload("/api/v1/fees/structures/import?validateOnly=true", w.ownerToken, body);
		expect(dry.status).toBe(200);
		expect(dry.body[0].status).toBe("VALID");
		const real = await upload("/api/v1/fees/structures/import", w.ownerToken, body);
		expect(real.status).toBe(200);
		expect(real.body[0].status).toBe("IMPORTED");
		const structs = await get(`/api/v1/fees/structures?feeStructureGroupId=${base.groupId}`, w.ownerToken);
		expect(structs.body).toHaveLength(2);
	});

	test("opening-balance bulk load creates an invoice carrying the legacy due; unknown student reported per row", async () => {
		const { classId, student } = await isolatedStudent();
		await feeSetup(w, { classId, amounts: [100] }); // group must exist for the class
		const body = csv(["studentAdmissionNumber", "amount", "asOfDate"], [[student.admissionNumber, 2500, "2026-06-30"], ["NO-SUCH-ADM", 100, "2026-06-30"]]);
		const res = await upload("/api/v1/fees/opening-balances/import", w.ownerToken, body);
		expect(res.status).toBe(200);
		expect(res.body[0].status).toBe("IMPORTED");
		expect(res.body[1].status).toBe("ERROR");
		const inv = await get(`/api/v1/fees/invoices?studentEnrollmentId=${student.enrollmentId}`, w.ownerToken);
		expect(inv.body.some((i: any) => Number(i.outstanding) === 2500)).toBe(true);
		const denied = await makeUser(w, "obviewer", ["FEE_VIEW"]);
		expect((await upload("/api/v1/fees/opening-balances/import", denied.token, body)).status).toBe(403);
	});
});
