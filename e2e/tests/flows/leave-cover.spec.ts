// Leave cover end to end: approved leave leaves periods without their teacher; the Uncovered tab lists them,
// "Auto-suggest cover" proposes substitutes (nothing is saved until Confirm), the leave inbox shows the periods a
// pending leave would take the teacher out of, and the whole thing is permission-gated (V35 grants TIMETABLE_VIEW /
// TIMETABLE_MANAGE to the starter-pack Principal and TIMETABLE_VIEW to HR Manager).
//
// Runs through leave-cover-playwright.config.ts (own config, owner session, one worker) rather than the shared
// playwright.config.ts. global-setup.ts supplies the fresh organisation, the academic year/class/section/subject and
// the role fixtures; everything cover-specific (teachers, periods, timetable, leave) is seeded below through the API
// client, then the UI is driven for what is under test. The tests are ordered and share that seeded state.
//
// Calendar used (hard-coded, inside global-setup's 2026-06-01..2027-03-31 year; default weekly off is Sunday):
//   Mon 2026-11-16 / Tue 2026-11-17  Anita's leave (and Dinesh's Monday)
//   Sat 2026-11-21 / Sun 11-22 / Mon 11-23  Farah's leave: Sunday is the weekly off, 11-23 is made a holiday afterwards
//   Tue 2026-12-01  Chitra's leave, which the inbox test rejects

import { mkdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { devices, expect, test, type Browser, type BrowserContext, type Locator, type Page } from "@playwright/test";
import { api } from "../../api/client";
import { createPerson, createSection, createSubject, type LoginResponse } from "../../api/organisations";
import { assignTeacher, claimStaffInvite, grantMembership, inviteUser } from "../../api/roles";
import { AUTH_DIR, FRONTEND_BASE_URL } from "../../env";
import { FIXED_PASSWORD, type SeedData } from "../../global-setup";
import { tokenFor } from "../../support/auth";
import { trackDiagnostics } from "../../support/diagnostics";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

const SHOT_DIR = new URL("../../test-results/leave-cover/", import.meta.url).pathname;
mkdirSync(SHOT_DIR, { recursive: true });

async function shot(page: Page, name: string) {
	await page.screenshot({ path: join(SHOT_DIR, `${name}.png`), fullPage: true });
}

const MON = "2026-11-16";
const TUE = "2026-11-17";
const SAT2 = "2026-11-21";
const SUN2 = "2026-11-22";
const MON2 = "2026-11-23";
const REJECT_DAY = "2026-12-01";
const LONG_TO = "2026-12-05"; // 20 days after MON: too long for Auto-suggest (14), fine for the list (62)

const PEOPLE = {
	anita: { firstName: "Anita", lastName: "Alpha" }, // teacher A: on leave Mon-Tue
	bhavin: { firstName: "Bhavin", lastName: "Bravo" }, // teacher B: free to cover (busy Mon period 2)
	chitra: { firstName: "Chitra", lastName: "Charlie" }, // cover candidate; her pending leave is rejected
	dinesh: { firstName: "Dinesh", lastName: "Delta" }, // second absent teacher (Mon period 1, other section)
	farah: { firstName: "Farah", lastName: "Foxtrot" }, // edge cases: weekly off and holiday
};
const BLANK = /^[\u200b\s]*$/;
const name = (p: { firstName: string; lastName: string }) => `${p.firstName} ${p.lastName}`;

interface Seeded {
	people: Record<keyof typeof PEOPLE, number>;
	staff: Record<"anita" | "chitra" | "dinesh" | "farah", number>;
	leave: Record<"anita" | "chitra" | "dinesh" | "farah", number>;
	roleStates: { principal: StorageState; hrManager: StorageState };
}
interface StorageState {
	cookies: [];
	origins: { origin: string; localStorage: { name: string; value: string }[] }[];
}

function stateFrom(session: LoginResponse): StorageState {
	return {
		cookies: [],
		origins: [
			{
				origin: FRONTEND_BASE_URL,
				localStorage: [
					{
						name: "operion.session",
						value: JSON.stringify({ token: session.token, expiresAt: session.expiresAt, userId: session.userId, organisationId: session.organisationId }),
					},
				],
			},
		],
	};
}

let seeded: Seeded;
let ownerToken: string;

async function newContext(browser: Browser, storageState: string | StorageState): Promise<BrowserContext> {
	return browser.newContext({ ...devices["Desktop Chrome"], baseURL: FRONTEND_BASE_URL, storageState });
}

test.describe.configure({ mode: "serial" });
test.use({ storageState: join(AUTH_DIR, "owner.json") });

test.beforeAll(async () => {
	test.setTimeout(240_000);
	ownerToken = tokenFor("owner");
	const t = ownerToken;

	// People, a designation, staff profiles for the four who take leave.
	const people = {} as Seeded["people"];
	for (const key of Object.keys(PEOPLE) as (keyof typeof PEOPLE)[]) {
		people[key] = (await createPerson(t, PEOPLE[key])).id;
	}
	const designation = await api.post<{ id: number }>("/api/v1/designations", { name: "Cover Teacher" }, t);
	const staff = {} as Seeded["staff"];
	for (const key of ["anita", "chitra", "dinesh", "farah"] as const) {
		const profile = await api.post<{ id: number }>(
			"/api/v1/hr/staff",
			{
				personId: people[key],
				campusId: seed.campusId,
				employeeCode: `LC-${key.toUpperCase()}-${seed.organisationSlug.slice(-6)}`.slice(0, 30),
				designationId: designation.id,
				dateOfJoining: "2026-06-01",
				employmentType: "PERMANENT",
			},
			t,
		);
		staff[key] = profile.id;
	}

	// The school day: three teaching periods, with gaps between them.
	const periods: number[] = [];
	for (const [i, [start, end]] of [["08:00", "08:45"], ["09:00", "09:45"], ["10:00", "10:45"]].entries()) {
		const period = await api.post<{ id: number }>("/api/v1/timetable/periods", { ordinal: i + 1, label: `Cover P${i + 1}`, startTime: start, endTime: end, slotType: "TEACHING" }, t);
		periods.push(period.id);
	}
	const [p1, p2, p3] = periods;

	// Two sections (seed's "A" plus a new "B") and one subject per teacher: a section allows a single ACTIVE
	// assignment per subject, so sharing a subject would silently end the previous teacher's assignment. The
	// candidate pool for cover is everyone with an active teacher assignment, which includes global-setup's own
	// "teacher" fixture.
	const sectionB = await createSection(t, seed.classId, "B", 40, "Room 2");
	const tag = seed.organisationSlug.slice(-6).toUpperCase();
	const subjects = {} as Record<keyof typeof PEOPLE, number>;
	for (const key of Object.keys(PEOPLE) as (keyof typeof PEOPLE)[]) {
		subjects[key] = (await createSubject(t, `Cover Subject ${key}`, `CV${key.slice(0, 3).toUpperCase()}${tag}`)).id;
	}
	const assign = (sectionId: number, personId: number, subjectId: number) =>
		assignTeacher(t, sectionId, { subjectId, teacherPersonId: personId, assignmentType: "SUBJECT", startDate: "2026-06-01" });
	await assign(seed.sectionId, people.anita, subjects.anita);
	await assign(seed.sectionId, people.chitra, subjects.chitra);
	await assign(sectionB.id, people.bhavin, subjects.bhavin);
	await assign(sectionB.id, people.dinesh, subjects.dinesh);
	await assign(sectionB.id, people.farah, subjects.farah);

	const slot = (sectionId: number, subjectId: number, teacher: number, dayOfWeek: string, periodId: number) =>
		api.put(`/api/v1/sections/${sectionId}/timetable`, { subjectId, teacherPersonId: teacher, dayOfWeek, periodId }, t);
	await slot(seed.sectionId, subjects.anita, people.anita, "MONDAY", p1);
	await slot(seed.sectionId, subjects.anita, people.anita, "MONDAY", p2);
	await slot(seed.sectionId, subjects.anita, people.anita, "TUESDAY", p1);
	await slot(sectionB.id, subjects.dinesh, people.dinesh, "MONDAY", p1);
	await slot(sectionB.id, subjects.bhavin, people.bhavin, "MONDAY", p2); // Bhavin is busy exactly when Anita's second period needs cover
	await slot(sectionB.id, subjects.farah, people.farah, "SATURDAY", p1);
	await slot(sectionB.id, subjects.farah, people.farah, "SUNDAY", p1);
	await slot(sectionB.id, subjects.farah, people.farah, "MONDAY", p3);

	// Leave type, balances, and the leave requests (all start PENDING).
	const leaveType = await api.post<{ id: number }>("/api/v1/hr/leave-types", { code: `CVR${seed.organisationSlug.slice(-6)}`.slice(0, 30), name: "Cover Test Leave", defaultAnnualDays: 30 }, t);
	for (const key of ["anita", "chitra", "dinesh", "farah"] as const) {
		await api.post("/api/v1/hr/leave-balances", { staffProfileId: staff[key], leaveTypeId: leaveType.id, academicYearId: seed.academicYearId, allocatedDays: 30 }, t);
	}
	const raise = (key: keyof Seeded["staff"], startDate: string, endDate: string, numberOfDays: number) =>
		api.post<{ id: number }>(
			"/api/v1/hr/leave-requests",
			{ staffProfileId: staff[key], leaveTypeId: leaveType.id, academicYearId: seed.academicYearId, startDate, endDate, numberOfDays, reason: "Leave cover e2e" },
			t,
		);
	const leave = {
		anita: (await raise("anita", MON, TUE, 2)).id,
		dinesh: (await raise("dinesh", MON, MON, 1)).id,
		chitra: (await raise("chitra", REJECT_DAY, REJECT_DAY, 1)).id,
		farah: (await raise("farah", SAT2, MON2, 2)).id, // Saturday + Monday; the Sunday between is the weekly off
	};
	// Farah's leave is approved up front (it is only the fixture for the edge cases); the weekly-off day is already
	// off by default, and Monday 23rd becomes a holiday only after the approval - leave cannot be raised on a holiday.
	await api.post(`/api/v1/hr/leave-requests/${leave.farah}/approve`, {}, t);
	await api.post("/api/v1/academic/working-calendar", { academicYearId: seed.academicYearId, campusId: null, date: MON2, label: "Leave cover e2e holiday", type: "HOLIDAY" }, t);

	// Starter-pack roles (Principal, HR Manager) and a user holding each.
	await api.post("/api/v1/organisations/settings/school-template/apply", undefined, t); // no body: apply for the modules already on (a body would try to configure modules)
	const roles = await api.get<{ id: number; name: string; permissionCodes: string[] }[]>("/api/v1/roles", t);
	const roleStates = {} as Seeded["roleStates"];
	for (const [key, roleName] of [["principal", "Principal"], ["hrManager", "HR Manager"]] as const) {
		const role = roles.find((r) => r.name === roleName);
		if (!role) throw new Error(`Starter pack did not create the ${roleName} role (roles: ${roles.map((r) => r.name).join(", ")})`);
		const person = await createPerson(t, { firstName: roleName.split(" ")[0], lastName: "Fixture" });
		const invite = await inviteUser(t, `${key.toLowerCase()}@${seed.organisationSlug}.test`);
		const claimed = await claimStaffInvite(seed.organisationSlug, invite.claimToken, FIXED_PASSWORD);
		await grantMembership(t, { userId: invite.userId, personId: person.id, roleId: role.id, campusId: seed.campusId });
		roleStates[key] = stateFrom(claimed);
	}

	seeded = { people, staff, leave, roleStates };
	console.log(
		`leave-cover seed: org=${seed.organisationSlug} owner=${seed.ownerEmail} leave ids ${JSON.stringify(leave)} people ${JSON.stringify(people)}`,
	);
});

// The pending-leave table on /hr; the staff directory above it also names these people, so scope to this one.
function inboxTable(page: Page) {
	return page.getByRole("table").filter({ has: page.getByRole("columnheader", { name: "Leave type" }) });
}

function uncoveredTable(page: Page) {
	return page.getByRole("table", { name: "Uncovered periods" });
}
const dataRows = (table: Locator) => table.getByRole("row").filter({ hasNot: table.page().getByRole("columnheader") });

async function openUncovered(page: Page, from: string, to: string) {
	await page.goto("/academics?tab=uncovered");
	await expect(page.getByRole("tab", { name: "Uncovered", selected: true })).toBeVisible();
	await page.getByRole("textbox", { name: "From", exact: true }).fill(from);
	await page.getByRole("textbox", { name: "To", exact: true }).fill(to);
}

interface PreviewRow {
	date: string;
	period: string;
	section: string;
	absent: string;
	cover: string; // the selected option's text, or the "nobody free" message
}

/** Opens a preview row's Substitute select and picks a candidate other than the current one and other than Skip. The
 * open menu is modal (it hides the dialog from the accessibility tree), so the current value is read before opening. */
async function changeToAnotherCandidate(page: Page, row: Locator): Promise<string> {
	const combo = row.getByRole("combobox");
	const before = ((await combo.textContent()) ?? "").trim();
	await combo.click();
	const options = (await page.getByRole("listbox").getByRole("option").allTextContents()).map((t) => t.trim());
	const alternative = options.find((t) => t !== "Skip" && t !== before);
	expect(alternative, `the row should offer a candidate other than "${before}" (options: ${options.join(" | ")})`).toBeTruthy();
	await page.getByRole("option", { name: alternative!, exact: true }).click();
	return alternative!;
}

async function readPreview(dialog: Locator): Promise<PreviewRow[]> {
	const rows = dialog.getByRole("table", { name: "Suggested cover" }).locator("tbody tr");
	const out: PreviewRow[] = [];
	for (let i = 0; i < (await rows.count()); i++) {
		const row = rows.nth(i);
		const cells = await row.getByRole("cell").allTextContents();
		const combo = row.getByRole("combobox");
		const cover = (await combo.count()) ? ((await combo.textContent()) ?? "").replace(/\u200b/g, "").trim() : cells[5];
		out.push({ date: cells[0], period: cells[1], section: cells[2], absent: cells[4], cover });
	}
	return out;
}

test("b. leave inbox: pending leave shows its timetable cover, Assign cover deep-links, Approve/Reject work", async ({ page }) => {
	const diagnostics = trackDiagnostics(page);
	const inbox = () => inboxTable(page);
	const inboxRow = (p: { firstName: string; lastName: string }) => inbox().getByRole("row", { name: new RegExp(`^${name(p)} \\(`) });
	const anitaRow = () => inboxRow(PEOPLE.anita);

	await test.step("pending request has a Timetable cover toggle that lists the periods it takes", async () => {
		await page.goto("/hr");
		await expect(anitaRow()).toBeVisible();
		await expect(anitaRow().getByRole("button", { name: "Timetable cover" })).toBeVisible();
		await anitaRow().getByRole("button", { name: "Timetable cover" }).click();

		const panel = page.getByRole("table", { name: "Periods missed during leave" });
		await expect(panel).toBeVisible();
		// Anita teaches Monday periods 1 and 2 and Tuesday period 1 in the seeded timetable.
		await expect(panel.locator("tbody tr")).toHaveCount(3);
		await expect(panel.locator("tbody tr").filter({ hasText: "Uncovered" })).toHaveCount(3);
		await expect(panel.getByText(`Monday ${MON}`)).toHaveCount(2);
		await expect(panel.getByText(`Tuesday ${TUE}`)).toHaveCount(1);
		await expect(page.getByRole("link", { name: "Assign cover" })).toBeVisible();
		await shot(page, "b1-inbox-cover-panel");
	});

	await test.step("Assign cover opens Substitutions prefilled, with the slots already shown", async () => {
		await page.getByRole("link", { name: "Assign cover" }).click();
		await expect(page).toHaveURL(/\/academics\?tab=substitutions&teacher=\d+&from=2026-11-16&to=2026-11-17/);
		await expect(page.getByRole("tab", { name: "Substitutions", selected: true })).toBeVisible();
		await expect(page.getByRole("combobox", { name: "Absent teacher" })).toContainText(name(PEOPLE.anita));
		await expect(page.getByRole("textbox", { name: "From", exact: true })).toHaveValue(MON);
		await expect(page.getByRole("textbox", { name: "To", exact: true })).toHaveValue(TUE);
		const slots = page.getByRole("table", { name: "Slots needing cover" });
		await expect(slots).toBeVisible();
		await expect(slots.locator("tbody tr")).toHaveCount(3);
		await shot(page, "b2-assign-cover-prefilled");
	});

	await test.step("Approve and Reject still work with the cover panel open", async () => {
		await page.goto("/hr");
		const dineshRow = inboxRow(PEOPLE.dinesh);
		await dineshRow.getByRole("button", { name: "Timetable cover" }).click();
		await expect(page.getByRole("table", { name: "Periods missed during leave" })).toBeVisible();
		await dineshRow.getByRole("button", { name: "Approve" }).click();
		await expect(inboxRow(PEOPLE.dinesh)).toHaveCount(0);

		const chitraRow = inboxRow(PEOPLE.chitra);
		await chitraRow.getByRole("button", { name: "Reject" }).click();
		await expect(inboxRow(PEOPLE.chitra)).toHaveCount(0);
		await expect(anitaRow()).toBeVisible(); // untouched
		await shot(page, "b3-after-approve-and-reject");

		const approved = await api.get<{ status: string }[]>("/api/v1/hr/leave-requests?status=APPROVED", ownerToken);
		const rejected = await api.get<{ status: string }[]>("/api/v1/hr/leave-requests?status=REJECTED", ownerToken);
		expect(approved.length).toBeGreaterThanOrEqual(2); // Dinesh's, plus Farah's from the seed
		expect(rejected).toHaveLength(1);
	});

	diagnostics.assertClean("leave inbox");
});

test("a. owner: approve leave, find uncovered periods, auto-suggest (preview, cancel, confirm), covered rows move", async ({ page }) => {
	const diagnostics = trackDiagnostics(page);
	const absentNames = { [MON]: [name(PEOPLE.anita), name(PEOPLE.dinesh)], [TUE]: [name(PEOPLE.anita)] } as Record<string, string[]>;
	let chosen: Record<string, string> = {}; // "date|period|absent" -> substitute's name, as confirmed

	await test.step("approve Anita's leave from the inbox", async () => {
		await page.goto("/hr");
		const row = inboxTable(page).getByRole("row", { name: new RegExp(`^${name(PEOPLE.anita)} \\(`) });
		await row.getByRole("button", { name: "Approve" }).click();
		await expect(row).toHaveCount(0);
	});

	await test.step("Academics > Uncovered lists the periods Anita (and Dinesh) leave without a teacher", async () => {
		await openUncovered(page, MON, TUE);
		const table = uncoveredTable(page);
		await expect(table).toBeVisible();
		const rows = table.locator("tbody tr");
		await expect(rows).toHaveCount(4);
		await expect(rows.filter({ hasText: "Uncovered" })).toHaveCount(4);
		await expect(rows.filter({ hasText: name(PEOPLE.anita) })).toHaveCount(3);
		await expect(rows.filter({ hasText: name(PEOPLE.dinesh) })).toHaveCount(1);
		await shot(page, "a1-uncovered-list");
	});

	await test.step("Auto-suggest cover: preview lists proposals, none for an absent teacher, nobody double-booked", async () => {
		await page.getByRole("button", { name: "Auto-suggest cover" }).click();
		const dialog = page.getByRole("dialog", { name: "Suggested cover" });
		await expect(dialog).toBeVisible();
		const preview = await readPreview(dialog);
		expect(preview).toHaveLength(4);
		await shot(page, "a2-suggest-preview");

		const seen = new Set<string>();
		for (const row of preview) {
			expect(row.cover, `a proposal for ${row.date} ${row.period} ${row.absent} (got "${row.cover}")`).toMatch(/ — /);
			const coverName = row.cover.split(" — ")[0].trim();
			expect(absentNames[row.date] ?? [], `${coverName} is absent on ${row.date} and cannot cover ${row.period}`).not.toContain(coverName);
			const slotKey = `${row.date}|${row.period}|${coverName}`;
			expect(seen.has(slotKey), `${coverName} is proposed twice in ${row.date} ${row.period}`).toBe(false);
			seen.add(slotKey);
			// Bhavin teaches Monday period 2 (another section), so he is not free to cover Anita there.
			if (row.date === MON && row.period === "Cover P2") expect(coverName).not.toBe(name(PEOPLE.bhavin));
		}
	});

	await test.step("change one row, skip another, then Cancel: nothing is saved", async () => {
		const dialog = page.getByRole("dialog", { name: "Suggested cover" });
		const rows = dialog.locator("tbody tr");
		const tueRow = rows.filter({ hasText: TUE });
		const changed = await changeToAnotherCandidate(page, tueRow);
		await expect(tueRow.getByRole("combobox")).toHaveText(changed);

		const monP2 = rows.filter({ hasText: MON }).filter({ hasText: "Cover P2" });
		await monP2.getByRole("combobox").click();
		await page.getByRole("option", { name: "Skip", exact: true }).click();
		// MUI renders a select whose chosen item has value "" as blank, not as the item's "Skip" label.
		await expect(monP2.getByRole("combobox")).toHaveText(BLANK);
		await shot(page, "a3-preview-edited");

		await dialog.getByRole("button", { name: "Cancel" }).click();
		await expect(dialog).toHaveCount(0);

		const monCover = await api.get<unknown[]>(`/api/v1/timetable/substitutions?date=${MON}`, ownerToken);
		const tueCover = await api.get<unknown[]>(`/api/v1/timetable/substitutions?date=${TUE}`, ownerToken);
		expect(monCover).toHaveLength(0);
		expect(tueCover).toHaveLength(0);
		await expect(uncoveredTable(page).locator("tbody tr").filter({ hasText: "Uncovered" })).toHaveCount(4);
	});

	await test.step("redo, change a row and skip a row, then Confirm", async () => {
		await page.getByRole("button", { name: "Auto-suggest cover" }).click();
		const dialog = page.getByRole("dialog", { name: "Suggested cover" });
		await expect(dialog).toBeVisible();
		const rows = dialog.locator("tbody tr");
		await expect(rows).toHaveCount(4);

		const tueRow = rows.filter({ hasText: TUE });
		await changeToAnotherCandidate(page, tueRow);
		const monP2 = rows.filter({ hasText: MON }).filter({ hasText: "Cover P2" });
		await monP2.getByRole("combobox").click();
		await page.getByRole("option", { name: "Skip", exact: true }).click();

		const preview = await readPreview(dialog);
		chosen = Object.fromEntries(preview.filter((r) => r.cover !== "").map((r) => [`${r.date}|${r.period}|${r.absent}`, r.cover.split(" — ")[0].trim()]));
		expect(Object.keys(chosen)).toHaveLength(3);

		await dialog.getByRole("button", { name: "Confirm" }).click();
		await expect(dialog.getByRole("status").filter({ hasText: "Assigned" })).toHaveCount(3);
		await expect(dialog.getByRole("status").filter({ hasText: "Failed" })).toHaveCount(0);
		await shot(page, "a4-preview-confirmed");
		await dialog.getByRole("button", { name: "Close" }).click();
		await expect(dialog).toHaveCount(0);
	});

	await test.step("list: covered rows show the substitute; 'Uncovered only' hides them; toggling off brings them back", async () => {
		const table = uncoveredTable(page);
		const rows = table.locator("tbody tr");
		await expect(rows).toHaveCount(4);
		await expect(rows.filter({ hasText: "Covered by" })).toHaveCount(3);
		await expect(rows.filter({ hasText: "Uncovered" })).toHaveCount(1);
		for (const [key, substitute] of Object.entries(chosen)) {
			const [date, period, absent] = key.split("|");
			await expect(rows.filter({ hasText: date }).filter({ hasText: period }).filter({ hasText: absent })).toContainText(`Covered by ${substitute}`);
		}
		await shot(page, "a5-list-after-confirm");

		await page.getByLabel("Uncovered only").check();
		await expect(rows).toHaveCount(1);
		await expect(rows.first()).toContainText("Cover P2");
		await expect(rows.first()).toContainText("Uncovered");
		await shot(page, "a6-uncovered-only");

		await page.getByLabel("Uncovered only").uncheck();
		await expect(rows).toHaveCount(4);
		await expect(rows.filter({ hasText: "Covered by" })).toHaveCount(3);
	});

	await test.step("the confirmed cover appears under Substitutions", async () => {
		await page.getByRole("tab", { name: "Substitutions" }).click();
		await expect(page.getByText(/^Cover arranged for \d{4}-\d{2}-\d{2}$/)).toBeVisible(); // the new panel is mounted (and defaulted to today)
		await page.getByRole("textbox", { name: "From", exact: true }).fill(MON);
		const arranged = page.getByRole("table", { name: "Cover arranged" });
		await expect(arranged).toBeVisible();
		await expect(arranged.locator("tbody tr")).toHaveCount(2); // Monday period 1, twice (two sections)
		for (const [key, substitute] of Object.entries(chosen)) {
			const [date, period, absent] = key.split("|");
			if (date !== MON) continue;
			await expect(arranged.locator("tbody tr").filter({ hasText: period }).filter({ hasText: absent })).toContainText(substitute);
		}
		await shot(page, "a7-substitutions-monday");

		await page.getByRole("textbox", { name: "From", exact: true }).fill(TUE);
		await expect(arranged.locator("tbody tr")).toHaveCount(1);
		await expect(arranged.locator("tbody tr").first()).toContainText(name(PEOPLE.anita));
	});

	diagnostics.assertClean("owner uncovered flow");
});

test.describe("c. permissions", () => {
	test("Principal: sees Uncovered and can Auto-suggest", async ({ browser }) => {
		const context = await newContext(browser, seeded.roleStates.principal);
		const page = await context.newPage();
		const diagnostics = trackDiagnostics(page);
		await openUncovered(page, MON, TUE);
		await expect(uncoveredTable(page)).toBeVisible();
		const button = page.getByRole("button", { name: "Auto-suggest cover" });
		await expect(button).toBeVisible();
		await expect(button).toBeEnabled();
		await shot(page, "c1-principal-uncovered");
		await button.click();
		const dialog = page.getByRole("dialog", { name: "Suggested cover" });
		await expect(dialog).toBeVisible();
		// Only the skipped Monday period 2 is still uncovered after the owner flow.
		await expect(dialog.locator("tbody tr")).toHaveCount(1);
		await shot(page, "c2-principal-suggest");
		await dialog.getByRole("button", { name: "Cancel" }).click();
		diagnostics.assertClean("principal");
		await context.close();
	});

	test("HR Manager: sees the Uncovered list but no Auto-suggest button", async ({ browser }) => {
		const context = await newContext(browser, seeded.roleStates.hrManager);
		const page = await context.newPage();
		const diagnostics = trackDiagnostics(page);
		await openUncovered(page, MON, TUE);
		await expect(uncoveredTable(page)).toBeVisible();
		await expect(uncoveredTable(page).locator("tbody tr")).toHaveCount(4);
		await expect(page.getByRole("button", { name: "Auto-suggest cover" })).toHaveCount(0);
		await shot(page, "c3-hr-manager-uncovered");
		diagnostics.assertClean("hr manager");
		await context.close();
	});

	test("Teacher and a no-permission user do not see the Uncovered tab", async ({ browser }) => {
		for (const fixture of ["teacher", "noPermissions"]) {
			const context = await newContext(browser, join(AUTH_DIR, `${fixture}.json`));
			const page = await context.newPage();
			await page.goto("/academics?tab=uncovered");
			await expect(page.getByRole("tab", { name: "Grade levels" }).or(page.getByRole("tab").first())).toBeVisible();
			await expect(page.getByRole("tab", { name: "Uncovered" }), `${fixture} should not get the Uncovered tab`).toHaveCount(0);
			await expect(page.getByRole("table", { name: "Uncovered periods" })).toHaveCount(0);
			await shot(page, `c4-${fixture}-no-uncovered-tab`);
			await context.close();
		}
	});
});

test.describe("d. edge cases", () => {
	test("a weekly-off day and a holiday show nothing uncovered", async ({ page }) => {
		const diagnostics = trackDiagnostics(page);
		const empty = page.getByText("No periods are affected by approved leave in this range.");

		await openUncovered(page, SAT2, SAT2);
		await expect(uncoveredTable(page).locator("tbody tr")).toHaveCount(1); // control: the working Saturday is listed
		await expect(uncoveredTable(page)).toContainText(name(PEOPLE.farah));

		await page.getByRole("textbox", { name: "From", exact: true }).fill(SUN2);
		await page.getByRole("textbox", { name: "To", exact: true }).fill(SUN2);
		await expect(empty).toBeVisible(); // Sunday: the weekly off, though Farah has a Sunday period in the timetable
		await shot(page, "d1-weekly-off-nothing-uncovered");

		await page.getByRole("textbox", { name: "From", exact: true }).fill(MON2);
		await page.getByRole("textbox", { name: "To", exact: true }).fill(MON2);
		await expect(empty).toBeVisible(); // Monday 23rd: a holiday, though Farah has a Monday period 3
		await shot(page, "d2-holiday-nothing-uncovered");

		await page.getByRole("textbox", { name: "From", exact: true }).fill(SAT2);
		await page.getByRole("textbox", { name: "To", exact: true }).fill(MON2);
		await expect(uncoveredTable(page).locator("tbody tr")).toHaveCount(1); // the whole leave: only Saturday counts
		diagnostics.assertClean("weekly off / holiday");
	});

	test("a range over 14 days disables Auto-suggest but the list still loads", async ({ page }) => {
		const diagnostics = trackDiagnostics(page);
		await openUncovered(page, MON, LONG_TO);
		await expect(page.getByText("Auto-suggest works on at most 14 days")).toBeVisible();
		await expect(page.getByRole("button", { name: "Auto-suggest cover" })).toBeDisabled();
		await expect(uncoveredTable(page)).toBeVisible();
		await shot(page, "d3-range-over-14-days");

		await page.getByRole("textbox", { name: "To", exact: true }).fill("2026-11-29"); // exactly 14 days
		await expect(page.getByRole("button", { name: "Auto-suggest cover" })).toBeEnabled();
		await expect(page.getByText("Auto-suggest works on at most 14 days")).toHaveCount(0);
		diagnostics.assertClean("range over 14 days");
	});
});
