// Guardian / teacher arrange helpers for the playbook specs.
import { createPerson } from "./organisations";
import { claimParentInvite, createGuardian, grantPortalAccess, linkGuardianToStudent } from "./parentPortal";
import { assignTeacher } from "./roles";
import { makeUser, PB_PASSWORD, uid, type PbStudent, type PbUser, type World } from "./pb-helpers";

export interface PbGuardian {
	guardianId: number;
	personId: number;
	token: string;
	userId: number;
}

/** A guardian linked to `children`, with portal access claimed (one claim-invite call per guardian: that endpoint is rate limited). */
export async function makeGuardian(w: World, children: PbStudent[]): Promise<PbGuardian> {
	const person = await createPerson(w.ownerToken, { firstName: `Gd${uid("")}`, lastName: "Pb", email: `${uid("g")}@${w.slug}.test`, phone: `90${String(Date.now()).slice(-6)}${Math.floor(Math.random() * 90 + 10)}` });
	const guardian = await createGuardian(w.ownerToken, person.id, "Parent");
	for (const child of children) {
		await linkGuardianToStudent(w.ownerToken, child.studentId, { guardianId: guardian.id, relationshipType: "MOTHER", primaryGuardian: true });
	}
	const invite = await grantPortalAccess(w.ownerToken, guardian.id);
	const session = await claimParentInvite(w.slug, invite.claimToken, PB_PASSWORD);
	return { guardianId: guardian.id, personId: person.id, token: session.token, userId: session.userId };
}

/** A teacher (a staff role with `permissionCodes`) who is the HOMEROOM teacher of `sectionId`. */
export async function makeTeacher(w: World, label: string, permissionCodes: string[], sectionId: number): Promise<PbUser> {
	const u = await makeUser(w, label, permissionCodes);
	await assignTeacher(w.ownerToken, sectionId, { teacherPersonId: u.personId, assignmentType: "HOMEROOM", startDate: "2026-06-01" });
	return u;
}
