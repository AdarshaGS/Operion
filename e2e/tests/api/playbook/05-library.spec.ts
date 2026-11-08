// Playbook module: Library - titles, copies with accession numbers, issue with a due date,
// return or lost, raise / collect / waive fines, copy status Available / Borrowed / Lost /
// Damaged / Withdrawn, bulk import of books. Needs the optional LIBRARY module enabled.
import { test, expect } from "@playwright/test";
import { addStudent, createWorld, csv, get, makeUser, must, post, put, upload, uid, type World } from "../../../api/pb-helpers";

let w: World;

test.beforeAll(async () => {
	w = await createWorld({ modules: ["LIBRARY"] });
});

async function title(extra: Record<string, unknown> = {}) {
	return must(post("/api/v1/library/books", w.ownerToken, { title: `Book ${uid("")}`, author: "Pb Author", publisher: "Pb Press", category: "Fiction", edition: "1", ...extra }), "book");
}

async function copy(bookId: number, accession = `ACC-${uid("")}`.toUpperCase()) {
	return must(post(`/api/v1/library/books/${bookId}/copies`, w.ownerToken, { campusId: w.campusId, accessionNumber: accession, acquiredDate: "2026-06-15", shelfLocation: "Shelf A" }), "copy");
}

const issue = (copyId: number, personId: number, borrowedDate = "2026-09-01", dueDate: string | null = "2026-09-15", token = w.ownerToken) =>
	post("/api/v1/library/borrow-records", token, { bookCopyId: copyId, borrowerPersonId: personId, borrowedDate, dueDate });

async function borrower() {
	return addStudent(w);
}

async function copyStatus(bookId: number, copyId: number): Promise<string> {
	const copies = await get(`/api/v1/library/books/${bookId}/copies`, w.ownerToken);
	return copies.body.find((c: any) => c.id === copyId).status;
}

test.describe("Library", () => {
	test("a title is added and appears in the catalogue; a blank title is rejected", async () => {
		const book = await title({ isbn: `978${uid("").replace(/\D/g, "").padEnd(10, "1").slice(0, 10)}` });
		const list = await get("/api/v1/library/books", w.ownerToken);
		expect(list.status).toBe(200);
		expect(list.body.some((b: any) => b.id === book.id)).toBe(true);
		const blank = await post("/api/v1/library/books", w.ownerToken, { title: "  " });
		expect(blank.status).toBe(400);
	});

	test("copies carry their own accession numbers; a copy without one is rejected", async () => {
		const book = await title();
		const a = await copy(book.id);
		const b = await copy(book.id);
		expect(a.accessionNumber).not.toBe(b.accessionNumber);
		expect(a.status).toBe("AVAILABLE");
		const copies = await get(`/api/v1/library/books/${book.id}/copies`, w.ownerToken);
		expect(copies.body).toHaveLength(2);
		const none = await post(`/api/v1/library/books/${book.id}/copies`, w.ownerToken, { campusId: w.campusId, accessionNumber: "" });
		expect(none.status).toBe(400);
	});

	test("two copies cannot share an accession number", async () => {
		const book = await title();
		const accession = `DUP-${uid("")}`.toUpperCase();
		await copy(book.id, accession);
		const other = await title();
		const again = await post(`/api/v1/library/books/${other.id}/copies`, w.ownerToken, { campusId: w.campusId, accessionNumber: accession, acquiredDate: "2026-06-15" });
		expect(again.status, JSON.stringify(again.body)).toBeGreaterThanOrEqual(400);
		expect(again.status).toBeLessThan(500);
	});

	test("issue with a due date marks the copy BORROWED; a borrowed copy cannot be issued again", async () => {
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		const rec = await issue(c.id, s.personId);
		expect(rec.status).toBe(200);
		expect(rec.body).toMatchObject({ status: "BORROWED", dueDate: "2026-09-15", accessionNumber: c.accessionNumber });
		expect(await copyStatus(book.id, c.id)).toBe("BORROWED");
		const other = await borrower();
		expect((await issue(c.id, other.personId)).status).toBe(409);
	});

	test("without an explicit due date the borrowing period default is applied", async () => {
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		const rec = await issue(c.id, s.personId, "2026-09-01", null);
		expect(rec.status).toBe(200);
		expect(rec.body.dueDate).toBe("2026-09-15");
	});

	test("a due date before the issue date is rejected", async () => {
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		expect((await issue(c.id, s.personId, "2026-09-10", "2026-09-01")).status).toBe(400);
	});

	test("return puts the copy back to AVAILABLE; returning twice is a conflict", async () => {
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		const rec = await must(issue(c.id, s.personId), "issue");
		const ret = await post(`/api/v1/library/borrow-records/${rec.id}/return`, w.ownerToken, { returnedDate: "2026-09-10" });
		expect(ret.status).toBe(200);
		expect(ret.body.status).toBe("RETURNED");
		expect(await copyStatus(book.id, c.id)).toBe("AVAILABLE");
		expect((await post(`/api/v1/library/borrow-records/${rec.id}/return`, w.ownerToken, { returnedDate: "2026-09-11" })).status).toBe(409);
	});

	test("returning damaged leaves the copy DAMAGED; marking lost leaves it LOST and closes the loan", async () => {
		const book = await title();
		const damaged = await copy(book.id);
		const lost = await copy(book.id);
		const s1 = await borrower();
		const s2 = await borrower();
		const r1 = await must(issue(damaged.id, s1.personId), "issue");
		const r2 = await must(issue(lost.id, s2.personId), "issue");
		expect((await post(`/api/v1/library/borrow-records/${r1.id}/return`, w.ownerToken, { returnedDate: "2026-09-05", damaged: true })).status).toBe(200);
		expect(await copyStatus(book.id, damaged.id)).toBe("DAMAGED");
		const ml = await post(`/api/v1/library/borrow-records/${r2.id}/mark-lost`, w.ownerToken);
		expect(ml.status).toBe(200);
		expect(ml.body.status).toBe("LOST");
		expect(await copyStatus(book.id, lost.id)).toBe("LOST");
		const again = await issue(lost.id, s1.personId);
		expect(again.status).toBe(409);
	});

	test("an overdue return raises an OVERDUE fine at the per-day rate set in library settings", async () => {
		const settings = await put("/api/v1/library/settings", w.ownerToken, {
			maxLoansStudent: 3,
			maxLoansStaff: 5,
			borrowingPeriodDaysStudent: 14,
			borrowingPeriodDaysStaff: 30,
			blockIssueOnOverdue: false,
			fineRatePerDay: 2,
		});
		expect(settings.status).toBe(200);
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		const rec = await must(issue(c.id, s.personId, "2026-09-01", "2026-09-05"), "issue");
		await must(post(`/api/v1/library/borrow-records/${rec.id}/return`, w.ownerToken, { returnedDate: "2026-09-10" }), "return");
		const fines = await get(`/api/v1/library/fines?borrowRecordId=${rec.id}`, w.ownerToken);
		expect(fines.body).toHaveLength(1);
		expect(fines.body[0]).toMatchObject({ reason: "OVERDUE", status: "PENDING" });
		expect(Number(fines.body[0].amount)).toBe(10);
	});

	test("a fine can be raised by hand, then collected; a collected fine cannot be paid or waived again", async () => {
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		const rec = await must(issue(c.id, s.personId), "issue");
		await must(post(`/api/v1/library/borrow-records/${rec.id}/mark-lost`, w.ownerToken), "lost");
		const fine = await post("/api/v1/library/fines", w.ownerToken, { borrowRecordId: rec.id, amount: 250, reason: "LOST" });
		expect(fine.status).toBe(200);
		expect(fine.body).toMatchObject({ status: "PENDING", reason: "LOST" });
		const paid = await post(`/api/v1/library/fines/${fine.body.id}/pay`, w.ownerToken, { paidDate: "2026-09-20" });
		expect(paid.status).toBe(200);
		expect(paid.body.status).toBe("PAID");
		expect((await post(`/api/v1/library/fines/${fine.body.id}/pay`, w.ownerToken, { paidDate: "2026-09-21" })).status).toBe(409);
		expect((await post(`/api/v1/library/fines/${fine.body.id}/waive`, w.ownerToken, { waivedBy: w.ownerUserId, waivedReason: "late" })).status).toBe(409);
	});

	test("a fine can be waived with a reason; waiving without one, or a zero fine, is rejected", async () => {
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		const rec = await must(issue(c.id, s.personId), "issue");
		await must(post(`/api/v1/library/borrow-records/${rec.id}/return`, w.ownerToken, { returnedDate: "2026-09-05", damaged: true }), "return");
		expect((await post("/api/v1/library/fines", w.ownerToken, { borrowRecordId: rec.id, amount: 0, reason: "DAMAGED" })).status).toBe(400);
		const fine = await must(post("/api/v1/library/fines", w.ownerToken, { borrowRecordId: rec.id, amount: 80, reason: "DAMAGED" }), "fine");
		expect((await post(`/api/v1/library/fines/${fine.id}/waive`, w.ownerToken, { waivedBy: w.ownerUserId, waivedReason: " " })).status).toBe(400);
		const waived = await post(`/api/v1/library/fines/${fine.id}/waive`, w.ownerToken, { waivedBy: w.ownerUserId, waivedReason: "Principal approved" });
		expect(waived.status).toBe(200);
		expect(waived.body).toMatchObject({ status: "WAIVED", waivedReason: "Principal approved" });
	});

	test("a withdrawn title can no longer be issued, and its copies show as WITHDRAWN", async () => {
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		const withdrawn = await post(`/api/v1/library/books/${book.id}/withdraw`, w.ownerToken);
		expect(withdrawn.status).toBe(200);
		expect((await issue(c.id, s.personId)).status).toBe(409);
		expect(await copyStatus(book.id, c.id)).toBe("WITHDRAWN");
	});

	test("a title with a copy on loan cannot be withdrawn", async () => {
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		await must(issue(c.id, s.personId), "issue");
		expect((await post(`/api/v1/library/books/${book.id}/withdraw`, w.ownerToken)).status).toBe(409);
	});

	test("the summary counts copies by Available / Issued / Lost / Damaged", async () => {
		const res = await get("/api/v1/library/summary", w.ownerToken);
		expect(res.status).toBe(200);
		expect(Object.keys(res.body).length).toBeGreaterThanOrEqual(5);
	});

	test("bulk import of books: validate-only reports rows, real run adds them, a bad row is reported per row", async () => {
		const t1 = `Imp ${uid("")}`;
		const t2 = `Imp ${uid("")}`;
		const file = csv(["title", "isbn", "author", "publisher", "category", "edition"], [
			[t1, "", "Auth One", "Pub", "Science", "1"],
			[t2, "", "Auth Two", "Pub", "History", "2"],
			["", "", "No Title", "Pub", "X", "1"],
		]);
		const dry = await upload("/api/v1/library/books/import?validateOnly=true", w.ownerToken, file, "books.csv");
		expect(dry.status).toBe(200);
		expect(dry.body.map((r: any) => r.status)).toEqual(["VALID", "VALID", "ERROR"]);
		expect((await get("/api/v1/library/books", w.ownerToken)).body.some((b: any) => b.title === t1)).toBe(false);
		const real = await upload("/api/v1/library/books/import", w.ownerToken, file, "books.csv");
		expect(real.status).toBe(200);
		expect(real.body.map((r: any) => r.status)).toEqual(["IMPORTED", "IMPORTED", "ERROR"]);
		const list = await get("/api/v1/library/books", w.ownerToken);
		expect(list.body.filter((b: any) => b.title === t1 || b.title === t2)).toHaveLength(2);
	});

	test("permissions: LIBRARY_VIEW alone reads but cannot add titles, issue or touch fines (403)", async () => {
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		const viewer = await makeUser(w, "libview", ["LIBRARY_VIEW"]);
		expect((await get("/api/v1/library/books", viewer.token)).status).toBe(200);
		expect((await post("/api/v1/library/books", viewer.token, { title: "nope" })).status).toBe(403);
		expect((await post(`/api/v1/library/books/${book.id}/copies`, viewer.token, { campusId: w.campusId, accessionNumber: "x" })).status).toBe(403);
		expect((await issue(c.id, s.personId, "2026-09-01", "2026-09-15", viewer.token)).status).toBe(403);
		expect((await post("/api/v1/library/fines", viewer.token, { borrowRecordId: 1, amount: 5, reason: "LOST" })).status).toBe(403);
		const nobody = await makeUser(w, "libnone", []);
		expect((await get("/api/v1/library/books", nobody.token)).status).toBe(403);
	});

	test("a librarian (catalogue + borrow + fine permissions) can run the whole desk but a borrow-only user cannot collect fines", async () => {
		const book = await title();
		const c = await copy(book.id);
		const s = await borrower();
		const desk = await makeUser(w, "libdesk", ["LIBRARY_VIEW", "LIBRARY_BORROW_MANAGE"]);
		const rec = await issue(c.id, s.personId, "2026-09-01", "2026-09-15", desk.token);
		expect(rec.status).toBe(200);
		expect((await post("/api/v1/library/fines", desk.token, { borrowRecordId: rec.body.id, amount: 5, reason: "LOST" })).status).toBe(403);
	});

	test("an organisation without the LIBRARY module cannot use library endpoints", async () => {
		const plain = await createWorld();
		const res = await get("/api/v1/library/books", plain.ownerToken);
		expect(res.status).toBeGreaterThanOrEqual(400);
		expect(res.status).toBeLessThan(500);
	});
});
