# Flush Context — School ERP Platform (Operion)

> Fill this in at the end of a work session and save it. It's the diff between "what load-context.md says the project is" and "what actually happened this session." Next session: read this alongside load-context.md, then fold anything durable back into load-context.md and clear this file for the next round.

## Session date

2026-08-10 (second session of the day — follows on from the Fees/Examinations session already folded into load-context.md and cleared from this file)

## Module / milestone worked on

**Communication module** (`com.operion.communication`) — the first of the remaining light-sketch modules (erp-system-plan.md §3.3) to get a deep design and full build, picked over RBAC enforcement and the Academic Foundation test backfill when those three options were surfaced at session start per the standing "surface options before picking" instruction in load-context.md.

## Decisions made

- **User picked Communication** over RBAC enforcement / Academic Foundation test backfill / other light-sketch modules, via an explicit multiple-choice prompt at session start.
- **Design doc presented and approved with all three recommended options** ("Proceed with implementation" + confirmed all three): (1) v1 ships **IN_APP delivery only** — EMAIL/SMS are reserved enum values, no provider wired; (2) **audience targeting is a type+id polymorphic column** (`audienceType` + `audienceRefId`) rather than a join table, matching "one Announcement targets exactly one audience"; (3) **read receipts tracked** (`readAt` per `NotificationRecipient`).
- **Because v1 is IN_APP-only, no polling dispatch worker was built** — this was a design simplification made during implementation (not explicitly asked, but a direct consequence of decision #1): `NotificationRecipient` rows are written straight to `SENT` at fan-out time, since row-creation-is-delivery for an in-app channel. A worker only becomes necessary once EMAIL/SMS are actually wired to a provider — flagged in code comments so it isn't mistaken for an oversight later.
- **Fan-out for CLASS/SECTION/INDIVIDUAL resolves students + their guardians only, not assigned teachers** — a deliberate v2 scope cut, not an oversight (flagged in `CommunicationService`'s class doc).
- **`NotificationTemplate` was built but nothing calls it yet** — it exists as a seam for a future module (e.g. Fees firing a due-date reminder) to use via `CommunicationService.sendTemplatedNotification`, matching the "don't build a generic template engine speculatively, but leave the seam" balance struck elsewhere in the codebase.
- **"Current person" resolution is new territory**: no prior module needed to map the JWT's `userId` (`TenantContext.getActorId()`) to a `Person` for a `/me`-style endpoint. Resolved via `OrganisationMembershipRepository.findByUserId(actorId).findFirst().getPerson()` — duplicated inline in the two controllers that need it (`NotificationController`, `NotificationPreferenceController`) rather than extracted into a shared helper, matching the codebase's general preference for controllers composing repositories directly over introducing abstraction for a 3-line lookup used in only two places.

## What was actually built

- **Communication** (`com.operion.communication`): `Announcement`, `NotificationTemplate`, `NotificationRecipient`, `NotificationPreference` + enums (`AudienceType`, `AnnouncementStatus`, `NotificationChannel`, `DeliveryStatus`); `CommunicationService` (draft/publish/cancel announcement, audience fan-out resolution per type, preference-filtered recipient creation, mark-read, template CRUD, preference upsert); full REST API under `/api/v1/announcements`, `/api/v1/notifications`, `/api/v1/notification-preferences`, `/api/v1/notification-templates`; migrations `V13__communication_schema.sql`, `V14__seed_communication_permissions.sql`; tests `AnnouncementFanOutTest` (5 cases: SECTION resolves student+guardian, disabled preference excludes a person, ORG resolves active memberships only, publish/cancel reject non-DRAFT transitions), `CommunicationTenantIsolationTest`.
- Two small supporting repository methods added to existing modules to support fan-out: `StudentEnrollmentRepository.findBySectionIdAndCurrentTrue`, `OrganisationMembershipRepository.findByStatus` / `findByCampusIdAndStatus`.
- `ai-context/load-context.md`'s "Current status" section updated live during the session to record the module and re-point the open fork (now: RBAC enforcement vs. Academic Foundation test backfill vs. Transportation/Library/Inventory/HR).
- `./gradlew test` run full-suite (not just the new module's tests) after building: 39/39 passing, 0 failures/errors.

## Open questions (unresolved, carry to next session)

- The open fork, now with Communication resolved off the list: **RBAC/permission enforcement** (outstanding since Foundation — seven modules' worth of permission codes now exist, nothing checks them) vs. **backfilling Academic Foundation's test gap** (`GradeLevel`/`SchoolClass`/`Section`/`Subject`/`ClassSubject` still untested directly) vs. **Transportation / Library / Inventory / HR** (none deep-designed yet, see erp-system-plan.md §3.3). Surface all three at the start of next session, same as this one.
- **Still nothing committed to git.** `git status` at this session's start showed Fees + Examinations (from the prior session) already uncommitted, plus small `INSERT` → `INSERT IGNORE` edits to `V2`/`V6`/`V8`/`V10` made outside any conversation. This session adds Communication (`V13`/`V14`) on top, uncommitted. Check `git log`/`git status` at the start of next session rather than assuming anything landed.
- **The `INSERT IGNORE` migration inconsistency from last session is still unresolved, and now has more data points.** Confirmed this session: `V2`, `V6`, `V8`, `V10` are `INSERT IGNORE INTO` (uncommitted edits, origin unknown — not made by the assistant in either session). `V4` and `V12` were **not** touched and remain plain `INSERT INTO`. This session's new `V14` was also written as plain `INSERT INTO`, matching `V4`/`V12` (the untouched majority) rather than guessing at the `IGNORE` pattern's intent. Worth deciding explicitly next session: was `INSERT IGNORE` on V2/V6/V8/V10 an intentional idempotency fix that should be extended to V4/V12/V14 too, or a one-off/accidental edit that should be reverted? Don't assume either way — ask.

## Explicitly deferred / rejected

- Nothing explicitly rejected this session — the design doc's three flagged decisions were all accepted as recommended.

## Next step

Start of next session: check `git log`/`git status` first (three modules' worth of uncommitted work now: Fees, Examinations, Communication — confirm nothing changed outside the conversation again, especially re: the `INSERT IGNORE` question above). Then ask the user to pick one of the three open items (RBAC enforcement / Academic Foundation test backfill / a remaining light-sketch module) before writing any code — don't assume, and don't propose UI/frontend work per the standing `workflow_build_all_modules_first` memory.

---

### Fold-back checklist (before clearing this file)

- [x] Any new standing principle or correction → none this session; existing `workflow_build_all_modules_first` memory already covered the design-doc-first + full-suite-test-run behavior this session followed, no update needed.
- [x] Milestone order still correct? → yes, unchanged; Communication (part of milestone 7 in load-context.md's ordering) now done. Remaining: RBAC enforcement (cross-cutting, not a milestone number), Academic Foundation test backfill, or one of Transportation/Library/Inventory/HR (order not yet decided among them).
- [x] "Current status" line in load-context.md updated to match where things actually stand → done live during the session.
