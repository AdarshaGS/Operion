# Build plans: documents, board report cards, accounting, library, statutory payroll, hostel, health

Status: **proposal, nothing built**. Every section ends with the decisions needed before building. Written 2026-10-20 against branch `feature/two-database-split` (uncommitted state).

Evidence tags as in the product audit: **[V]** read in code this session, **[A]** assumption about schools, **[R]** needs external or legal verification, **[U]** unverified.

---

## 0. What exists today (the starting point)

| Area | Today [V] |
|---|---|
| Printing | No PDF library in `build.gradle`. Report card and payslip use browser print (`ReportCardPrintPage.tsx`). Apache POI is present but only for reading `.xlsx` imports. |
| Templates | `DocumentTemplate` (`document/DocumentTemplate.java`): type (`QUESTION_PAPER_HEADER`, `REPORT_CARD`), style (CLASSIC/MODERN/MINIMAL/ELEGANT), page size, font, header subtext. |
| Branding | `OrganisationBranding`: logo, stamp and signature refs, school-name override, address line, affiliation text, footer text, number formats. |
| Numbering | `DocumentNumberFormatter`: `{SEQ}`, `{SEQ:n}`, `{AY}`, `{YYYY}`. Receipt numbers are gap-free per school under a lock. |
| Exams | `ReportCard` is per **single exam**: totals, percentage, overall grade, pass flag, rank, status, stale flag. `ExamType` = UNIT_TEST, MID_TERM, FINAL, OTHER. No term, no co-scholastic, no weighting. Grading bands store only a minimum percentage. |
| Students | `tcNumber` (the TC from the *previous* school), `bloodGroup`, `medicalAlerts` (free text). `StudentExit` stores type (TRANSFER/WITHDRAWAL/GRADUATION/EXPULSION), date, reason and destination school. |
| Finance | Payment methods: CASH, CHEQUE, UPI, CARD, BANK_TRANSFER, ONLINE. A payment carries a reference and a bank name. `Waiver` and `Adjustment` carry `approvedBy` from the request body. `FEE_DISCOUNT_APPROVE` exists but is unused. No tax fields, no ledger or journal entities. |
| Library | `BookCopy.accessionNumber` exists. Copy statuses: AVAILABLE/BORROWED/LOST/DAMAGED/WITHDRAWN. No reservation entity, no copy lock (LIB-03). |
| Payroll | Components are EARNING or DEDUCTION, FLAT or PERCENT_OF_BASIC. `StaffBankDetail.taxIdentifier` is a PAN-like field, plain text. No UAN/ESI fields. No proration (PAY-01). |
| Modules | `AppModule` enum (19 values) with per-school `OrganisationModuleSetting`, `@RequireModule` on controllers, route prefixes and dependencies. New modules plug in here. |
| Guardians | `StudentGuardian.contactPriority` (an integer) plus primary, emergency, pickup and communication flags. Channels: IN_APP, EMAIL, SMS, WHATSAPP. |
| Hostel, health, visitors | Nothing exists. |
| Latest migration | `db/migration/school/V23__…`. New work starts at V24. |

The guiding rule from earlier work still applies. **Board formats, statutory rates and certificate wording are data the school (or the platform) configures. They are never `if (board == CBSE)` branches in code.** Presets ship as optional starting templates a school copies and edits.

---

## 1. Shared foundation: the document engine

Receipts, report cards, hall tickets, certificates, payslips, library labels, Form 16 Part B and ID cards all need the same thing. This gets built once and first.

### 1.1 Rendering

- **Templates:** HTML + CSS, rendered server-side with **Thymeleaf** (already Spring-native), then converted to PDF.
- **PDF converter, behind one interface `PdfRenderer`:**
  - Default: **OpenHTMLtoPDF** (in-process, no extra service). Licence is LGPL [R: confirm it is acceptable for a commercial SaaS used as an unmodified library].
  - Known limitation [U, from my knowledge, not tested here]: it does not do complex-script shaping, so **Hindi/Kannada/Tamil text may render wrongly**. If regional-language documents are required (decision D-N2 in the audit), plug in a **headless-Chromium sidecar (Gotenberg)** behind the same interface. Nothing else changes.
- **Fonts:** bundle Noto Sans (and Noto Sans Devanagari etc. when needed). No reliance on system fonts, so output is identical on every server.
- **Page sizes:** A4, A5, Letter, and 80 mm thermal for counter receipts.
- **Images:** logo, stamp, signature and student photo are read from asset storage server-side and embedded. Nothing is fetched by URL inside the PDF, which also keeps working once uploads become private (SEC-08).
- **QR code and barcodes:** ZXing (Apache-2.0).

### 1.2 The issued-document register

Every *official* document (certificates, receipts, published report cards, hall tickets) gets a row:

```
issued_documents
  id, organisation_id, document_kind (RECEIPT, REPORT_CARD, HALL_TICKET, CERTIFICATE, PAYSLIP, ...)
  document_number            -- from DocumentNumberFormatter with a per-kind counter
  subject_type, subject_id   -- e.g. STUDENT 123, PAYMENT 456
  template_id, template_version
  data_snapshot JSON         -- every value printed, frozen at issue
  content_hash (sha256)      -- of the snapshot + template version
  verification_token         -- random, for the QR code
  status ISSUED | CANCELLED | SUPERSEDED
  issued_by, issued_at, cancelled_by, cancelled_at, cancel_reason
  copy_count                 -- reprints; the 2nd+ print carries a "DUPLICATE" mark
```

- **Why a snapshot, not a stored PDF:** the PDF is re-rendered from the frozen snapshot on demand. That avoids storing thousands of files of minors' data, and the document can never drift from what was issued, even if the student's name is corrected later. A later correction means cancel and reissue, and both rows stay.
- **Verification:** the QR code points to a public `GET /verify/{token}` page. It shows only the document kind, number, issue date, school, the student's name and status (VALID/CANCELLED). This lets a receiving school check a TC without calling.
- **Reprints:** each reprint increments `copy_count` and is audited.

### 1.3 Bulk generation

Report cards and hall tickets for 1,000 students must not run in one request.

- `document_batches` (kind, filter, requested_by, status, counts) and `document_batch_items` (subject, status, error).
- A background worker processes items one row at a time, in its own transaction, with TenantContext set **before** the transaction opens (the known gotcha). One failure doesn't stop the others; this is the same per-row pattern as bulk invoicing.
- Output: one combined PDF per class (for printing) or a ZIP of per-student PDFs. It is downloadable for 24 h, then deleted; the snapshots remain.

### 1.4 Template editing

- **Phase 1:** the school picks a preset and edits fields (header text, which sections show, signature labels, colours) with live preview. No raw HTML for schools; that would become a support burden and an XSS surface.
- **Phase 2 (optional):** a platform-only raw-HTML editor for Operion staff to onboard a school's exact format.

### 1.5 Permissions

`DOCUMENT_TEMPLATE_MANAGE`. Each document kind keeps its own issue permission (below). Cancellation is a separate permission from issuing.

**Size:** M. **Decisions:** D-DOC1 renderer (OpenHTMLtoPDF now, Chromium if regional scripts are needed); D-DOC2 whether a public verify page is wanted.

---

## 2. PDF fee receipts

**Data:** Payment + allocations → invoices → fee categories, student, enrollment (class/section), primary guardian, branding. The receipt number already exists [V].

**Content:**
- School header, receipt number, date, student, admission number, class.
- One line per fee head paid, plus the method and reference (cheque number/bank/UPI ref).
- Amount in figures and **in words**. The words formatter is configurable: Indian grouping (lakh/crore) or international (million), set per school, not hard-coded.
- Balance still outstanding, collector name, stamp/signature.

**States:**
- A bounced cheque or cancelled payment prints "CANCELLED".
- Reprints print "DUPLICATE".
- Optional thermal 80 mm layout.

**Endpoints:**
- `GET /api/v1/fees/payments/{id}/receipt.pdf` (FEE_PAYMENT_VIEW).
- Portal: `GET /api/v1/me/children/{studentId}/payments/{paymentId}/receipt.pdf`, checked through the existing guardian-link check.

**Email:** the receipt can be attached to the payment-confirmation email (already sent through the outbox).

**Optional GST on receipts (from the competitor list):**
- `tax_rate` and `hsn_sac_code` on `FeeCategory`.
- A tax-registration number on branding.
- Inclusive or exclusive computation, with the tax split printed.
- **Off by default.** Many school fees are tax-exempt in India, while some heads (e.g. some transport or uniform sales) may not be [R: verify with a CA before shipping].

**Size:** S once §1 exists.

---

## 3. Hall tickets

**Needs one schema addition:** `ExamSchedule` has a date but no **start/end time, venue or seat**. Add `start_time`, `end_time` and `venue` (all nullable).

**Content:**
- Photo, name, admission/roll number, class/section, exam name.
- A table of the student's schedules (only the subjects scheduled for their class/section).
- Instructions text (template field), seat number (optional), signature lines, QR.

**Generation:**
- Per exam × class/section as a bulk batch (§1.3). Single reprint from the student page.
- Portal download for guardians once the school "releases" hall tickets for that exam.

**Optional withhold rule (generic, off by default):**
- Per-school setting `withhold_hall_ticket_when_dues_above` (amount).
- Withheld students are listed with their dues and can be released individually; the override is audited. Some schools do this and others consider it unfair [A], so it is the school's call.

**Seat allocation** (roll-number ranges per room) can come later. Phase 1 prints whatever the school enters.

**Permissions:** `HALL_TICKET_ISSUE`. **Size:** S–M.

---

## 4. Certificates: TC, bonafide, character, leaving

### 4.1 Model: certificate types are data

```
certificate_types
  code, name (school-defined: "Transfer Certificate", "Bonafide", "Character", "School Leaving")
  template_id
  number_format                 -- e.g. "TC/{AY}/{SEQ:4}"
  once_per_student BOOLEAN      -- TC: yes; bonafide: no
  requires_exit BOOLEAN         -- TC/leaving need a StudentExit
  requires_clearance BOOLEAN    -- run the dues/library/transport checks
  manual_fields JSON            -- fields the issuer fills in: conduct, "qualified for promotion",
                                   "subjects studied", purpose (bonafide) ...
  approval_required BOOLEAN
```

**Merge fields available to every template (computed, not typed):**
- Student: name, date of birth in figures and words, gender, nationality, category, admission number and date.
- Guardian names.
- Class at admission and current/last class.
- Exit date and reason, destination school.
- Attendance: working days and days present in the last year (via `WorkingDayService`).
- Last exam result; fees paid up to.

Anything the school's format needs that is not computable becomes a `manual_field`.

### 4.2 Workflow

```
REQUESTED  (staff, or guardian through the portal for bonafide)
  -> CLEARANCE  (if required: unpaid invoices, open library loans/fines, active transport,
                 hostel; each shown as a pass/fail line; override needs a reason, audited)
  -> APPROVED   (if approval_required; approver != requester, CERTIFICATE_APPROVE)
  -> ISSUED     (number allocated, snapshot frozen into issued_documents, PDF)
  -> CANCELLED / REISSUED (duplicate copy says "DUPLICATE")
```

- **TC issue is tied to the exit.** Recording a TRANSFER exit offers "Issue TC" pre-filled from the exit. Issuing a TC for a student with no exit is blocked.
- **The clearance step is the STU-03 checklist.** Building certificates closes most of that gap.
- A **certificate register report** (number, student, type, issue date, issuer, status) covers what schools keep as a physical register.

### 4.3 Presets

Optional starting templates for common TC layouts. Board and state formats prescribe specific TC fields and they change [R: take the exact format from the pilot school and current board circulars]. Presets are copied into the school's own templates, never referenced live.

**Permissions:** `CERTIFICATE_REQUEST`, `CERTIFICATE_APPROVE`, `CERTIFICATE_ISSUE`, `CERTIFICATE_CANCEL`. **Size:** M.

---

## 5. Board report cards: cumulative, CBSE/ICSE-style, NEP Holistic Progress Card

This is the largest academic piece. The current per-exam `ReportCard` stays and is relabelled "exam result". A new **cumulative report card** sits on top of it.

### 5.1 Assessment scheme (configurable per academic year and grade range)

```
assessment_schemes        (academic_year, name, grade_levels[], grading_scale, status)
assessment_terms          (scheme, name "Term 1", order, start_date, end_date)
assessment_components     (term, name "Periodic Test", weight %, max_marks_scaled_to,
                           aggregation SUM | AVERAGE | BEST_OF_N | WEIGHTED, n,
                           exam_ids[]  -- which existing exams feed it)
subject_overrides         (scheme, subject, component, weight)  -- e.g. a practical-heavy subject
```

**Worked example.** "Term 1 = Periodic Test (best 2 of 3 unit tests, scaled to 10) + Notebook 5 + Enrichment 5 + Half-yearly 80" is pure configuration.
- The Notebook and Enrichment marks are **internal-assessment components**. They are entered per subject per student through the existing marks register (an `ExamType` of INTERNAL is added) so they get the same draft → submit → approve workflow.

**Engine:** for each student × subject × term:
1. Pull the marks of the linked exams.
2. Apply the aggregation.
3. Scale to the component maximum.
4. Weight and sum.
5. Grade via the scheme's scale.

The same steps then roll up across terms for the year.

**Grading bands need an upper bound or an ordered list.** For example, A1 91–100 down to E (fail) is the CBSE-style 8-point scale [R]. Add `max_percentage` and `grade_point` to bands.

### 5.2 Co-scholastic areas, attendance, remarks

```
co_scholastic_areas   (scheme, name "Work Education" / "Art" / "Health & PE" / "Discipline", grading_scale)
co_scholastic_grades  (student, term, area, grade, entered_by)      -- class teacher entry grid
report_card_remarks   (student, term, remark, entered_by)          -- AI draft hook (see §10)
```

Attendance per term is computed from the attendance register against working days (WorkingDayService), never typed in.

### 5.3 NEP Holistic Progress Card (HPC)

The HPC is not marks-based. It describes **competencies and ability levels per domain**, with self, peer and parent inputs [R: the format differs by stage (foundational, preparatory, middle, secondary) and is still evolving; take the current PARAKH/board template]. It is modelled generically:

```
holistic_domains      (scheme, stage, name "Language", "Numeracy", "Socio-emotional", "Physical" ...)
holistic_descriptors  (domain, competency text, order)
holistic_levels       (scheme, label "Beginner" / "Proficient" / "Advanced" / "Stream" ..., order)
holistic_entries      (student, term, descriptor, level, note, source TEACHER | SELF | PEER | PARENT,
                       entered_by_person, entered_at)
```

- Teachers enter levels in a grid (student × descriptor).
- **Parent input** comes through the portal: one short form per term.
- **Self and peer reflection** need the student to enter something. There is no student login (decision D-I2), so phase 1 lets the teacher record them on the student's behalf (as on paper today). Real student entry waits for a student login.
- **Physical-health data** (height, weight, BMI) comes from the Health module's check-ups (§9) if switched on.

### 5.4 Publish, release, withhold, viewed tracking

- **Publish:** compute everything into `cumulative_report_cards(student, scheme, term or FINAL, data_snapshot, status DRAFT/PUBLISHED/STALE, version)`.
  - Snapshot-based, like certificates: later mark corrections set STALE and need a republish. This mirrors the existing stale/republish design.
  - Ranks are frozen at publish (fixes audit contradiction C-6).
- **Scheduled release (D-E2):** `release_at` per class. Parents see nothing before it. Notification goes out at release, not at publish.
- **Withhold when fees are unpaid (Entab idea):** a school setting with a threshold. The portal shows "Result withheld, please contact the office". The admin list shows who is withheld, with a per-student release override (audited). Off by default.
- **Viewed tracking (Entab idea):**
  - `document_views(document, viewer_person, viewed_at, channel)`, written when a guardian opens the card in the portal.
  - Report: "published but not viewed by any guardian", for follow-up calls.
- **Teacher-wise "marks not entered" report (Entab idea):** for each exam schedule past its date + N days whose register is missing or DRAFT, list the assigned subject teacher (TeacherAssignment). This is a daily digest to the exam coordinator.

### 5.5 Presets

Shipped as optional scheme presets:
- "Two-term weighted (CBSE-style)".
- "Percentage, single final (ICSE-style)".
- "HPC foundational/preparatory".

Each comes with a matching layout template. All are copied into the school and editable; they are verified against the pilot school's actual card before shipping [R].

**Permissions:** `ASSESSMENT_SCHEME_MANAGE`, `CO_SCHOLASTIC_ENTER` (scoped to assigned sections once the section-scope work lands), `REPORT_CARD_PUBLISH` (existing pattern), `REPORT_CARD_RELEASE`. **Size:** L (scheme engine M, HPC M, layouts S–M each).

---

## 6. Finance: concessions, carry-forward, cheques, reconciliation, Tally, ledger

Built in this order; each step is useful on its own.

### 6.1 Concession request and approval (S)

```
concession_requests (invoice or fee_assignment, kind WAIVER | DISCOUNT, amount or percent, reason,
                     proof_ref, requested_by, status PENDING | APPROVED | REJECTED,
                     decided_by, decided_at, decision_note)
```

- **Approval:** approver ≠ requester, holds the existing `FEE_DISCOUNT_APPROVE`.
- On approval it creates the `Waiver`/`Adjustment`, with `approvedBy` = **the caller, not a body field**. This fixes FEE-03 for concessions.
- **Threshold:** a school setting where amounts below X auto-approve but are still logged.
- Recurring policies (sibling, staff-child, scholarship) come later as `concession_policies` that generate requests automatically at assignment time.

### 6.2 Carry-forward of dues into the next year (S–M)

Part of the year rollover, with the same preview-then-apply pattern as bulk invoicing:
- For each student with outstanding invoices in the closing year, create one "Previous year dues" invoice in the new year. It has line references to the old invoices, which move to a new status `CARRIED_FORWARD`, so dues are never counted twice.
- **Overpayments** need a credit balance. A credit/wallet was **deliberately deferred** earlier. Options:
  - (a) carry overpayments as a negative adjustment on the first new-year invoice;
  - (b) build the credit balance now.
  
  Your call.

### 6.3 Post-dated cheques (S)

- Add `cheque_number`, `cheque_date` and a cheque status: `HELD → DEPOSITED → CLEARED | BOUNCED`.
- A school setting decides whether the invoice is credited on receipt (today's behaviour) or only on clearing.
- The existing bounce flow is reused.
- A "cheques due for deposit this week" list.

### 6.4 Bank reconciliation (M)

1. Upload a bank statement CSV with a column-mapping step, since every bank's CSV differs. This creates `bank_statement_lines` (date, amount, reference, narration).
2. **Auto-match** to payments by UPI/transaction reference, cheque number, or amount + date window.
3. A manual match UI.
4. Two exception lists: "in bank, not in Operion" and "in Operion, not in bank".
5. **Day-close (FEE-05) comes first:** a closed day cannot be edited, and reconciliation works on closed days.

### 6.5 Tally export (M)

Most schools keep their books in Tally with an outside accountant [A]. The goal is that the accountant stops re-keying.

**Ledger mapping (per school, set once):**

| Operion source | Maps to a Tally ledger the school names |
|---|---|
| Each fee category | an income ledger ("Tuition Fees", "Transport Fees") |
| Each payment method / bank | cash or bank ledger |
| Student receivable | one "Fees Receivable" ledger, or per-student ledgers (choice) |
| Waivers / concessions | "Fee Concession" (expense or contra-income) |
| Refunds | payment voucher against the bank ledger |

**Export:**
- A date range produces Tally **XML vouchers**: Receipt for payments, Payment for refunds, Journal for waivers/bounces, optionally Sales/Journal for invoices if the accountant books on accrual.
- The voucher number = Operion receipt number, so a re-import doesn't duplicate.
- `accounting_export_runs` records range, counts and file hash. Exported days are flagged and re-exporting asks for confirmation.
- A CSV/Excel variant serves accountants who import differently.

[R: the exact XML envelope and voucher fields must be tested against the pilot accountant's TallyPrime version before shipping; Tally changes its import formats between releases.]

### 6.6 Internal accounting ledger (L, only if the school wants Operion to be the books)

```
ledger_accounts   (code, name, type ASSET | LIABILITY | INCOME | EXPENSE | EQUITY, parent, status)
journal_entries   (date, source_type, source_id, narration, reversal_of, posted_at, period)
journal_lines     (entry, account, debit, credit)     -- sum(debit) = sum(credit), enforced
accounting_periods (month, status OPEN | LOCKED)
```

- **Insert-only.** Corrections are reversing entries.
- A `FinancePostingService` posts automatically when a fee event happens (invoice, payment, refund, waiver, bounce). Library fines and store sales (audit SAL-01) post too, which finally puts all money in one place.
- **Reports:** day book, cash book, bank book, ledger, trial balance, income & expenditure.

**Recommendation:** Tally export first. Build the ledger only if a pilot school wants to drop Tally, because a ledger also needs expenses, vendors and payroll postings to be complete.

### 6.7 Teacher-wise "attendance not marked" report

Same as §5.4's marks report: for each working day, sections with a HOMEROOM assignment and no submitted register, grouped by teacher. Delivered as a daily digest at a school-set time.

**Permissions:** existing finance codes plus `FEE_RECONCILE`, `ACCOUNTING_EXPORT`, `LEDGER_MANAGE`.

---

## 7. Library: barcodes, OPAC, reservation queue

**Prerequisites from the audit, done first:**
- **LIB-03:** lock the copy row in issue/return/lost (fixes double issue).
- **LIB-06:** search pickers instead of the unpaged person list.

### 7.1 Barcodes (S)

- The accession number already exists [V]; it becomes a **Code 128** barcode.
- **Label sheets:** a PDF on the document engine, with a configurable grid (e.g. 3×8 A4 sticker sheets), for selected copies or "all copies added since a date".
- **Member cards:** a barcode of admission number or employee code.
- **Scanners need no integration:** USB/Bluetooth scanners type the code and Enter. Issue and return screens get a focused "scan" field: scan the member, then scan books, and each scan issues immediately. Return mode: scan, it returns, and any fine is shown.
- **Optional ISBN lookup** on catalogue entry (Open Library API) to pre-fill title and author. It's external and optional, and off if the school doesn't want outbound calls.

### 7.2 OPAC (online catalogue) (S–M)

- `GET /api/v1/library/opac?q=&category=&campus=` searches title, author, ISBN, publisher and category.
  - MySQL **FULLTEXT** index on title and author; fine at school scale.
  - Results show availability per campus: "2 of 5 available".
- **Surfaces:** the staff app, the guardian and teacher portals, and optionally a **public catalogue page per school** (a school toggle, off by default; shows no borrower data ever).
- Shows the book's reservation queue length.

### 7.3 Reservation queue (M)

```
book_reservations (book  -- title level, not a copy
                   borrower_person, status WAITING | READY | FULFILLED | CANCELLED | EXPIRED,
                   created_at  -- queue order, ready_at, hold_until, held_copy)
```

- **Reserve:** only when no copy is available (setting); max N active per borrower; no duplicate reservation for the same title.
- **On return of a copy** whose book has a WAITING reservation:
  - the copy goes to a new status **ON_HOLD**, assigned to the first in the queue;
  - the reservation becomes READY with `hold_until` = today + N working days;
  - the borrower (or guardian) is notified.
- **Issuing an ON_HOLD copy** is allowed only to the holder, and fulfils the reservation.
- **Expiry:** a daily job expires holds past `hold_until` and passes the copy to the next in line (or back to AVAILABLE).
- **Concurrency:** the return, hold and issue steps all take the copy row lock (the LIB-03 fix), so two desks cannot both give away the held copy.

**Permissions:** `LIBRARY_RESERVE` (portal users for themselves / their child), `LIBRARY_RESERVATION_MANAGE`. **Size:** M overall.

---

## 8. Statutory payroll: PF, ESI, PT, TDS, Form 16, ECR

**Read this first.** This is the riskiest item on the list:
- The audit put statutory payroll under "do not build yet". The base payroll still pays full salary to late joiners and unpaid leave (PAY-01), and corrections drop leavers (PAY-02).
- Statutory files built on wrong gross pay produce wrong government filings.
- **Prerequisites:** PAY-01 (proration, LOP days), PAY-20 (paid/unpaid leave flag), PAY-02, PAY-03, and PAY-06 (encrypt and mask PAN/bank, because we'd now be storing UAN and ESI numbers too).
- Every rate, ceiling and file format below is [R]. They change by notification and budget, and must be confirmed with a payroll CA before go-live.

### 8.1 Generic "statutory scheme" model (rates are data, never code)

```
statutory_schemes          (code PF | ESI | PT | LWF | TDS, region, enabled per school)
statutory_scheme_versions  (scheme, effective_from,
                            wage_components[]   -- which salary components form the wage base
                            employee_rate, employer_rate, employer_split JSON (e.g. EPF/EPS/EDLI/admin),
                            wage_ceiling, eligibility_threshold, rounding rule)
pt_slabs                   (region/state, effective_from, salary_from, salary_to, amount, month rules)
staff_statutory_ids        (staff, scheme, identifier  -- UAN, ESI IP number; encrypted
                            enrolled_from, exempt BOOLEAN, exempt_reason)
```

- Platform staff maintain the national tables; each school only switches schemes on and maps its components.
- **During a run**, the engine adds **system-generated payslip lines**:
  - employee contributions as deductions;
  - employer contributions as non-deducted "employer cost" lines.
  
  This gives correct net pay plus CTC and the challan amounts.

### 8.2 TDS (income tax on salary)

```
tax_regimes / tax_slab_tables  (financial_year, regime, slabs, rebate, cess, standard deduction)  -- platform data
tax_declarations               (staff, financial_year, regime, declared items JSON, proofs, verified_by)
```

- **Monthly TDS** = (projected annual tax − TDS already deducted) ÷ remaining months, recomputed each run. The projection uses actual pay to date + structure for the rest of the year.
- Staff submit declarations and proofs in the staff portal; HR verifies.

### 8.3 Outputs

| Output | What Operion can do |
|---|---|
| **PF ECR file** | Generate the monthly ECR text file from a finalized run: UAN, name, gross, EPF/EPS/EDLI wages, contributions, NCP days (= LOP days, which needs PAY-01), refunds. Validated against the current EPFO spec before upload [R]. |
| **ESI** | Monthly contribution sheet in the portal's upload template [R]. |
| **PT** | Monthly state-wise PT register. |
| **Form 16** | **Part A is issued from the government's TRACES portal; Operion cannot generate it.** Operion generates **Part B** (salary break-up, deductions, tax computation) as a PDF on the document engine. |
| **Form 24Q (quarterly TDS return)** | Export the deductee data in the format the CA's return utility imports. Generating the final FVU file ourselves is not recommended. |
| **Registers** | Salary register, PF/ESI/PT registers, challan summaries. |

### 8.4 Order

1. PAY-01/02/03/06/20 fixes.
2. PF + ECR.
3. ESI.
4. PT.
5. TDS projection + declarations.
6. Form 16 Part B.

Behind the existing PAYROLL module plus a sub-switch "Statutory", off by default.

**Permissions:** `PAYROLL_STATUTORY_MANAGE`, `TAX_DECLARATION_VERIFY`; staff self-service for declarations. **Size:** L, plus external validation time.

---

## 9. Hostel: rooms, mess, in/out log

You said you're designing this yourself. This is my version to compare against yours, not a replacement. Entity names are kept generic ("residence", "room", "bed") with school-facing labels, in line with the no-industry-hardcoding rule. The same model fits a hospital ward later.

### 9.1 Rooms and allocation

```
residences          (campus, name, residence_type label, gender_policy (configurable list), status)
residence_wardens   (residence, staff, role WARDEN | ASSISTANT, from, to)   -- scope for wardens
rooms               (residence, floor, number, room_type label, capacity, status)
beds                (room, label, status AVAILABLE | OCCUPIED | MAINTENANCE)
bed_allocations     (bed, student_enrollment, from_date, to_date, status, allocated_by)  -- insert-only history
```

- **One active allocation per student and per bed**, enforced with a row lock on the bed, the same pattern as the invoice/copy locks.
- **Transfer between rooms** = close one allocation, open another (history kept).
- **Fees:** allocation creates the hostel fee assignment through the same hook as transport (`FeeCategoryType`, #285). Vacating ends it, and the school is prompted about the remaining installments (the audit's TRN-02 lesson).
- Vacating is part of the **exit clearance** (§4.2).

### 9.2 Mess

```
mess_plans          (name, meals included, fee structure link)
mess_subscriptions  (student, plan, from, to)
mess_menus          (residence or campus, weekday, meal, items, effective_from)
meal_attendance     (optional: student, date, meal, source TICK | SCAN)
mess_rebate_rules   (optional: away > N consecutive days -> rebate per day)
```

Dietary needs (veg/non-veg/allergies) come from the Health profile (§9.4), so the mess sees allergens without seeing the full medical record.

### 9.3 In/out log and leave

```
residence_leave_requests (student, type OUTING | HOME_LEAVE, from, expected_return,
                          requested_by (guardian via portal or warden), reason,
                          status PENDING | APPROVED | REJECTED | CANCELLED, decided_by)
residence_movements      (student, direction OUT | IN, at, leave_request, recorded_by,
                          collected_by_person  -- must be a StudentGuardian with canPickup, or a named
                                                  authorised person recorded by the warden
                          notes)
roll_calls               (residence, date, session NIGHT | MORNING, status DRAFT | SUBMITTED)
roll_call_entries        (roll_call, student, status PRESENT | ON_LEAVE | ABSENT)
```

- **Notifications:** guardian on OUT and on IN; warden when a student is past `expected_return` (a job checks every 15 minutes).
- **Escalation down the contact list (Edumerge idea):** if a critical message (absent at roll call, late return) is not acknowledged within N minutes, notify the next guardian by `contactPriority`. Acknowledgement only works reliably on IN_APP/push, since email and SMS have no read signal, so this fully pays off with the parent app.
- Roll call marks students on approved leave as ON_LEAVE automatically.

### 9.4 Visitors

Guardian visits to a resident are logged in the general **visitor log** (the optional module from the earlier list). The hostel uses it rather than having its own.

**Module:** new `AppModule.HOSTEL` (requires STUDENTS; FEES optional). **Permissions:** `HOSTEL_VIEW`, `HOSTEL_SETUP`, `HOSTEL_ALLOCATE`, `HOSTEL_MOVEMENT_RECORD`, `HOSTEL_LEAVE_APPROVE`. Wardens are scoped to their residences. **Size:** L (rooms + allocation M, mess M, movement/leave M).

---

## 10. Health and infirmary

Health data of minors is the most sensitive data Operion would hold [R: treat it as sensitive personal data; confirm consent and retention rules with counsel].

### 10.1 Health profile (students and staff)

```
health_profiles   (person, blood_group  -- migrate from Student.bloodGroup
                   emergency_instructions, doctor_name/phone, insurance JSON, updated_by, updated_at)
health_allergies  (person, allergen, severity MILD | MODERATE | SEVERE, reaction, action)
health_conditions (person, condition, notes, since)
standing_medications (person, medicine, dose, schedule, consent_record, from, to)
immunizations     (person, vaccine, dose_number, date, proof_ref)
health_checkups   (person, date, height_cm, weight_kg, bmi (computed), vision L/R, dental, hearing, notes, examiner)
```

- Today's free-text `medicalAlerts` is migrated into a single "note" entry and kept until staff restructure it.
- **Guardians** see their child's profile in the portal. They change it through the existing **profile-change request** workflow (staff approve), not by direct edit.
- Check-ups feed the HPC physical-health section (§5.3).

### 10.2 Infirmary visits

```
infirmary_visits (person, arrived_at, referred_by (teacher), complaint,
                  vitals JSON (temperature, pulse, BP, SpO2), assessment, treatment,
                  outcome RETURNED_TO_CLASS | REST | SENT_HOME | REFERRED_HOSPITAL,
                  collected_by_person (canPickup check), departed_at, attended_by, guardian_notified_at)
medicine_dispensed (visit, item  -- links to Inventory items, so infirmary stock is tracked
                    quantity, dose, given_by)
medication_administrations (standing_medication, given_at, given_by, notes)   -- daily log
incident_reports (person, occurred_at, place, description, injury, first_aid, witnesses, follow_up, reported_by)
```

- **Notification rules (configurable):** e.g. always notify the guardian on SENT_HOME/REFERRED; notify on any visit if the school wants.
- **Reports:** visits by day/class/complaint (spotting outbreaks), repeat visitors, medicine consumption, immunization coverage.

### 10.3 Access control (the important part)

- Full records: `HEALTH_VIEW` / `HEALTH_MANAGE` (nurse, principal).
- **Teachers see only an "alert card"**: allergies marked SEVERE plus emergency instructions. It shows on the section roster and the attendance screen, because a teacher on a field trip needs that and nothing more.
- **Every read of a full health record is audited** (not just writes).
- Health data is excluded from general exports, the reporting views and the AI features, unless a specific permission is held.
- Medication administration requires a recorded consent (`consent_record`, linking to the existing consent module, which then finally *enforces* something; audit REG-01).

**Module:** `AppModule.HEALTH` (requires STUDENTS; Inventory optional for stock). **Size:** M–L.

---

## 11. The small ideas from the competitor list

| Idea | Where it lands | Size |
|---|---|---|
| Withhold report card when fees unpaid | §5.4, generic threshold setting, off by default, per-student release | S |
| Track when a parent viewed the report card | §5.4 `document_views`; reused for circulars and receipts | S |
| Teacher-wise "attendance not marked" / "marks not entered" | §5.4 and §6.7, daily digest | S |
| Carry unpaid/overpaid fees forward | §6.2 (overpaid needs your credit-balance decision) | S–M |
| Post-dated cheques, bank reconciliation | §6.3, §6.4 | S + M |
| GST on fee receipts | §2, off by default, [R] | S |
| Concession approval step | §6.1 | S |
| Voice-call broadcast | New VOICE channel through a telephony provider (Exotel/Twilio-style): text-to-speech or recorded audio, per-call cost. **Defer** until a school asks and agrees to pay per call; telecom rules [R]. | M + vendor |
| Escalate to the next contact if no response | §9.3; needs an acknowledgement signal, so best after push/the parent app | S–M |

---

## 12. Suggested order

| Step | What | Why this position | Size |
|---|---|---|---|
| 1 | Document engine (§1) + PDF receipts (§2) | Unblocks everything; receipts are needed daily | M + S |
| 2 | Certificates (§4) incl. exit clearance | Closes STU-03/STU-06; first leaver needs a TC | M |
| 3 | Concession approval, PDC, carry-forward (§6.1–6.3) + "not marked / not entered" digests | Small, high daily value | S each |
| 4 | Hall tickets (§3) | Before the next exam season | S–M |
| 5 | Assessment scheme + cumulative report card + co-scholastic (§5.1–5.2, 5.4) | Needs the pilot's real card (D-E1) | L |
| 6 | Tally export (§6.5), then reconciliation (§6.4) | Needs day-close and the accountant's Tally version | M + M |
| 7 | Library prerequisites + barcodes + OPAC + reservations (§7) | Independent; can run in parallel | M |
| 8 | Health (§10) | Independent; sensitive, so careful access work | M–L |
| 9 | Hostel (§9), merged with your design | Independent | L |
| 10 | HPC (§5.3) | After the scheme engine exists | M |
| 11 | Statutory payroll (§8) | Only after PAY-01..06; external validation | L |
| — | Internal ledger (§6.6), voice calls | Only on a real school's request | L / M |

Every step ships with: Flyway migration (school DB, V24+), service tests in the existing `@DataJpaTest` style, a tenant-isolation test for each new table, permission seeds, a module switch where it is a new module, web screens, and an e2e check on the throwaway-DB harness.

---

## 13. Decisions needed from you

| ID | Question | My recommendation |
|---|---|---|
| D-DOC1 | PDF renderer | OpenHTMLtoPDF now; switch to a Chromium sidecar only if regional-language documents are required |
| D-DOC2 | Public QR verification page for certificates | Yes, minimal fields only |
| D-E1 | Report-card format | Use the pilot school's actual card(s) as the acceptance test |
| D-E2 | Result release | Scheduled release per class |
| D-E4 | Withhold results / hall tickets for dues | Available, off by default, school decides |
| D-F6 | Carry-forward of overpayments | Negative adjustment on the first new-year invoice now; credit balance later |
| D-F8 | Invoice credited on cheque receipt or on clearing | School setting, default "on clearing" |
| D-F9 | Tally export vs own ledger | Tally export first; ledger only on request |
| D-TAX1 | GST fields on fee categories | Build, off by default, after a CA confirms which heads are taxable |
| D-LIB1 | Public OPAC page | Off by default, school toggle |
| D-PAY2 | Statutory payroll timing | Only after PAY-01..06 are fixed; PF/ECR first |
| D-HOS1 | Hostel design | Merge with your system design before I build anything |
| D-HLT1 | Teacher visibility of health data | Severe allergies + emergency instructions only |
| D-HLT2 | Infirmary stock through Inventory | Yes, if Inventory is switched on |
