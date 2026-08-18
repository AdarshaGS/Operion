# Flush Context — School ERP Platform (Operion)

> Fill this in at the end of a work session and save it. It's the diff between "what load-context.md says the project is" and "what actually happened this session." Next session: read this alongside load-context.md, then fold anything durable back into load-context.md and clear this file for the next round.

## Session date

2026-08-14

## Module / milestone worked on

Parent/Guardian frontend (built + verified this session) + a competitive-gap roadmap against Schools24 (a live competitor product, browsed via a demo account) turned into GitHub issues.

## Decisions made

- Guardian management lives on the Student Detail page, not a standalone nav item — matches the backend's lack of a list-all-guardians endpoint.
- File/asset storage: local disk behind one swappable interface for MVP (not S3 yet) — mirrors the `RazorpayCredentialsProvider` seam pattern.
- Document rendering (Letter Formats / ID Cards): ship live HTML preview first, defer PDF export as a fast-follow.
- Learner Transfer workflow: intra-org (cross-campus) only for v1 — cross-org/government-connector sync explicitly out of scope.
- Subject/Curriculum catalog templating: architecture change, not a feature — needs its own Option A/B design session before any code (issue #39 tracks the decision only).

## What was actually built

- Guardian/parent frontend: `StudentGuardiansPanel`, `ClaimInvitePage`, `guardians.ts`/`studentGuardians.ts` API clients, `claimInvite` on `AuthContext` — verified end-to-end in a browser (add guardian → grant portal access → claim invite → guardian logs in). Issue #23.
- GitHub roadmap for the Schools24 gap list, phased and issue-tracked:
  - Phase A (#24 parent): #25 file storage, #26 branding entity/API, #27 branding settings screen
  - Phase B: #28 bulk CSV import, #29 audit log UI, #30 cross-module dashboard
  - Phase C: #31 Letter Formats, #32 ID Card Studio (parent) → #33 entity/API, #34 studio canvas
  - Phase D: #35 Learner Transfer workflow, #36 self-service profile edits (blocked on `/me` API), #37 Teacher Recruitment
  - Phase E: #38 Question Papers (upload+review only), #39 Subject/Curriculum catalog (design decision only, not implementation-ready)
- Full plan detail at `/Users/adarsh/.claude/plans/wondrous-wobbling-dream.md` (local, not in repo).

## Open questions (unresolved, carry to next session)

- Which phase to actually implement first — issues are filed but none started.
- `/api/v1/me/**` guardian read API still not built — blocks issue #36 and is the standing highest-payoff gap from earlier sessions too.

## Explicitly deferred / rejected

- AI-generated question papers (Schools24 has this; #38 deliberately scopes it out).
- Cross-org transfer + DIKSHA/ABC government connector sync (deferred out of #35 v1).
- S3/cloud storage (deferred in favor of local disk behind a seam, #25).

## Next step

Start Phase A (#24) — the file-storage decision (#25) blocks Phase C entirely, so it's the natural next thing to pick up.



---

### Fold-back checklist (before clearing this file)

- [ ] Any new standing principle or correction → fold into `load-context.md`'s relevant section, or into this repo's CLAUDE.md if it's a durable workflow rule.
- [ ] Milestone order still correct?
- [ ] "Current status" / React admin portal section in `load-context.md` updated to match where things actually stand?
