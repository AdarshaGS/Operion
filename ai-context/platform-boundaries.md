# Platform boundaries: core vs. vertical

Operion's long-term goal is to support industries beyond schools without a rewrite. The
codebase already separates cleanly into a generic core and a School vertical; this
document writes that boundary down as a rule, not just a convention, so it stays a
boundary as more modules get built. Enforced automatically by
`ArchitectureBoundaryTest` (`src/test/java/com/operion/common/ArchitectureBoundaryTest.java`).

## Core packages

- `com.operion.organisation`
- `com.operion.identity`
- `com.operion.authorization`
- `com.operion.audit`

Rules:

- **May never import from any vertical package** (listed below).
- Any new field or entity added here must pass an industry-neutrality gate: *"would
  this make sense for a health clinic too?"* If not, it doesn't belong in core.

## Vertical packages

Everything else — today, all School-specific: `academic`, `student`, `parent`,
`attendance`, `finance`, `examination`, `communication`, `library`, `transport`, `hr`.

Also here: `com.operion.dashboard` — a cross-cutting aggregator (read-only rollup
queries against nearly every module above, for the post-login Dashboard), not itself
School-specific, but excluded from core for the same reason `ArchitectureBoundaryTest`
exists: it necessarily depends on most of the packages above, which core must never do.

Rules:

- May depend on core.
- Core never depends on them.

## Notes

- `com.operion.communication` (Announcement/Notification*) is already vertical-agnostic
  in practice — treat new communication work as platform-level, not School-only, even
  though it's grouped with the vertical packages above for now.
- `AcademicYear` currently lives in the core `organisation` package despite being
  School-flavored. Open question, not something to fix now: stays as-is vs. becomes a
  neutral `OperatingPeriod` primitive.
- `OrganisationConfiguration` (core) originally carried `school_start_time`/
  `school_end_time`, which violated this boundary. Fixed by moving those fields to a new
  `AcademicConfiguration` entity in `com.operion.academic` — see that migration
  (`V31__move_school_hours_to_academic_configuration.sql`) as the reference example for
  how to fix a future leak the same way.
