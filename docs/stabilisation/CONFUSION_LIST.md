# Confusion / usability list — walkthrough findings

Things that are confusing or awkward in the UI and backend, found during the product walkthrough.
**Log only — no fixes until after the freeze (2026-11-14) and the weekly usage reset.**
Status: `open` / `fixing` / `fixed (commit)` / `wontfix (reason)`.
Source: **WT** = reported by the owner during the walkthrough; **OBS** = noticed by Claude in the owner's screenshots, not yet confirmed by the owner.

## Platform console (platform-admin login, not an institution login)

| ID | Where | What | Source | Status |
|---|---|---|---|---|
| UX-1 | Organisation details → Invoices → "Generate invoice" dialog | Amount is optional. Make it mandatory and require a value greater than 0 (no negative or zero). Backend must enforce the same rule, not just the form. Today a blank amount bills ₹150 × active students; decide whether that fallback goes away. | WT | fixed (uncommitted): amount required and > 0 in form and backend; rate × headcount fallback removed |
| UX-2 | Organisation details (platform) | Contacts, Follow-ups and Activity always show, even when empty. Show a section only when it has data; otherwise tuck them behind a "⋯" (3-dots) menu. | WT | open |
| UX-3 | Organisation details (platform), and other pages | Layout wastes space (large padding, sections stacked full-width with big empty areas). Use the page spacing properly. | WT | open |
| UX-4 | Invoices | "Invoice correction is required." The owner has not yet said what is wrong — **needs detail before it can be fixed.** | WT | open — needs detail |
| UX-5 | Platform → Payments (and every other platform page) | Add a back-arrow button on every page. On Payments, remove the small "BILLING" label above the title. Read as: remove the section eyebrow label on every page, to be confirmed. | WT | open |
| UX-6 | System health | Need an option to edit secrets from this page. | WT | open — scope to decide (which secrets, who may edit, how they are stored and masked; security-sensitive) |
| UX-7 | Platform settings | Remove the extra "SETTINGS" label above the title; add a back-arrow button. | WT | open |
| UX-8 | Leads | Offer more than one view: list and grid (alongside the current board of stages). | WT | open |

## Seen in the screenshots, not yet confirmed

| ID | Where | What | Source | Status |
|---|---|---|---|---|
| UX-9 | Generate invoice dialog | Period start, period end and due date all default to today, so the default invoice covers one day and is due immediately. | OBS | open — confirm intended |
| UX-10 | Invoices table vs dialog / Payments page | Dates appear as `2026-11-08` in the Invoices table and period columns but as `15/11/2026` in the date pickers and Payments "Paid on". One format would be less confusing. | OBS | open |
| UX-11 | Platform → Payments | A payment of ₹0 is listed (for 2026-11-06). Zero-amount invoices/payments are possible today; related to UX-1. | OBS | open |
| UX-12 | Leads | The stage count badges use different colours (grey, blue, purple, orange) with no legend, and "Won" is orange. | OBS | open |
| UX-13 | Platform pages | Sidebar group headers (CUSTOMERS, BILLING, OPERATIONS…) are collapsible; the sidebar top item "Dashboard" is partly cut off under the header when the list is scrolled. | OBS | open |

## Institution (school) level

| ID | Where | What | Source | Status |
|---|---|---|---|---|
| SCH-1 | Fees → Collect fee → Payment history / Invoices | **Data bug.** After a refund, no refund transaction is recorded. The history then shows more money than was paid: for a ₹100 invoice it lists RCT-…000002 ₹100 Cleared, RCT-…000003 ₹50 Cleared, RCT-…000001 ₹100 Bounced — i.e. ₹150 of "cleared" payments against a ₹100 invoice. Suspected cause (**unverified**): the refund is saved as a second positive "cleared" payment, or the refund is not written to the ledger/payment history. Needs a reproduction and a look at `FeeService` before fixing. Collected shows ₹100, Outstanding ₹0, which contradicts the ₹150 in history. | WT | open — move to `BUG_LIST.md` (data integrity) when triaged |
| SCH-2 | Students list | Pagination and selection: when there are fewer than 20 students, show no pagination/selection UI at all. From 20 up, show a page-size / selection-count control so users can choose based on selection and filters. The existing pagination code stays as it is. Same "show only when there is something to show" idea as UX-2. The owner's screenshot looks like a design mock (1,245 students, "Showing 1–25") rather than the live page, so confirm the threshold and what "selection count" should do (page size vs row selection). | WT | open — needs confirmation |
| SCH-3 | Academics module | **Restructure (large feature, not a bug).** Replace the ten tabs (Grade levels, Sections, Subjects, Classes, Teacher assignments, Periods, Rooms, Timetable, Substitutions, Uncovered) with: **Classes** (list + details with Overview / Sections / Subjects & Teachers), **Timetable**, and **More ⋮** → Timetable settings (school day, periods, rules), Rooms, Teacher coverage (Substitutions + Uncovered periods tabs). Keep business logic, APIs, DB relationships, permissions and a central subject catalogue; add redirects for old routes; update tests. Full requirements are in the owner's message of 2026-11-15 (not yet saved as a file). | WT | open — plan first (architect), then implement |

## Backend / API

_None logged yet._
