// Playbook module: Organisation - profile, campuses, business settings, branding (references only),
// departments / designations, setup steps and the school template. Branding FILE upload is not covered here.
import { test, expect } from "@playwright/test";
import { createPerson } from "../../../api/organisations";
import { call, createWorld, get, makeUser, patch, post, put, uid, type World } from "../../../api/pb-helpers";

let w: World;

test.describe.configure({ mode: "default" });

test.beforeAll(async () => {
	w = await createWorld();
});

test.describe("Profile", () => {
	test("the school profile can be updated; null keeps a value, empty string clears it", async () => {
		const res = await patch("/api/v1/organisations/profile", w.ownerToken, { name: `Renamed ${w.slug}`, legalName: "Legal PB", primaryContactName: "Principal", primaryContactEmail: "p@pb.test", city: "Pune", state: "MH", country: "India", pincode: "411001", taxIdentifier: "TAX1" });
		expect(res.status).toBe(200);
		expect(res.body).toMatchObject({ name: `Renamed ${w.slug}`, legalName: "Legal PB", city: "Pune", taxIdentifier: "TAX1" });
		const keep = await patch("/api/v1/organisations/profile", w.ownerToken, { city: null });
		expect(keep.body.city).toBe("Pune");
		const clear = await patch("/api/v1/organisations/profile", w.ownerToken, { taxIdentifier: "" });
		expect(clear.body.taxIdentifier).toBeNull();
		expect((await get("/api/v1/organisations/profile", w.ownerToken)).body.name).toBe(`Renamed ${w.slug}`);
	});

	test("a blank school name is rejected", async () => {
		expect((await patch("/api/v1/organisations/profile", w.ownerToken, { name: "  " })).status).toBe(400);
	});

	test("a malformed contact email is rejected", async () => {
		const res = await patch("/api/v1/organisations/profile", w.ownerToken, { primaryContactEmail: "not-an-email" });
		expect(res.status, "invalid contact email accepted").toBe(400);
	});

	test("any member can read the profile but only ORGANISATION_MANAGE can change it", async () => {
		const u = await makeUser(w, "orgview", ["STUDENT_VIEW"]);
		expect((await get("/api/v1/organisations/profile", u.token)).status).toBe(200);
		expect((await patch("/api/v1/organisations/profile", u.token, { city: "Hacked" })).status).toBe(403);
	});
});

test.describe("Campuses", () => {
	test("an extra campus is added with its own timezone and listed with the first campus", async () => {
		const code = uid("cm").toUpperCase();
		const res = await post("/api/v1/campuses", w.ownerToken, { name: `North ${code}`, code, city: "Delhi", timezone: "Asia/Kolkata" });
		expect(res.status).toBe(200);
		expect(res.body).toMatchObject({ name: `North ${code}`, code, status: "ACTIVE", timezone: "Asia/Kolkata" });
		const list = await get("/api/v1/campuses", w.ownerToken);
		expect(list.body.length).toBeGreaterThanOrEqual(2);
		expect(list.body.map((c: any) => c.id)).toEqual(expect.arrayContaining([w.campusId, res.body.id]));
	});

	test("a duplicate campus name (any case) is refused, as is an unknown timezone or a missing code", async () => {
		const name = `Dup ${uid("")}`;
		expect((await post("/api/v1/campuses", w.ownerToken, { name, code: uid("c").toUpperCase() })).status).toBe(200);
		expect((await post("/api/v1/campuses", w.ownerToken, { name: name.toUpperCase(), code: uid("c").toUpperCase() })).status).toBe(409);
		expect((await post("/api/v1/campuses", w.ownerToken, { name: `Tz ${uid("")}`, code: uid("c").toUpperCase(), timezone: "Mars/Olympus" })).status).toBe(400);
		expect((await post("/api/v1/campuses", w.ownerToken, { name: `NoCode ${uid("")}` })).status).toBe(400);
	});

	test("a duplicate campus code is refused", async () => {
		const code = uid("cc").toUpperCase();
		expect((await post("/api/v1/campuses", w.ownerToken, { name: `A ${uid("")}`, code })).status).toBe(200);
		const dup = await post("/api/v1/campuses", w.ownerToken, { name: `B ${uid("")}`, code });
		expect(dup.status, "two campuses share a code").toBeGreaterThanOrEqual(400);
		expect(dup.status).toBeLessThan(500);
	});

	test("adding a campus needs ORGANISATION_MANAGE", async () => {
		const u = await makeUser(w, "campview", ["STUDENT_VIEW"]);
		expect((await post("/api/v1/campuses", u.token, { name: `X ${uid("")}`, code: uid("x").toUpperCase() })).status).toBe(403);
	});
});

test.describe("Business settings", () => {
	test("timezone, currency, date format, working days and brand colour are saved, and the change is audited", async () => {
		const res = await patch("/api/v1/organisations/settings", w.ownerToken, { timezone: "Asia/Dubai", defaultCurrency: "AED", dateFormat: "dd/MM/yyyy", workingDaysMask: 31, logoUrl: null, primaryColor: "#1A2B3C" });
		expect(res.status).toBe(200);
		expect(res.body).toMatchObject({ timezone: "Asia/Dubai", defaultCurrency: "AED", workingDaysMask: 31, primaryColor: "#1A2B3C" });
		expect((await get("/api/v1/organisations/settings", w.ownerToken)).body).toMatchObject({ timezone: "Asia/Dubai", workingDaysMask: 31 });
		const audit = await get("/api/v1/audit-logs?entityType=OrganisationConfiguration", w.ownerToken);
		expect(audit.body.content.map((r: any) => r.action)).toContain("SETTINGS_UPDATED");
	});

	test("an unknown timezone, a working-day mask outside 1..127 and a non-hex colour are rejected", async () => {
		const base = { timezone: "Asia/Kolkata", defaultCurrency: "INR", dateFormat: "dd/MM/yyyy", workingDaysMask: 63, primaryColor: "#112233" };
		expect((await patch("/api/v1/organisations/settings", w.ownerToken, { ...base, timezone: "Nowhere/City" })).status).toBe(400);
		expect((await patch("/api/v1/organisations/settings", w.ownerToken, { ...base, workingDaysMask: 0 })).status).toBe(400);
		expect((await patch("/api/v1/organisations/settings", w.ownerToken, { ...base, workingDaysMask: 128 })).status).toBe(400);
		expect((await patch("/api/v1/organisations/settings", w.ownerToken, { ...base, primaryColor: "red" })).status).toBe(400);
	});

	test("an unrecognised currency code that fits the column (ZZZ) is rejected", async () => {
		const res = await patch("/api/v1/organisations/settings", w.ownerToken, { timezone: "Asia/Kolkata", defaultCurrency: "ZZZ", dateFormat: "dd/MM/yyyy", workingDaysMask: 63 });
		expect(res.status, "currency is not validated against ISO 4217").toBe(400);
	});

	test("an over-long currency value is a 400 validation error, not a 409 conflict", async () => {
		const res = await patch("/api/v1/organisations/settings", w.ownerToken, { timezone: "Asia/Kolkata", defaultCurrency: "NOTACURRENCY", dateFormat: "dd/MM/yyyy", workingDaysMask: 63 });
		expect(res.status).toBe(400);
	});

	test("settings writes need ORGANISATION_MANAGE", async () => {
		const u = await makeUser(w, "setview", ["STUDENT_VIEW"]);
		expect((await patch("/api/v1/organisations/settings", u.token, { timezone: "Asia/Kolkata", defaultCurrency: "INR", dateFormat: "dd/MM/yyyy", workingDaysMask: 63 })).status).toBe(403);
	});
});

test.describe("Branding (references and number formats only)", () => {
	const base = { logoRef: null, stampRef: null, signatureRef: null, schoolNameOverride: "PB School", addressLine: "1 Road", affiliationText: "CBSE 123", footerText: "Thanks", admissionNumberFormat: "ADM-{YYYY}-{SEQ:4}", invoiceNumberFormat: "INV-{SEQ:5}", receiptNumberFormat: "RCP-{YYYY}-{SEQ:5}" };

	test("number formats, school name override, affiliation and footer are saved", async () => {
		const res = await put("/api/v1/organisation/branding", w.ownerToken, base);
		expect(res.status).toBe(200);
		expect(res.body).toMatchObject({ schoolNameOverride: "PB School", affiliationText: "CBSE 123", admissionNumberFormat: "ADM-{YYYY}-{SEQ:4}", receiptNumberFormat: "RCP-{YYYY}-{SEQ:5}" });
		expect((await get("/api/v1/organisation/branding", w.ownerToken)).body.invoiceNumberFormat).toBe("INV-{SEQ:5}");
	});

	test("the saved admission format drives the next admission number (when none is typed in)", async () => {
		const person = await createPerson(w.ownerToken, { firstName: `Fmt${uid("")}`, lastName: "Pb", dateOfBirth: "2015-04-10", gender: "FEMALE" });
		const res = await post("/api/v1/students", w.ownerToken, { personId: person.id, admissionDate: "2026-06-01" });
		expect(res.status).toBe(200);
		expect(res.body.admissionNumber).toMatch(/^ADM-2026-\d{4}$/);
	});

	test("a logo reference that was never uploaded is refused; a blank number format is refused; an oversize SEQ width is refused", async () => {
		expect((await put("/api/v1/organisation/branding", w.ownerToken, { ...base, logoRef: "made-up-file.png" })).status).toBe(400);
		expect((await put("/api/v1/organisation/branding", w.ownerToken, { ...base, invoiceNumberFormat: "" })).status).toBe(400);
		expect((await put("/api/v1/organisation/branding", w.ownerToken, { ...base, receiptNumberFormat: "R-{SEQ:99}" })).status).toBe(400);
	});

	test("branding writes need ORGANISATION_MANAGE", async () => {
		const u = await makeUser(w, "brandview", ["STUDENT_VIEW"]);
		expect((await put("/api/v1/organisation/branding", u.token, base)).status).toBe(403);
	});
});

test.describe("Departments and designations", () => {
	for (const [path, noun] of [["departments", "department"], ["designations", "designation"]] as const) {
		test(`a ${noun} is created, listed, de-activated and re-activated; a duplicate or blank name is refused`, async () => {
			const name = `${noun} ${uid("")}`;
			const created = await post(`/api/v1/${path}`, w.ownerToken, { name });
			expect(created.status).toBe(200);
			expect(created.body).toMatchObject({ name, status: "ACTIVE" });
			expect((await post(`/api/v1/${path}`, w.ownerToken, { name: name.toUpperCase() })).status).toBe(409);
			expect((await post(`/api/v1/${path}`, w.ownerToken, { name: "  " })).status).toBe(400);
			expect((await get(`/api/v1/${path}`, w.ownerToken)).body.map((d: any) => d.id)).toContain(created.body.id);
			const off = await post(`/api/v1/${path}/${created.body.id}/status`, w.ownerToken, { status: "INACTIVE" });
			expect(off.status).toBe(200);
			expect(off.body.status).toBe("INACTIVE");
			expect((await post(`/api/v1/${path}/${created.body.id}/status`, w.ownerToken, { status: "ACTIVE" })).body.status).toBe("ACTIVE");
			expect((await post(`/api/v1/${path}/${created.body.id}/status`, w.ownerToken, { status: "BOGUS" })).status).toBe(400);
			expect((await post(`/api/v1/${path}/99999999/status`, w.ownerToken, { status: "ACTIVE" })).status).toBe(404);
		});

		test(`creating a ${noun} needs ORGANISATION_MANAGE`, async () => {
			const u = await makeUser(w, `${noun}view`, ["STUDENT_VIEW"]);
			expect((await post(`/api/v1/${path}`, u.token, { name: `X ${uid("")}` })).status).toBe(403);
		});
	}
});

test.describe("Setup wizard and school template", () => {
	test("setup steps are recorded once (idempotent); an unknown step is refused", async () => {
		expect((await post("/api/v1/setup/structure/steps/profile/complete", w.ownerToken)).body.completed ?? []).toBeDefined();
		const again = await post("/api/v1/setup/structure/steps/profile/complete", w.ownerToken);
		expect(again.status).toBe(200);
		const keys: string[] = Array.isArray(again.body) ? again.body : (again.body.completedSteps ?? again.body.steps ?? Object.values(again.body)[0]);
		expect(keys.filter((k) => k === "profile")).toHaveLength(1);
		expect((await post("/api/v1/setup/structure/steps/bogus/complete", w.ownerToken)).status).toBe(400);
		expect((await get("/api/v1/setup/structure/steps", w.ownerToken)).status).toBe(200);
	});

	test("the school template previews what it would add, applies it, and a second apply creates nothing new", async () => {
		const preview = await get("/api/v1/organisations/settings/school-template/preview", w.ownerToken);
		expect(preview.status).toBe(200);
		expect(preview.body.grades.length).toBeGreaterThan(0);
		const first = await call("POST", "/api/v1/organisations/settings/school-template/apply", w.ownerToken);
		expect(first.status).toBe(200);
		expect(first.body.gradesCreated.length + first.body.gradesSkipped.length).toBeGreaterThan(0);
		const second = await call("POST", "/api/v1/organisations/settings/school-template/apply", w.ownerToken);
		expect(second.status).toBe(200);
		expect(second.body.gradesCreated).toEqual([]);
		expect(second.body.departmentsCreated).toEqual([]);
		expect(second.body.subjectsCreated).toEqual([]);
		const grades = await get("/api/v1/grade-levels", w.ownerToken);
		expect(grades.body.length).toBeGreaterThan(0);
	});

	test("applying the school template needs ORGANISATION_MANAGE; go-live report needs it too", async () => {
		const u = await makeUser(w, "tmplview", ["STUDENT_VIEW"]);
		expect((await post("/api/v1/organisations/settings/school-template/apply", u.token)).status).toBe(403);
		expect((await get("/api/v1/organisations/settings/go-live", u.token)).status).toBe(403);
		expect((await get("/api/v1/organisations/settings/go-live", w.ownerToken)).status).toBe(200);
	});
});
