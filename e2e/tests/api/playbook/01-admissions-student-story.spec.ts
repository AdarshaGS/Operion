// Playbook demo story 1: "New student joins" - enquiry -> convert to student -> student
// record -> guardians & documents. Also the Admissions / Student module claims.
import { test, expect } from "@playwright/test";
import { addStudent, createWorld, get, makeUser, must, patch, post, put, uid, type World } from "../../../api/pb-helpers";
import { createPerson } from "../../../api/organisations";

let w: World;

test.beforeAll(async () => {
	w = await createWorld();
});

async function newApplicant(label = "Enq") {
	const person = await createPerson(w.ownerToken, { firstName: `${label}${uid("")}`, lastName: "Pb", dateOfBirth: "2016-02-02", gender: "MALE" });
	const applicant = await must(
		post("/api/v1/applicants", w.ownerToken, { personId: person.id, inquiryDate: "2026-05-20", source: "Walk-in", notes: "pb" }),
		"create applicant",
	);
	return { person, applicant };
}

test.describe("Story 1: new student joins", () => {
	test("enquiry is recorded and listed with status counts", async () => {
		const { applicant } = await newApplicant();
		expect(applicant.status).toBe("INQUIRY");
		const list = await get(`/api/v1/applicants?status=INQUIRY&size=100`, w.ownerToken);
		expect(list.status).toBe(200);
		expect(list.body.content.some((a: any) => a.id === applicant.id)).toBe(true);
		const counts = await get("/api/v1/applicants/status-counts", w.ownerToken);
		expect(counts.status).toBe(200);
	});

	test("enquiry -> review -> convert creates a student record linked back to the applicant", async () => {
		const { applicant } = await newApplicant();
		const review = await post(`/api/v1/applicants/${applicant.id}/start-review`, w.ownerToken);
		expect(review.status).toBe(200);
		const convert = await post(`/api/v1/applicants/${applicant.id}/convert`, w.ownerToken, {
			admissionNumber: `ADM-${uid("")}`.toUpperCase(),
			admissionDate: "2026-06-01",
		});
		expect(convert.status).toBe(200);
		const studentId = convert.body.id;
		expect(studentId).toBeTruthy();

		const reread = await get(`/api/v1/applicants?status=CONVERTED&size=100`, w.ownerToken);
		const row = reread.body.content.find((a: any) => a.id === applicant.id);
		expect(row?.status).toBe("CONVERTED");
		expect(row?.studentId).toBe(studentId);

		const student = await get(`/api/v1/students/${studentId}`, w.ownerToken);
		expect(student.status).toBe(200);
		expect(student.body.personId ?? student.body.person?.id).toBe(applicant.personId);
	});

	test("an applicant cannot be converted twice", async () => {
		const { applicant } = await newApplicant();
		const body = { admissionNumber: `ADM-${uid("")}`.toUpperCase(), admissionDate: "2026-06-01" };
		expect((await post(`/api/v1/applicants/${applicant.id}/convert`, w.ownerToken, body)).status).toBe(200);
		const again = await post(`/api/v1/applicants/${applicant.id}/convert`, w.ownerToken, { ...body, admissionNumber: `ADM-${uid("")}`.toUpperCase() });
		expect(again.status).toBeGreaterThanOrEqual(400);
		expect(again.status).toBeLessThan(500);
	});

	test("an enquiry can be rejected with a reason and then cannot be converted", async () => {
		const { applicant } = await newApplicant();
		const rejected = await post(`/api/v1/applicants/${applicant.id}/reject`, w.ownerToken, { reason: "No seats" });
		expect(rejected.status).toBe(200);
		expect(rejected.body.status).toBe("REJECTED");
		const convert = await post(`/api/v1/applicants/${applicant.id}/convert`, w.ownerToken, {
			admissionNumber: `ADM-${uid("")}`.toUpperCase(),
			admissionDate: "2026-06-01",
		});
		expect(convert.status).toBeGreaterThanOrEqual(400);
		expect(convert.status).toBeLessThan(500);
	});

	test("admission document checklist can be recorded on the enquiry", async () => {
		const { applicant } = await newApplicant();
		const put1 = await put(`/api/v1/applicants/${applicant.id}/documents`, w.ownerToken, [{ docType: "BIRTH_CERTIFICATE", received: true }]);
		expect(put1.status).toBe(200);
		const list = await get(`/api/v1/applicants/${applicant.id}/documents`, w.ownerToken);
		expect(list.status).toBe(200);
		expect(list.body.find((d: any) => d.docType === "BIRTH_CERTIFICATE")?.received).toBe(true);
	});

	test("public enquiry form (no login) lands as an applicant the school can see", async () => {
		const opts = await get(`/api/v1/public/orgs/${w.slug}/admission-inquiries/form-options`);
		expect(opts.status).toBe(200);
		const first = `Pub${uid("")}`;
		const submit = await post(`/api/v1/public/orgs/${w.slug}/admission-inquiries`, undefined, {
			firstName: first,
			lastName: "Parent",
			dateOfBirth: "2017-03-03",
			gender: "FEMALE",
			guardianName: "Pat Parent",
			guardianPhone: "9876543210",
			guardianEmail: "pat@example.org",
			website: "",
		});
		expect(submit.status).toBeLessThan(300);
		const list = await get("/api/v1/applicants?size=200", w.ownerToken);
		expect(list.body.content.some((a: any) => a.firstName === first)).toBe(true);
	});

	test("student record can be fetched, searched and patched", async () => {
		const s = await addStudent(w, { firstName: `Srch${uid("")}` });
		expect((await get(`/api/v1/students/${s.studentId}`, w.ownerToken)).status).toBe(200);
		const search = await get(`/api/v1/students/search?q=${encodeURIComponent(s.firstName)}`, w.ownerToken);
		expect(search.status).toBe(200);
		const patched = await patch(`/api/v1/students/${s.studentId}`, w.ownerToken, { remarks: "updated by pb" });
		expect(patched.status).toBe(200);
	});

	test("guardian is created, linked to the student and visible from both sides", async () => {
		const s = await addStudent(w);
		const gp = await createPerson(w.ownerToken, { firstName: `Gd${uid("")}`, lastName: "Pb", phone: "9000000001" });
		const guardian = await must(post("/api/v1/guardians", w.ownerToken, { personId: gp.id, occupation: "Engineer" }), "create guardian");
		const link = await post(`/api/v1/students/${s.studentId}/guardians`, w.ownerToken, {
			guardianId: guardian.id,
			relationshipType: "MOTHER",
			primaryGuardian: true,
			emergencyContact: true,
			canPickup: true,
			canReceiveCommunication: true,
			contactPriority: 1,
		});
		expect(link.status).toBe(200);
		const forStudent = await get(`/api/v1/students/${s.studentId}/guardians`, w.ownerToken);
		expect(forStudent.body.some((g: any) => g.guardianId === guardian.id && g.primaryGuardian)).toBe(true);
		const forGuardian = await get(`/api/v1/guardians/${guardian.id}/students`, w.ownerToken);
		expect(forGuardian.status).toBe(200);
		expect(JSON.stringify(forGuardian.body)).toContain(String(s.studentId));
	});

	test("student documents can be attached and verified", async () => {
		const s = await addStudent(w);
		const doc = await post(`/api/v1/students/${s.studentId}/documents`, w.ownerToken, {
			documentType: "BIRTH_CERTIFICATE",
			fileReference: `pb/${uid("")}.pdf`,
			fileName: "birth.pdf",
			mimeType: "application/pdf",
		});
		expect(doc.status).toBe(200);
		expect(doc.body.verificationStatus).toBe("PENDING");
		const verified = await patch(`/api/v1/students/${s.studentId}/documents/${doc.body.id}/verify`, w.ownerToken, {
			verificationStatus: "VERIFIED",
			verifiedBy: w.ownerUserId,
		});
		expect(verified.status).toBe(200);
		expect(verified.body.verificationStatus).toBe("VERIFIED");
		const list = await get(`/api/v1/students/${s.studentId}/documents`, w.ownerToken);
		expect(list.body).toHaveLength(1);
	});

	test("a user without STUDENT_MANAGE gets 403 creating an enquiry; without STUDENT_DOCUMENT_MANAGE, 403 on documents", async () => {
		const viewer = await makeUser(w, "viewer", ["STUDENT_VIEW"]);
		const person = await createPerson(w.ownerToken, { firstName: `Nop${uid("")}`, lastName: "Pb" });
		const denied = await post("/api/v1/applicants", viewer.token, { personId: person.id, inquiryDate: "2026-05-20" });
		expect(denied.status).toBe(403);
		const s = await addStudent(w);
		const deniedDoc = await post(`/api/v1/students/${s.studentId}/documents`, viewer.token, {
			documentType: "X",
			fileReference: "pb/x.pdf",
			fileName: "x.pdf",
		});
		expect(deniedDoc.status).toBe(403);
		const allowedRead = await get(`/api/v1/students/${s.studentId}`, viewer.token);
		expect(allowedRead.status).toBe(200);
	});

	test("a user with no permissions cannot list students", async () => {
		const nobody = await makeUser(w, "nobody", []);
		expect((await get("/api/v1/students", nobody.token)).status).toBe(403);
	});
});
