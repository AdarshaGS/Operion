// Fee-flow arrange helpers for the playbook specs (category -> structure -> assignment ->
// invoice). Every call goes through must(), so a failure here is a test-setup problem.
import { addDays, must, post, uid, type World } from "./pb-helpers";

export interface FeeInstallment {
	id: number;
	installmentNumber: number;
	dueDate: string;
	amount: number;
}

export interface FeeSetup {
	categoryId: number;
	categoryName: string;
	groupId: number;
	structureId: number;
	installments: FeeInstallment[];
}

/** One category + group + structure for the world's class. Defaults to two installments (due in the past by default). */
export async function feeSetup(
	w: World,
	opts: { classId?: number; amounts?: number[]; firstDue?: string; categoryType?: "GENERAL" | "TRANSPORT"; groupId?: number } = {},
): Promise<FeeSetup> {
	const amounts = opts.amounts ?? [1000, 1000];
	const firstDue = opts.firstDue ?? "2026-07-10";
	const code = uid("fc").toUpperCase();
	const categoryName = `Cat ${code}`;
	const category = await must(
		post("/api/v1/fees/categories", w.ownerToken, { code, name: categoryName, description: "pb", categoryType: opts.categoryType ?? "GENERAL", requiredAtAdmission: false }),
		"fee category",
	);
	let groupId = opts.groupId;
	if (!groupId) {
		const group = await must(
			post("/api/v1/fees/structure-groups", w.ownerToken, { name: `Group ${code}`, academicYearId: w.yearId, schoolClassId: opts.classId ?? w.classId }),
			"structure group",
		);
		groupId = group.id;
	}
	const total = amounts.reduce((a, b) => a + b, 0);
	const structure = await must(
		post("/api/v1/fees/structures", w.ownerToken, {
			feeStructureGroupId: groupId,
			feeCategoryId: category.id,
			amount: total,
			paymentFrequency: "QUARTERLY",
			installments: amounts.map((amount, i) => ({ installmentNumber: i + 1, dueDate: addDays(firstDue, i * 30), amount })),
		}),
		"fee structure",
	);
	return { categoryId: category.id, categoryName, groupId: groupId!, structureId: structure.id, installments: structure.installments };
}

export interface Assigned {
	assignmentId: number;
	invoices: { id: number; totalAmount: number; installmentId: number }[];
}

/** Assign the structure to an enrollment and raise one invoice per installment. */
export async function assignAndInvoice(w: World, enrollmentId: number, fee: FeeSetup, extra: Record<string, unknown> = {}): Promise<Assigned> {
	const assignment = await must(
		post("/api/v1/fees/assignments", w.ownerToken, { studentEnrollmentId: enrollmentId, feeStructureId: fee.structureId, ...extra }),
		"assign fee",
	);
	const invoices: Assigned["invoices"] = [];
	for (const inst of fee.installments) {
		const invoice = await must(post(`/api/v1/fees/assignments/${assignment.id}/invoices`, w.ownerToken, { feeStructureInstallmentId: inst.id }), "invoice");
		invoices.push({ id: invoice.id, totalAmount: Number(invoice.totalAmount), installmentId: inst.id });
	}
	return { assignmentId: assignment.id, invoices };
}

export function payBody(w: World, invoiceId: number, amount: number, extra: Record<string, unknown> = {}) {
	return {
		academicYearId: w.yearId,
		amount,
		paymentMethod: "CASH",
		paymentDate: "2026-09-01",
		allocations: [{ invoiceId, amount }],
		...extra,
	};
}
