# Operion — Complete School Procedure & Predefined Configuration

**Purpose.** Describe how a real Indian school runs a full academic year in Operion, from first onboarding to academic-year closure. For every step, state what Operion should already know, what the school must decide, and what Operion should do automatically.

**Audience.** Operion product team: deciding what ships out of the box, what stays configurable, and what gets automated.

**Scope.** School operations only. No roles/permissions, architecture, APIs, databases, AI, or implementation detail. A short "where Operion stands today" appendix (Appendix A) is the only place that refers to the current build.

**Design principle (from the multi-industry direction).** Everything below is delivered as a **School Starter Pack**: a selectable, editable preset applied at onboarding. It is not hard-wired into the product. Another industry would get its own pack. Nothing here should be hard-coded as "the only way".

---

## 1. Conventions

### 1.1 The five value categories

| Code | Category | Meaning |
|---|---|---|
| **A** | System Predefined | Operion provides it. The school never sees it as a task. Not editable (legal/standard lists). |
| **B** | Predefined but Editable | Operion ships a sensible default; school may rename, add, remove, or change it. |
| **C** | School Configuration | The school must make a decision Operion cannot guess (kept as small as possible). |
| **D** | School Data | Real records only the school has (students, staff, balances). Entered or imported. |
| **E** | Derived Automatically | Operion calculates or generates it from other information. |

### 1.2 Standard workflow block

Every workflow uses: **Purpose → Prerequisites → Procedure → Result → Next step**, followed by a predefined-values table:

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|

### 1.3 The three "starter choices" that unlock most defaults

Most defaults can be pre-filled once the school answers three questions in onboarding. These drive almost everything below:

1. **Board / curriculum**: CBSE, ICSE/ISC, a State Board (which one), IB, Cambridge, NIOS.
2. **State/UT where the school is located**: drives holidays, language options, fee/regulatory norms, academic-year start convention.
3. **Class range offered**: e.g. Nursery–5, 1–10, LKG–12 (with streams for 11–12).

---

## 2. Lifecycle Map

| # | Stage | Typical timing (Indian school) | Frequency |
|---|---|---|---|
| 1 | Initial school setup | Once, before go-live | Once |
| 2 | Academic year creation | Jan–Mar for April start | Yearly |
| 3 | Academic structure (terms, calendar, working days) | With year creation | Yearly |
| 4 | Classes and sections | Before admissions open | Yearly (adjusted) |
| 5 | Subjects and curriculum | Before timetable | Yearly (mostly reused) |
| 6 | Student admission | Enquiry from Oct–Nov; admission Jan–Apr; rolling after | Yearly + rolling |
| 7 | Parent/student information | At admission | Per student |
| 8 | Teacher/staff setup | Feb–Apr | Yearly (changes) |
| 9 | Student allocation (class/section/roll no.) | Mar–Apr | Yearly |
| 10 | Fee configuration | Before admission fee collection | Yearly |
| 11 | Timetable | Late Mar–Apr | Yearly + mid-year edits |
| 12 | Attendance | Daily | Daily |
| 13 | Examinations | Per term; PT/Mid/Final | Several per year |
| 14 | Marks and results | After each exam | Several per year |
| 15 | Communication | Continuous | Continuous |
| 16 | Parent activities (portal, PTM, leave, payments) | Continuous | Continuous |
| 17 | Daily school operations (diary, substitutions, transport, library, visitors) | Daily | Daily |
| 18 | Finance (fees, receipts, dues, expenses, payroll) | Monthly/quarterly cycles | Continuous |
| 19 | Reports | Daily/monthly/term/annual | Continuous |
| 20 | Student transfer / withdrawal | Any time | Per event |
| 21 | Student promotion | Feb–Mar after finals | Yearly |
| 22 | New academic year preparation | Feb–Mar (overlaps 21) | Yearly |
| 23 | Year-end activities (certificates, board/regulatory data, archival, alumni) | Mar–Apr | Yearly |
| 24 | Academic year closure | Late Mar–Apr, after dues reconciliation | Yearly |

Additional workflows a typical Indian school ERP needs, covered in Part 3.2: enquiry & lead tracking (X1), admission test/interview (X2), concessions & scholarships (X3), house system & clubs (X4), certificates (X5), staff leave & staff attendance (X6), payroll (X7), library (X8), transport (X9), inventory/uniform/book store (X10), parent-teacher meetings & events (X11), visitor/gate/pickup (X12), board registration & regulatory data (X13), hostel, if offered (X14), alumni (X15).

---

## 3. Workflows

### 3.1 The 24 core stages

---

#### W1. Initial School Setup

**Purpose.** Establish the school's identity so every printout, message, and report carries correct details.

**Prerequisites.** School registration/affiliation details at hand.

**Procedure.**
1. Choose the Starter Pack (School) and answer the three starter choices (board, state, class range).
2. Enter school identity: name, address, contact, logo, affiliation number, school code, UDISE code, principal's name (for signatures), website.
3. Add campuses/branches if more than one.
4. Choose the academic-year convention (default April–March; June start is offered for states where that is common).
5. Confirm defaults Operion pre-filled (calendar pattern, grading scale, attendance statuses, fee heads, templates) by reviewing a single summary screen, not 20 separate configuration pages.

**Result.** A working school with defaults applied, ready for an academic year.

**Next step.** W2 Academic Year Creation.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Country, currency (INR), time zone (IST), date format (DD/MM/YYYY), language | India defaults | Nothing normally | A/B | Multi-language UI optional later |
| States/UTs, districts | Complete lists | Pick the school's state/district | A | |
| Boards | CBSE, ICSE/ISC, State Boards, IB, Cambridge, NIOS | Select | A→C(select) | |
| Medium of instruction | English, Hindi + 22 scheduled languages | Select | A→C(select) | |
| School type (Co-ed/Boys/Girls; Day/Residential) | Options | Select | A→C(select) | |
| School name, address, logo, affiliation no., UDISE code, signatory | None | Enter | C/D | Cannot be guessed |
| Number format (admission no., receipt no.) | Pattern like `ADM/2026-27/0001` | Optionally change prefix | B/E | |
| Letterhead/print layout | Standard A4 layout using logo + address | Optional tweak | B | |

---

#### W2. Academic Year Creation

**Purpose.** Create the container within which classes, fees, exams, and attendance live.

**Prerequisites.** W1 complete. For year 2 onward: previous year exists.

**Procedure.**
1. Create the year (default label `2026-27`, April 1 to March 31 pre-filled from convention).
2. Choose term pattern: Annual, 2 Semesters, 3 Terms, 4 Quarters.
3. Set term start/end dates (pre-filled by pattern; school adjusts).
4. Choose "start fresh" or "copy from previous year" (classes, sections, subjects, fee structures, timetable periods, exam scheme, templates).
5. Save as **Draft**; activate on the first day of operation.

**Result.** A Draft/Active academic year with terms.

**Next step.** W3 Academic Structure.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Year label | `YYYY-YY` suggested from current date | Confirm | E | |
| Year dates | 1 Apr–31 Mar (June variant offered) | Adjust if different | B | |
| Term patterns | Annual / 2 Semesters / 3 Terms / 4 Quarters | Select one | A→C(select) | |
| Term dates | Typical splits (e.g. Apr–Sep / Oct–Mar) | Adjust | B | |
| Year status flow | Draft → Active → Closed → Archived | Nothing | A | |
| Copy from previous year | Copy engine | Choose what to copy | E | Biggest setup saver from year 2 |

---

#### W3. Academic Structure (Calendar, Working Days, Periods)

**Purpose.** Define when the school runs: working days, holidays, events, period pattern.

**Prerequisites.** W2.

**Procedure.**
1. Choose the working week (Mon–Fri / Mon–Sat / Mon–Sat with alternate Saturdays off). Default Mon–Sat.
2. Review pre-loaded holidays: national holidays plus state-specific and common festival holidays for the school's state. School marks which it observes and adds local ones.
3. Add vacations (summer, winter/Diwali, Dussehra, etc.), exam windows, PTM dates, annual day, sports day.
4. Define the school day: start/end time, assembly, number of periods, period length, breaks.
5. Publish the calendar.

**Result.** Calendar of working days; the basis for attendance %, fee due dates, and timetable.

**Next step.** W4 Classes and Sections.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| National holidays | 26 Jan, 15 Aug, 2 Oct each year | Confirm | A/B | Dates are fixed |
| State & festival holidays | Suggested list per state per year (Diwali, Holi, Eid, Christmas, Pongal, Onam, etc.) | Tick observed ones; add local | B | Lunar dates change yearly: Operion should refresh the suggestion list each year |
| Working week | Mon–Sat default; 5-day and alternate-Saturday patterns | Select | B | |
| Period template | 8 periods × 40 min, assembly, 2 breaks (short/lunch) | Times, count | B | Multiple templates (primary vs senior) allowed |
| Working-day count per month/term | Calculated from week pattern − holidays | Nothing | E | Needed for attendance % |
| Event types | Exam, PTM, Annual Day, Sports Day, Excursion, Holiday, Vacation | Add school-specific | B | |

---

#### W4. Classes and Sections

**Purpose.** Create the teaching groups students belong to.

**Prerequisites.** W2; class range chosen in W1.

**Procedure.**
1. Operion shows the standard class ladder trimmed to the chosen range (Nursery/LKG/UKG, 1–12).
2. For each class, school chooses number of sections (names default A, B, C…), capacity per section, class teacher, room.
3. For 11–12, choose streams offered (Science, Commerce, Humanities) and section per stream.
4. Optionally copy from last year; adjust strengths.

**Result.** Classes and sections (e.g. Class 6-A, 2026-27) ready for allocation.

**Next step.** W5 Subjects, then W6 Admission.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Class ladder | Pre-Nursery/Nursery, LKG, UKG, Class 1–12; stages (Pre-primary, Primary 1–5, Middle 6–8, Secondary 9–10, Senior Secondary 11–12) | Select the range offered | B | School may rename (e.g. "Nursery" → "Play Group") |
| Section names | A, B, C… | Count per class | B | |
| Streams (11–12) | Science (PCM/PCB/PCMB), Commerce, Humanities | Select | B | |
| Default section capacity | 40 (editable by class) | Confirm | B | Strength norm varies by board/state |
| Class eligibility age | Entry-age rule per class (e.g. Class 1: 6+ as of cut-off) | Confirm cut-off date and rule | B/E | Rules vary by state; use state default |
| Class teacher, room | None | Assign | C/D | |

---

#### W5. Subjects and Curriculum

**Purpose.** Define what is taught, per class, with how it is assessed.

**Prerequisites.** W4.

**Procedure.**
1. Operion suggests a subject bundle for each class based on board and class (e.g. CBSE Class 6: English, Hindi, Mathematics, Science, Social Science, Sanskrit/3rd language, Computer Science, Art Education, Physical Education, Value Education).
2. School accepts, removes, or adds subjects. Marks subjects as scholastic/co-scholastic, compulsory/elective, and language order (1st/2nd/3rd).
3. For electives (e.g. 11–12), define elective groups and let students choose later.
4. Set weekly periods per subject (default suggested).
5. Assign teacher(s) per subject per section (can be deferred until W8).

**Result.** Curriculum per class and section for the year.

**Next step.** W6 Admission, W11 Timetable.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Subject catalogue | Platform-wide standard catalogue (English, Hindi, regional languages, Maths, EVS, Science, Physics, Chemistry, Biology, SST/History/Geography/Civics/Economics, Computer Science/IT, Sanskrit, Art, Music, Dance, PE, Value Education, GK, Moral Science, Accountancy, Business Studies, etc.) | Select what is taught; add rare ones | A + C(select) | Resolved decision: shared catalogue, not per-org presets |
| Board-suggested bundle per class | Suggested set per board + class | Confirm | B | |
| Subject type | Scholastic / Co-scholastic / Activity | Confirm | B | |
| Language order (1st/2nd/3rd) | Options | Set per class | B | Three-language formula varies by state |
| Weekly periods per subject | Suggested counts | Adjust | B | |
| Max marks per subject | 100 (80+20 internal for 9–12 by CBSE pattern; 100 for others); overridable | Adjust if different | B | |
| Teacher assignment | None | Assign | C | |
| Syllabus/lesson plan | Optional | Optional | D | |

---

#### W6. Student Admission

**Purpose.** Take a candidate from enquiry to an admitted, enrolled student.

**Prerequisites.** W2, W4, W10 (admission fee head at least). Enquiry stage (X1) is optional.

**Procedure.**
1. Capture the application (online form for parents or entered by the office). Operion offers a standard admission form.
2. Verify age eligibility for the applied class (calculated from date of birth).
3. Collect documents (digitally uploaded or marked "to be submitted").
4. Schedule test/interview if used (X2). Record outcome.
5. Decide: Offer, Waitlist, Reject.
6. On acceptance and payment of admission/registration fee: **Admit**. Operion assigns the admission number, creates the student record, creates the parent link, assigns the class/section, applies the fee structure.
7. Print/send admission confirmation.

**Result.** An admitted student, linked parent(s), enrolled in a class/section, with fees applied.

**Next step.** W7 (complete profile), W9 (allocation), W10 (fees).

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Admission statuses | Enquiry, Applied, Test/Interview Scheduled, Shortlisted, Offered, Accepted, Admitted, Waitlisted, Rejected, Withdrawn | Rename if wanted | B | |
| Standard admission form fields | Child (name, DOB, gender, blood group, nationality, mother tongue, religion, category, Aadhaar [optional], previous school), Parent(s), address, emergency contact, medical info, transport need, siblings | Add/remove fields, mark mandatory | B | Aadhaar must be optional/masked |
| Admission number | Auto-generated by pattern | Prefix | E/B | |
| Application number | Auto | | E | |
| Class applied for | From W4 | | E | |
| Age eligibility | Calculated | Cut-off date, min age | E/B | |
| Seat vacancy per class | Calculated from capacity − enrolled | Reserved quotas (RTE/EWS 25%, management, staff ward) | E + C | RTE/EWS quota is statutory for private unaided schools in most states |
| Application fee | None | Amount | C | |
| Required documents per class | Standard checklist (birth certificate, previous TC/report card, photo, address proof, parent ID, medical/vaccination, category/income certificate where applicable) | Mark mandatory vs optional | B | |
| Admission offer/rejection/waitlist letters | Standard templates | Edit wording | B | |

---

#### W7. Parent / Student Information

**Purpose.** Hold one clean record per student and family, entered once and reused everywhere.

**Prerequisites.** W6 (or direct import).

**Procedure.**
1. Complete student profile: personal, medical (allergies, conditions, doctor), identity (APAAR/PEN, Aadhaar optional), previous school.
2. Add father, mother, and guardian(s) with phone/email, occupation, and who is the primary contact/fee payer.
3. Link siblings automatically by shared parent identity.
4. Upload/verify documents.
5. Parent receives a portal invitation.

**Result.** Complete student + family profile, parent portal active.

**Next step.** W9, W10, W15.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Gender | Male, Female, Other | Nothing | A | |
| Blood group | A+, A−, B+, B−, AB+, AB−, O+, O−, Unknown | Nothing | A | |
| Religion | Hindu, Muslim, Christian, Sikh, Buddhist, Jain, Parsi, Other, Not disclosed | Nothing | A | Optional field |
| Social category | General, EWS, OBC (NCL), SC, ST, Other | Nothing | A | Optional; some states add sub-lists |
| Minority status, Differently-abled (CWSN) flag + type | Yes/No + standard disability types | Nothing | A | Needed for regulatory data |
| Mother tongue / languages | 22 scheduled languages + Other | Nothing | A | |
| Nationality | Indian default; countries list | Nothing | A | |
| Parent relationship types | Father, Mother, Guardian, Grandparent, Sibling, Other | Nothing | A | |
| Occupation, qualification | Short standard lists + Other | Nothing | A | |
| Document types | Birth Certificate, Aadhaar (optional), Transfer Certificate, Previous Report Card, Passport Photo, Address Proof, Caste Certificate, Income Certificate, Medical/Vaccination Record, Parent ID, Other | Add school-specific | B | |
| Document verification status | Pending, Verified, Rejected | Nothing | A | |
| Student status | Active, Promoted, Detained, Transferred Out, Withdrawn, Passed Out, Alumni, Inactive | Nothing | A | |
| Extra fields (house, club, hobbies, scholarship ID) | Custom-field mechanism | Define what school needs | C | Generic mechanism, no school hardcoding |
| Student ID / PEN / APAAR | Fields exist | Enter/import | D | |
| Age, siblings, family fee-payer grouping | Calculated | Nothing | E | |

---

#### W8. Teacher / Staff Setup

**Purpose.** Make staff available for class teacher, subject teacher, timetable, attendance, payroll.

**Prerequisites.** W1.

**Procedure.**
1. Add staff (individually or import): personal, qualification, joining date, department, designation, employment type, documents.
2. Mark teaching vs non-teaching; for teachers record subjects and classes they can teach, workload limits.
3. Add staff bank/statutory details for payroll (if payroll is used).
4. Staff receive an invitation to the app.

**Result.** Staff directory; teachers ready for assignment.

**Next step.** W5/W9 assignments, W11 timetable.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Departments | Administration, Academics, Finance & Accounts, HR, Operations, Transport, Library, IT, Examination, Admissions | Add/remove | B | |
| Designations | Principal, Vice Principal, Headmistress/Headmaster, Coordinator, HOD, PGT, TGT, PRT, Pre-primary Teacher, Assistant Teacher, Sports Teacher, Counsellor, Accountant, Clerk, Librarian, Lab Assistant, Transport In-charge, Driver, Conductor, Attendant (Aaya), Peon, Security, Support Staff | Add/remove | B | PGT/TGT/PRT are standard Indian designations |
| Staff type | Teaching, Non-teaching, Administrative, Support | Nothing | A | |
| Employment type | Permanent, Probation, Contract, Part-time, Visiting/Guest | Nothing | A | |
| Staff status | Active, On Leave, Resigned, Terminated, Retired | Nothing | A | |
| Staff document types | ID proof, Educational certificates, Experience letters, Appointment letter, PAN, Police verification (for drivers/support), B.Ed./TET certificate | Add | B | |
| Staff ID | Auto-generated | Prefix | E | |
| Workload norms | Suggested max periods/week (e.g. 30–35) | Adjust | B | |
| Actual staff data | None | Import | D | |

---

#### W9. Student Allocation (Class, Section, Roll Number, House)

**Purpose.** Place every student in the right section for the year and assign class-level identifiers.

**Prerequisites.** W4, W6 or promotion output (W21).

**Procedure.**
1. For new admissions: Operion proposes a section (balanced by strength and, optionally, gender/alphabet); office confirms or moves.
2. For continuing students: placement comes from promotion (W21).
3. Roll numbers generate (alphabetical by default).
4. Houses assigned (round-robin/balanced) if the school uses houses.
5. Elective choices captured for 11–12.
6. Transport and hostel flags confirmed.
7. Class teachers review and publish class lists.

**Result.** Class lists with roll numbers and houses; the base for attendance, exams, and fees.

**Next step.** W10, W11, W12.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Section allocation rule | Balanced by strength; alternatives: alphabetical, by merit, manual | Choose | B | |
| Roll number rule | Alphabetical, regenerate on change | Choose (alphabetical / gender-wise / admission order) | B/E | |
| Class lists | Generated | Nothing | E | |
| House names | None (generic "Houses" with colour) | Names/colours | C | School-specific; no default names |
| Enrolment status | Enrolled, Promoted, Detained, Transferred, Dropped, Completed | Nothing | A | |

---

#### W10. Fee Configuration

**Purpose.** Define what every student is charged, when, and under what rules.

**Prerequisites.** W4 (class), W2 (terms).

**Procedure.**
1. Review fee heads (pre-listed). Tick the ones the school charges.
2. For each class (and category, e.g. new vs old student), enter amounts per head and frequency.
3. Set instalment schedule and due dates (pre-filled from the term pattern: monthly on the 10th, quarterly, half-yearly, annual).
4. Set late-fee rule, grace days, and reminder schedule.
5. Set concession types and approval rule (X3).
6. Configure transport fee slabs by route/distance (X9).
7. Choose payment modes; set receipt numbering and tax treatment where applicable.
8. Preview "what Class 6 student Priya will be charged" before saving.

**Result.** Fee structures per class, ready to auto-apply on enrolment.

**Next step.** Applied at admission/allocation; collection in W18.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Fee heads | Admission/Registration, Tuition, Annual/Development, Examination, Transport, Library, Laboratory/Computer, Sports/Activity, Uniform, Books & Stationery, Hostel, Security Deposit (refundable), Late Fee, Miscellaneous | Select used heads; add school-specific | B | Fee categories exist today; heads list is broader |
| Frequency | One-time, Monthly, Quarterly, Half-yearly, Annual | Per head | A→C(select) | |
| Due dates | Derived from the term pattern + chosen frequency | Adjust | E/B | |
| Fee amounts | None | Enter per class (or import) | C/D | |
| Late fee rule | Options: fixed per day, per month, % of due, capped | Choose and enter value | C | |
| Payment modes | Cash, Cheque, DD, UPI, Card, Net Banking, Bank Transfer, Online Gateway | Select | B | |
| Payment status | Pending, Partial, Paid, Overdue, Refunded, Cancelled | Nothing | A | |
| Concession types | Sibling, Staff Ward, Merit, Sports, Single Girl Child, Defence Ward, RTE/EWS Free-seat, Financial Hardship | Select, set % / amount | B | |
| Refund rules | Standard reasons list | Percentage policy | C | |
| Receipt/invoice number | Auto pattern (`REC/2026-27/000123`) | Prefix | E/B | |
| Fee reminders | Default: 7 days before, on due date, 3 days after, weekly | Adjust | B | |
| Tax on fees | Education services by recognised schools are exempt | Confirm for non-exempt items (transport, uniforms) | B | Confirm with school's accountant |

---

#### W11. Timetable

**Purpose.** Place subjects, teachers, rooms into the period grid without clashes.

**Prerequisites.** W3 (periods), W5 (subjects, weekly counts), W8 (teachers), W9 (sections).

**Procedure.**
1. Pick a period template per class group.
2. Auto-generate a draft timetable from subject weekly counts and teacher availability (or build manually).
3. Resolve flagged clashes (teacher double-booked, room clash, over-limit workload).
4. Fix special slots (assembly, games, library, lab).
5. Publish. Teachers and parents see it.
6. During the year: record substitutions when a teacher is absent (W17).

**Result.** Published weekly timetable per section and per teacher.

**Next step.** Daily attendance (W12) and substitution (W17).

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Period template | From W3 | | B | |
| Subject frequency per week | Suggested | Adjust | B | |
| Special periods | Games, Library, Art, Music, Computer Lab, Value Education, Assembly | Select | B | |
| Clash detection | Teacher, room, section | Nothing | E | |
| Teacher availability | Derived from leaves and workload | Mark unavailable | E/C | |
| Substitution | Suggest free teachers by subject match | Approve | E | |

---

#### W12. Attendance

**Purpose.** Know who is present every day; drive parent alerts, exam eligibility, and reports.

**Prerequisites.** W3, W9.

**Procedure.**
1. Class teacher opens today's list (pre-filled "Present" to save taps; exceptions are marked).
2. Mark Absent / Late / Half-day / On Leave.
3. Submit before the cut-off time. Operion tells parents of absent children.
4. Approved leave applications from parents pre-mark attendance.
5. Corrections within the allowed window; after that, locked.
6. Monthly percentage computed; low-attendance flagged.

**Result.** Daily attendance record, parent notifications, monthly %.

**Next step.** W19 Reports; exam eligibility (W13); promotion (W21).

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Attendance statuses | Present, Absent, Late, Half-day (morning/afternoon), On Leave (approved), On Duty / School Activity, Holiday, Not Marked | Add if needed | B | |
| Attendance mode | Daily (default) or period-wise | Choose | B | |
| Cut-off time to submit | Suggested (e.g. 10:00) | Confirm | B | |
| Late-arrival threshold | e.g. after 15 minutes | Confirm | B | |
| Correction window | e.g. 3 days, then lock; unlock by exception | Confirm | B | |
| Minimum attendance | 75% (common board norm for exam eligibility) | Confirm | B | |
| Monthly %, working days | Calculated: present days ÷ working days | Nothing | E | Holiday-aware |
| Absentee alert | Default: same-day message to primary parent for absent child | Channel, timing | B | |
| Staff attendance | Same statuses + check-in time | Shift timings | B | |

---

#### W13. Examinations

**Purpose.** Plan and run assessments.

**Prerequisites.** W5, W9, W3 (exam window).

**Procedure.**
1. Define the **exam scheme** for the year (which exams, in which term, weightage): suggested by board.
2. Create exam instances per class (e.g. Mid-Term, Class 6).
3. Build the date sheet: subject, date, time, duration, room; Operion checks clashes.
4. Assign invigilators.
5. Publish date sheet to parents; generate hall tickets (eligibility checked: attendance %, dues if school enforces).
6. Conduct exam; handle absentees/make-up exams.

**Result.** Published exam schedule, hall tickets.

**Next step.** W14 Marks and Results.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Exam types | Unit/Periodic Test, Mid-Term / Half-Yearly, Pre-Board, Final / Annual, Practical, Internal Assessment, Project, Oral, Make-up | Add school-specific | B | |
| Exam scheme per board | Suggested: CBSE pattern (PT + Notebook + Subject Enrichment + Half-yearly + Annual); ICSE; State patterns; simple "3 terms" | Choose and adjust weightage | B | |
| Max marks / pass marks | 100/33 default (33% pass is the common board norm) | Adjust | B | |
| Exam status | Draft, Scheduled, Ongoing, Completed, Marks Entered, Published | Nothing | A | |
| Date sheet | Template with clash detection | Dates | C + E | |
| Hall ticket | Standard layout | Notes/instructions | B | |
| Eligibility rule | Min. attendance | Whether dues block hall tickets | C | Policy choice |

---

#### W14. Marks and Results

**Purpose.** Capture marks, convert to grades, produce report cards.

**Prerequisites.** W13.

**Procedure.**
1. Subject teachers enter marks (or upload) per exam; absent/exempt flagged.
2. Operion validates (not above max, none missing).
3. Class teacher reviews; co-scholastic grades, remarks, and discipline/attendance summary added.
4. Operion computes totals, weighted marks, percentage, grade, rank (optional), pass/fail.
5. Moderation/lock; publish results.
6. Print/share report cards; parents view in app.
7. Re-evaluation requests handled via correction workflow.

**Result.** Published results and report cards; marks history.

**Next step.** W15 Communication; W21 Promotion decision.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Grading scale | See catalogue: **Percentage bands (A+ … F)**; **CBSE 9-point (A1 91–100, A2 81–90, B1 71–80, B2 61–70, C1 51–60, C2 41–50, D 33–40, E1 21–32, E2 0–20)**; **5-point (A–E)**; **Primary descriptive (Excellent/Very Good/Good/Needs Improvement)**; **CGPA** | Pick one per class group | A+B | Choose by board/class |
| Co-scholastic grading | A / B / C (or A–E) for art, music, PE, discipline | Select | B | |
| Pass/fail rule | 33% per subject; overall per board | Adjust | B | |
| Rank rule | Class rank on/off | Choose | C | Many schools disable ranks for primary |
| Grace/moderation rule | None | Optional | C | |
| Report card template | Board-style standard templates (Primary, Middle, Secondary) with remarks, attendance, co-scholastic section | Logo, remarks wording | B | |
| Result states | Draft, Reviewed, Published | Nothing | A | |
| Computations | Totals, %, grade, rank, GPA | Nothing | E | |
| Remarks bank | Common remark suggestions per grade band | Edit | B | Saves teachers time |

---

#### W15. Communication

**Purpose.** Reach parents, students, and staff reliably and in the right channel.

**Prerequisites.** W7 (contacts), parent consent.

**Procedure.**
1. Choose channels (app notification, email, SMS, WhatsApp). SMS needs the school's registered sender ID and templates (India DLT rules).
2. Use template library: pick a template, choose audience (school/class/section/individual/route/house), preview recipients, send or schedule.
3. Automatic messages fire from events (absence, fee due, result published).
4. Two-way messaging between parent and class teacher with office hours.
5. Emergency broadcast override.

**Result.** Messages delivered, tracked (sent/delivered/read), logged.

**Next step.** Continuous.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Channels | App, Email, SMS, WhatsApp | Enable what the school has credentials for | C | Provider accounts are the school's |
| Template library | Absence alert, late arrival, fee due/overdue/receipt, admission offer/confirmation, welcome/portal invite, holiday notice, exam date sheet, result published, PTM invitation, event reminder, bus delay/route change, emergency closure, birthday wish, leave approved/rejected, document pending | Edit wording; language | B | Ship with DLT-ready wording and variable placeholders |
| Audience groups | School, Class, Section, Stream, Route, House, Staff, Individual, Custom | Nothing | A | |
| Merge fields | {student}, {class}, {amount}, {due_date}, {date}, {school} | Nothing | A | |
| Languages | English + Hindi + regional | Select | B | |
| Quiet hours | e.g. 9 pm–7 am, except emergency | Confirm | B | |
| Opt-in/consent | Captured at admission | Nothing | E | |
| Sender ID / templates registration | None | Register with provider | C | Cannot be automated |
| Announcement scheduling | Built-in | Nothing | A | |

---

#### W16. Parent Activities

**Purpose.** Let parents self-serve instead of calling the office.

**Prerequisites.** W7 (portal invite).

**Procedure.** Parent: views child's attendance, timetable, homework, results, fee dues; pays fees online; downloads receipts and report cards; applies for leave; requests certificates; books PTM slots; reads announcements; messages teachers; updates contact details (office approves); views bus status.

**Result.** Fewer office queries; requests logged and tracked.

**Next step.** Continuous.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Parent portal features | Standard set (above) | Switch off items not used | B | |
| Leave request types | Sick, Family function, Travel, Medical appointment, Other | Add | B | |
| Request types | Bonafide, Character, TC, Fee certificate, Address update, ID card replacement | Add | B | |
| PTM slots | Slot generator (e.g. 10 min each) | Date, teachers | C + E | |
| Parent account creation | Automatic on admission | Nothing | E | |
| Sibling switch | One login, all children | Nothing | E | |

---

#### W17. Daily School Operations

**Purpose.** Everything that happens between the formal modules, day to day.

**Prerequisites.** Timetable, attendance.

**Procedure.**
1. Morning: attendance, bus run starts, staff attendance.
2. Teachers: homework/diary, lesson notes, assignments.
3. Substitutions for absent teachers.
4. Library issue/return.
5. Visitor and early-pickup logging (X12).
6. Discipline / incident notes; sick-room / health incidents.
7. End of day: homework published, bus drop confirmations, fee desk receipts reconciled.

**Result.** Daily operational record.

**Next step.** W19 reports.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Homework/diary types | Homework, Classwork, Project, Reading, Reminder | Add | B | |
| Incident categories | Discipline, Health, Safety, Property damage, Bullying | Add | B | |
| Health incident handling | Standard fields; notify parent | Nothing | A/B | |
| Daily summary | Generated | | E | |

---

#### W18. Finance

**Purpose.** Collect fees, track dues, handle refunds, run expenses and payroll.

**Prerequisites.** W10, W9.

**Procedure.**
1. Invoices generate from fee structures on due dates.
2. Collect payment (online by parent, or at the counter): receipt issued automatically.
3. Partial payment, advance payment, and sibling-combined payment allowed.
4. Overdue → reminders → late fee → escalation list for the office.
5. Concessions/waivers applied with approval; refunds processed.
6. Daily collection reconciliation by mode; day-close.
7. Expenses and purchases recorded; payroll run monthly (X7).
8. Monthly/annual financial reports.

**Result.** Accurate dues, receipts, collection and ledger records.

**Next step.** W19; year-end dues carry-forward (W22/W24).

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Invoice generation | Automatic by schedule | Nothing | E | |
| Receipt format | Standard with school header, payer, head-wise split, mode | Footer text | B | |
| Day-close/reconciliation | Collection summary by mode | Nothing | E | |
| Dues carry-forward | Automatic to next year | Policy (carry or write off) | C | |
| Expense categories | Salaries, Utilities, Maintenance, Stationery, Events, Transport, Repairs, Other | Add | B | |
| Bank accounts | None | Enter | C/D | |
| Payment gateway | None | Connect the school's own account | C | |
| Opening balances | None | Import (see Part 7) | D | |

---

#### W19. Reports

**Purpose.** Give management, teachers, and regulators the numbers they need.

**Prerequisites.** Data from the above.

**Procedure.** Select a ready-made report, choose year/class/date range, view or export (PDF/Excel), schedule recurring delivery, or save custom variants.

**Result.** Printable/exportable reports.

**Next step.** Used in W21, W23.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Standard reports | **Students:** strength by class/section, gender/category split, birthday list, new admissions, TC issued. **Attendance:** daily, monthly register, low-attendance, staff attendance. **Fees:** collection (daily/head/mode), defaulters, head-wise, concession, refund, outstanding ageing. **Exams:** marks sheet, subject analysis, toppers, result summary, pass percentage. **Staff:** list, leave, payroll summary. **Transport:** route-wise, bus occupancy. **Library:** issued, overdue. **Inventory:** stock, low stock | Pick favourites | A | |
| Printable documents | Report card, hall ticket, ID card, fee receipt, bonafide, character, TC, fee certificate, attendance certificate | Letterhead | B | |
| Report scheduling | Weekly principal summary, daily fee collection | Choose | B | |
| Regulatory/board data export | Student, staff, infrastructure data in board formats | Fill missing fields | C | See X13 |

---

#### W20. Student Transfer / Withdrawal

**Purpose.** Cleanly exit a student mid-year or at year end, with dues cleared and certificates issued.

**Prerequisites.** Admitted student; parent request.

**Procedure.**
1. Parent submits TC/withdrawal request (or office records it) with reason and last working date.
2. Operion runs a **clearance checklist**: fee dues, library books, uniform/store items, transport, lab/sports equipment.
3. Office clears each item; remaining dues must be settled or waived.
4. Prepare the TC: details, last class, conduct, last attendance date, promotion status, reason, fee-paid-up-to date.
5. Approval by the principal; issue with a number and duplicate-copy control.
6. Student status changes to Transferred Out/Withdrawn; seat freed; parent portal restricted to read-only; fee schedule stopped; transport removed.

**Result.** TC issued, student exited, records preserved.

**Next step.** Seat released to waitlist (W6).

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Exit reasons | Relocation, Change of school, Financial, Dissatisfaction, Board change, Health, Other | Add | B | |
| Clearance checklist | Fees, Library, Store, Transport, Lab/Sports | Remove/add | B | |
| TC template | Standard Indian TC layout (admission no., DOB in figures and words, category, class, conduct, qualified for promotion, etc.) | Signatory | B | |
| TC numbering | Auto sequence per year | Prefix | E | |
| Conduct options | Excellent, Very Good, Good, Satisfactory | Nothing | A | |
| Status effects | Automatic | Nothing | E | |
| TC fee rule | None | Whether TC withheld for dues | C | Policy |

---

#### W21. Student Promotion

**Purpose.** Move students from the old year to the new year in a single controlled step.

**Prerequisites.** Final results published (W14); new year Draft with classes/sections (W22).

**Procedure.**
1. Open Promotion for a class. Operion lists every student with a **proposed outcome** from the school's rules: Promote / Detain / Pass out (Class 10 or 12) / Needs decision.
2. Class teacher reviews exceptions (grace, medical, retention).
3. Choose target section rule (keep together, rebalance, alphabetical, by merit).
4. Confirm. Operion creates next-year enrolments, new roll numbers, carried-forward dues, new fee structures, and new class lists.
5. Passed-out students move to the Alumni pool; TC/leaving certificates prepared.
6. Students leaving (no re-registration) flagged for X5.

**Result.** Next-year enrolment for all students, ready for allocation tweaks.

**Next step.** W9 adjustments, W24 closure.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Promotion criteria | Passed in required subjects + min. attendance (75%) | Adjust | B | |
| Outcomes | Promoted, Promoted with Grace, Detained/Repeater, Passed Out, Left School | Nothing | A | |
| Proposed outcome | Computed from results + attendance | Review exceptions only | E | |
| Class progression ladder | Nursery→…→12 | Nothing | A/E | |
| Section reshuffle rule | Same as W9 | Choose | B | |
| Re-registration/confirmation | Optional parent confirmation + advance fee | Whether to use | C | |

---

#### W22. New Academic Year Preparation

**Purpose.** Make next year ready before the old one closes.

**Prerequisites.** Current year in last term.

**Procedure.**
1. Create next year (W2) in Draft, copy structures.
2. Review class strengths and section counts using expected promotions + new admissions.
3. Revise fees (annual increase %), transport slabs, calendar, timetable template.
4. Update staff assignments (class teacher, subject teachers).
5. Open admissions for the new year (W6) in parallel.
6. Run Promotion (W21) after results.
7. Publish the new calendar, fee circular, and booklists.

**Result.** New year fully prepared and ready to activate.

**Next step.** Activate at start date; W24 closure of old year.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Copy-forward bundle | Classes, sections, subjects, fee heads, exam scheme, period template, templates, house setup | Choose what to copy | E | |
| Fee revision | Apply % increase option | Percentage | C | |
| Readiness checklist | Auto-generated: sections ready? teachers assigned? fees defined? calendar published? | Resolve gaps | E | |
| Circular/booklist | Template | Contents | B | |

---

#### W23. Year-End Activities

**Purpose.** Wrap up all outstanding obligations of the year.

**Prerequisites.** Results published.

**Procedure.**
1. Issue final report cards, certificates (character, bonafide, migration, leaving certificate).
2. Submit board/regulatory data (X13): student list, results, staff data.
3. Settle dues; decide carry-forward vs write-off.
4. Staff: appraisal data, leave encashment/balances carried forward, payroll closure.
5. Inventory stock-take; library stock-take and book recall.
6. Archive attendance, marks, and finance data for the year.
7. Annual reports for management/trust/board.
8. Alumni records created for passed-out students.

**Result.** Clean data for the year, ready to lock.

**Next step.** W24.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Year-end checklist | Standard list (above) | Add | B | |
| Leave balance carry-forward rules | Defaults per leave type | Confirm | B | |
| Annual reports | Pack of standard annual reports | Nothing | A | |
| Archive | Automatic on closure | Nothing | E | |

---

#### W24. Academic Year Closure

**Purpose.** Freeze the year so past data cannot be accidentally changed, and make the new year the working one.

**Prerequisites.** W21–W23 complete.

**Procedure.**
1. Operion runs a **closure readiness check**: attendance locked? results published? dues carried? promotions completed? pending TCs? open leave/requests?
2. School resolves flagged items or waives them with a note.
3. Confirm closure. Year moves Active → Closed (read-only), then Archived later.
4. New year becomes Active (if not already).
5. Final annual snapshot stored.

**Result.** Closed, read-only year; new year active.

**Next step.** Return to W2 for the following year.

| Configuration | Predefined by Operion | School needs to configure | Cat. | Notes |
|---|---|---|---|---|
| Closure checklist | Standard checks | Nothing | E | |
| Reopen after close | Exception for authorised correction with note | Policy | C | |
| Retention | Student data kept per statutory norms | Nothing | A | |

---

### 3.2 Additional workflows typical Indian schools need

Each uses the same five-part structure, shortened for brevity.

#### X1. Enquiry & Lead Tracking
- **Purpose:** Capture parent enquiries (phone, walk-in, website, campaign), follow up, convert to application.
- **Prerequisites:** W4.
- **Procedure:** Log enquiry → assign follow-up owner and date → school tour/visit → send brochure → convert to application (data carried over, not retyped).
- **Result:** Funnel visibility; fewer lost leads.
- **Next step:** W6.
- **Predefined:** Lead sources (Walk-in, Phone, Website, Referral, Social media, Newspaper, Hoarding, Other) [B]; stages (New, Contacted, Visited, Applied, Admitted, Lost) [B]; lost reasons [B]; follow-up reminders [E]; conversion % [E].

#### X2. Admission Test / Interview
- **Purpose:** Evaluate candidates where the school uses tests/interviews.
- **Prerequisites:** W6 applications.
- **Procedure:** Create test slot → notify candidates → mark attendance → enter score/remarks → shortlist by cut-off.
- **Result:** Ranked shortlist.
- **Next step:** Offers (W6).
- **Predefined:** Test types (Written, Oral, Interaction, Parent interview, Observation for pre-primary) [B]; result outcomes (Selected, Waitlisted, Not selected) [A]; slot generator [E]. School sets: cut-offs, questions [C].

#### X3. Concessions & Scholarships
- **Purpose:** Apply discounts consistently with approvals.
- **Prerequisites:** W10.
- **Procedure:** Select concession type → attach proof → approval → auto-apply to relevant fee heads for the year → renew annually with re-verification.
- **Result:** Reduced dues with audit trail.
- **Next step:** Fee calculations (W18).
- **Predefined:** Concession type list (see W10) [B]; value types (% / flat / full waiver) [A]; approval chain template [B]; proof checklist [B]; RTE/EWS free-seat handling (no fee, quota counted) [A/B]; auto-renewal prompt each year [E]. School sets amounts/eligibility [C].

#### X4. House System, Clubs & Co-curriculars
- **Purpose:** Run houses, clubs, sports teams and track participation.
- **Procedure:** Define houses/clubs → auto-assign students → record points/events → publish leaderboard.
- **Result:** House points, participation records for certificates.
- **Predefined:** Mechanism and balanced auto-assign [A/E]; common activity categories (Sports, Arts, Music, Dance, NCC, Scouts & Guides, Eco club, Debate, Science club) [B]. School supplies house names/colours [C].

#### X5. Certificates
- **Purpose:** Issue bonafide, character, TC, fee, date-of-birth, migration, study certificates, ID cards, bus pass.
- **Procedure:** Request (parent or office) → auto-fill from records → approve → print with serial number and QR → log.
- **Predefined:** Standard templates and numbering [B]; certificate register [E]. School sets: signatory, seal [C].

#### X6. Staff Leave & Staff Attendance
- **Purpose:** Manage staff availability fairly.
- **Procedure:** Staff applies → reporting head approves → balance deducted → timetable substitution suggested → payroll uses attendance.
- **Predefined:** Leave types: Casual (CL), Sick (SL), Earned (EL), Maternity (26 weeks standard), Paternity, Loss of Pay, Compensatory Off, On Duty, Study/Exam Leave [B]; suggested yearly quotas (e.g. CL 12, SL 10) [B]; holiday-aware accrual [E]; statuses Pending/Approved/Rejected/Cancelled [A]. School sets quotas if different [B/C].

#### X7. Payroll
- **Purpose:** Pay staff accurately and on time.
- **Prerequisites:** W8, staff attendance.
- **Procedure:** Define salary structure per designation → monthly run (attendance + leave → LOP) → review → approve → payslips → bank file.
- **Predefined:** Standard components (Basic, HRA, DA, Conveyance, Special Allowance, PF, ESI, Professional Tax, TDS, Advance recovery) [B]; statutory rate defaults (PF, ESI, professional tax by state) [B, review yearly]; payslip template [B]; LOP calculation [E]. School sets salary amounts [C/D].

#### X8. Library
- **Purpose:** Track books, issues, returns, fines.
- **Procedure:** Catalogue books (import) → issue to students/staff against limits → return → fine on overdue.
- **Predefined:** Categories (Fiction, Non-fiction, Reference, Textbook, Periodical, Comics, Story) [B]; issue limits (e.g. 2 books/14 days) [B]; fine per day [B]; overdue reminders [E]. School sets book data [D].

#### X9. Transport
- **Purpose:** Safe, billed, trackable school transport.
- **Procedure:** Define vehicles, drivers, routes, stops → fee slab by distance/zone → assign students → fee auto-added → daily boarding/trip log → parent alerts.
- **Predefined:** Vehicle types (Bus, Van, Mini-bus) [B]; fee model options (zone, distance slab, flat) [B]; trip types (Pickup/Drop) [A]; safety checklist (fitness, permit, insurance expiry, driver licence) with expiry alerts [E]. School sets routes, stops, amounts [C/D].

#### X10. Inventory, Uniform & Book Store
- **Purpose:** Track stock and sell uniforms/books/stationery to students.
- **Procedure:** Add items → purchase → receive → stock → sell/issue to student (charge to fee account) → low-stock alert.
- **Predefined:** Item categories (Uniform, Books, Stationery, Lab, Sports, IT, Furniture, Housekeeping) [B]; size sets for uniforms [B]; reorder alerts [E]. School sets items/prices [D].

#### X11. Parent-Teacher Meetings & Events
- **Procedure:** Create event → generate slots → parents book → reminders → teacher notes after meeting.
- **Predefined:** Event templates (PTM, Open House, Annual Day, Sports Day, Orientation) [B]; reminders [E]; attendance at events [A].

#### X12. Visitor, Gate & Safe Pickup
- **Procedure:** Visitor registers → host approval → pass issued → exit logged. Early pickup: parent request → authorised person verified (OTP/photo) → exit recorded.
- **Predefined:** Visitor purposes (Parent, Vendor, Official, Interview, Delivery) [B]; authorised-pickup list from parent info [E]; log [A].

#### X13. Board Registration & Regulatory Data
- **Purpose:** Satisfy board/government data demands (e.g. UDISE+, board registration for Classes 9 and 11, LOC, state portals).
- **Procedure:** Operion compiles student/staff/infra data → highlights missing mandatory fields → school fills → export in required format.
- **Predefined:** Field mapping templates per major reporting format [B]; data completeness checker [E]; student identifiers (PEN, APAAR) fields [A]. School supplies missing values [D].

#### X14. Hostel (only for residential schools)
- **Procedure:** Rooms/beds → allotment → hostel fee → leave-out/gate pass → mess attendance → warden notes.
- **Predefined:** Room types [B]; hostel fee heads [B]; leave-out request flow [B]. School sets rooms/amounts [C/D].

#### X15. Alumni
- **Procedure:** Passed-out students move to alumni automatically → optional alumni portal for certificates and updates.
- **Predefined:** Alumni status [A]; record creation [E]. School decides engagement features [C].

---

## 4. Reference Data Catalogue (what Operion should simply know)

These are the "nothing to configure" lists. Anything marked **A** should never appear as a school task.

| Dataset | Content | Cat. | Notes |
|---|---|---|---|
| Countries | Complete ISO list (~249) with dial codes | A | |
| Indian states & UTs | 28 states + 8 union territories | A | |
| Districts | Complete district list per state (LGD/census based) | A | Refresh when districts change |
| PIN code → city/district/state lookup | India Post data | A | Auto-fill addresses at entry |
| Blood groups | A+, A−, B+, B−, AB+, AB−, O+, O−, Unknown | A | |
| Gender | Male, Female, Other | A | |
| Religions, social categories, minority flags, CWSN types | Standard lists | A | Optional fields |
| Languages | 22 scheduled languages + English + Other | A | |
| Boards | CBSE, ICSE/ISC, State Boards (all), IB, Cambridge IGCSE/A-Level, NIOS | A | |
| Class ladder & stages | Pre-Nursery–12 | B | Rename allowed |
| Subject catalogue | Standard list | A | School selects |
| Grading scales | Percentage, CBSE 9-point, 5-point, descriptive, CGPA | A/B | |
| Exam types & scheme templates | See W13 | B | |
| Attendance statuses | See W12 | B | |
| Student, admission, enrolment, staff, leave, payment, exam statuses | Fixed workflow states | A | |
| Fee heads, frequencies, payment modes, concession types | See W10 | B | |
| Document types | See W7/W8 | B | |
| Holiday seeds | National + state + festival suggestions | B | Updated annually |
| Message templates | See W15 | B | |
| Report & certificate templates | See W19, X5 | B | |
| Leave types and statutory components | See X6, X7 | B | Review yearly for law changes |
| Departments & designations | See W8 | B | |
| Time zone, currency, date formats | IST, INR, DD/MM/YYYY | A | |

---

## 5. "What can Operion already know?" — Per-Module Answer

| Module | Operion can already know / provide | Remaining school effort | Move toward |
|---|---|---|---|
| School identity | Country, currency, tz, board list, state/district list, PIN lookup | Name, logo, address, affiliation no. | Minimal, unavoidable |
| Academic year | Label, dates, term splits | Confirm dates | B/E |
| Calendar | National/state/festival holidays; working-week patterns; period template | Tick/observe + local events | B |
| Classes | Full ladder; section names; streams | Section counts and capacity | B + C(small) |
| Subjects | Catalogue + board-suggested bundle per class + weekly period suggestions | Remove/add; assign teachers | B + C |
| Students/Parents | All dropdown lists; form fields; document checklist; PIN lookup | Actual data | D (import) |
| Staff | Departments, designations, types, leave types and quotas | Actual data; salary | D |
| Allocation | Section balancing; roll numbers; house assignment | Choose rule | E |
| Fees | Heads, frequencies, due dates, late-fee models, concession types, receipt format | Amounts only | C (amounts) + E |
| Timetable | Template; auto-generation; clash detection | Special constraints | E |
| Attendance | Statuses, cut-offs, 75% rule, working-day maths, alerts | Nothing typically | B/E |
| Exams | Types, scheme per board, pass marks, date-sheet template, hall tickets | Dates | B + C(dates) |
| Results | Grade scale, report-card layout, remarks bank, ranking | Rank policy | B/E |
| Communication | Templates, audiences, merge fields, quiet hours | Provider credentials/DLT | C |
| Parent portal | Full feature set, auto-invite | Nothing | E |
| Finance | Invoice/receipt automation, reconciliation, expense categories | Bank/gateway | C |
| Transport | Vehicle types, fee models, safety expiry alerts | Routes/stops/amounts | C/D |
| Library/Inventory | Categories, limits, fines | Items | D |
| Payroll | Components, statutory defaults | Salaries | D |
| Transfers/TC | Checklist, template, numbering, status effects | Signatory, dues policy | B/C |
| Promotion | Criteria, proposed outcomes, next-year enrolment | Review exceptions | E |
| Year-end/closure | Checklists, archive, readiness check | Resolve flags | E |

---

## 6. School Onboarding (the minimum)

Goal: **Select → Configure a few school-specific values → Import existing data → Start using Operion.** Target: a school is operational in a day, not weeks.

### 6.1 Operion already provides (no school input)
- All reference data (Part 4), grade/status/attendance/exam-type lists, fee head list, templates, report/certificate formats, standard workflows, calendar mechanics, calculations.

### 6.2 School selects (choose from predefined options)
1. Board (+ State Board name), state/UT, medium(s)
2. School type (co-ed/boys/girls, day/residential), class range
3. Academic-year pattern (Apr–Mar / Jun–May) and term pattern
4. Working week pattern; period template
5. Which subjects are taught (pre-ticked by board suggestion)
6. Streams for 11–12
7. Grading scale (pre-selected by board/class group)
8. Exam scheme (pre-selected by board)
9. Which fee heads are charged; fee frequencies
10. Which holidays are observed; which concession types are used
11. Which modules are used (Transport, Library, Hostel, Inventory, Payroll…)
12. Communication channels

### 6.3 School enters (unique to the school)
- School name, address, contact, logo, affiliation no., UDISE code, principal/signatory, bank details
- Section count and capacity per class
- Fee amounts per class (can be imported)
- Late-fee rule values, concession values
- School timings
- House names (if used)
- Transport routes and fares (if used)
- SMS/WhatsApp/payment-gateway credentials

### 6.4 School imports (bulk)
1. Staff
2. Students (with class/section)
3. Parents/guardians (linked to students)
4. Fee structure and opening dues
5. Transport assignments and routes
6. Library catalogue
7. Previous-year results (if wanted for history)
8. Student documents (zip/bulk) — optional

### 6.5 The onboarding sequence (target ~ a day)
1. Starter Pack + three choices (5 min)
2. Review summary of everything pre-filled (15 min)
3. School identity (10 min)
4. Classes and sections (15 min)
5. Fee amounts (30 min, import)
6. Import staff, then students + parents (1–2 hours, with validation)
7. Run a **go-live check** (Part 10) → Activate year → invite staff and parents

---

## 7. Data Import

General rules for **every** dataset:
- Operion provides a **downloadable template** (Excel/CSV) with a header row, sample row, and drop-down guidance.
- Operion offers a **validation preview before import**: counts of rows OK / warnings / errors.
- **Invalid records**: never block the whole file. Valid rows import; invalid rows are skipped and put in an **error file** (original row + reason) which the school fixes and re-uploads. Duplicates are flagged and skipped or merged (school chooses).
- Re-runnable: re-importing a corrected file does not duplicate earlier rows.
- Imports are logged (who/when/how many) and the batch can be reviewed.

| Dataset | Required fields | Optional fields | Template? | Pre-validation? | Invalid records |
|---|---|---|---|---|---|
| Classes & sections | Class, section, capacity | Class teacher, room, stream | Yes | Yes (duplicates, class exists) | Skipped + error file |
| Subjects & class-subject mapping | Subject (match to catalogue), class | Teacher, periods/week, elective group | Yes | Yes (matches catalogue; suggest nearest) | Unmatched subjects flagged for mapping |
| Staff | Name, mobile, designation, joining date | Email, DOB, qualification, department, employment type, bank, PAN, IDs | Yes | Yes (duplicate phone/email, designation known) | Skipped + error file |
| Students | Name, DOB, gender, class, section, admission no. (or auto) | Blood group, category, religion, Aadhaar (optional), address, previous school, house, transport, PEN/APAAR, photo | Yes | Yes (DOB sanity, class/section exist, duplicate admission no.) | Skipped + error file |
| Parents/Guardians | Name, mobile, relation, linked student's admission no. | Email, occupation, address, primary-contact flag | Yes | Yes (linked student exists; duplicate phone merges into same family) | Skipped; unlinked flagged |
| Fee structure | Class, fee head, amount, frequency | Due dates, applicable category | Yes | Yes (head and class valid) | Skipped + error file |
| Existing balances / opening dues | Admission no., head, amount due/advance | As-of date, remarks | Yes | Yes (student exists; total reconciliation shown) | Skipped; reconciliation report |
| Past fee payments (history) | Admission no., date, amount, mode | Receipt no., head split | Yes | Yes | Skipped |
| Concessions | Admission no., concession type, value | Reason, valid-till | Yes | Yes | Skipped |
| Transport routes & stops | Route, stop, sequence, fare | Vehicle, driver, timing | Yes | Yes | Skipped |
| Transport assignment | Admission no., route, stop | Pickup/drop | Yes | Yes | Skipped |
| Library books | Title, copies/accession no. | Author, ISBN, category, shelf, price | Yes | Yes (duplicate accession) | Skipped |
| Previous academic records | Admission no., year, class, subject, marks/grade | Rank, remarks | Yes | Yes | Skipped; per-student summary |
| Attendance history | Admission no., date, status | Remarks | Yes | Yes (valid statuses, within year) | Skipped |
| Student documents | File + admission no. + type | Verified flag | Bulk upload (zip with naming rule) | File type/size, student match | Unmatched listed |
| Staff leave balances | Staff ID, leave type, balance | As-of date | Yes | Yes | Skipped |
| Inventory items & opening stock | Item, category, quantity | Price, reorder level | Yes | Yes | Skipped |
| Holidays/events | Date, name, type | Applies to | Yes | Yes | Skipped |
| Migrating from another ERP | Same sets above, plus "mapping file" option to match that ERP's export headings | | Mapping wizard | Yes | Same rules |

---

## 8. Automation Opportunities

Format: **Trigger → Automatic action → Result.**

| # | Trigger | Automatic action | Result |
|---|---|---|---|
| 1 | Enquiry converted | Copy details into application | No retyping |
| 2 | Application accepted + fee paid | Generate admission number; create student; create parent record/link; create portal invite; place in class/section; apply fee structure; generate roll number; send welcome | Fully admitted student with no manual steps |
| 3 | Same parent phone/email on a second child | Link as sibling; group fee payment | Family view, sibling concession candidate |
| 4 | Student placed in section | Apply class fee structure + category/transport/concessions; schedule invoices | Dues are ready |
| 5 | Transport assigned | Add transport fee by slab; add to route manifest | Billing and manifest in sync |
| 6 | Fee due date minus N days | Send reminder | Fewer defaulters |
| 7 | Payment received | Receipt generated and sent; ledger and dues updated | Zero manual receipting |
| 8 | Payment overdue | Late fee computed; reminder escalates; appears on defaulter list | Controlled follow-up |
| 9 | Parent submits leave request | Notify class teacher; on approval, pre-mark attendance | No conflicting absence alert |
| 10 | Attendance submitted | Absent parents notified; monthly % recalculated; low-attendance flagged | Early intervention |
| 11 | Teacher on approved leave | Highlight timetable gaps; suggest substitutes | Classes covered |
| 12 | Exam published | Date sheet to parents; hall tickets generated; eligibility checked | Smooth exam start |
| 13 | Marks entered and locked | Totals, grades, ranks, report cards computed | No manual calculation |
| 14 | Results published | Notify parents; update portal; build remarks | Instant visibility |
| 15 | Student status = Transferred Out | Stop fees/transport; free seat; lock portal; archive records; offer waitlist candidate | Clean exit |
| 16 | TC requested | Run clearance checklist; prefill TC | Quick, accurate TC |
| 17 | Final results published | Propose promotion outcome for every student | Teachers review only exceptions |
| 18 | Promotion confirmed | Create next-year enrolments, roll numbers, carry dues, apply new fees | Next year ready |
| 19 | Student completes final class | Mark Passed Out; move to alumni; prepare leaving certificate | Year-end closure of cohort |
| 20 | Academic year closed | Archive data; identify students needing next-year preparation; generate readiness report | Next year starts clean |
| 21 | New year created | Offer copy-forward of classes, subjects, fees, calendar, templates | Months of setup avoided |
| 22 | Staff joins | Create staff ID; leave balances per policy; portal invite | Ready-to-use staff account |
| 23 | Month-end | Staff attendance + leave → LOP → payroll draft | Quick payroll review |
| 24 | Stock below reorder level | Alert purchaser | No stock-outs |
| 25 | Library book overdue | Fine computed; reminder | Return follow-up |
| 26 | Document uploaded | Verification task; reminder if still missing after N days | Complete files |
| 27 | Vehicle fitness/insurance/driver-licence expiry approaching | Alert transport in-charge | Safety compliance |
| 28 | Birthday | Greeting to student/staff (optional) | Engagement |
| 29 | Holiday/closure declared | Notify all; skip attendance expectation; shift events | One action, many effects |
| 30 | Board/regulatory export requested | Compile data; list missing mandatory fields | Minimal last-minute scramble |

---

## 9. The Continuous Journey (enter once, reuse everywhere)

```
School Onboarding ──► School identity, board, state, class range  (entered ONCE)
        │                  └─► reused on: every printout, message, report, TC, receipt
        ▼
Academic Year Setup ──► year, terms, calendar
        │                  └─► reused by: attendance %, fee due dates, exam windows, timetable
        ▼
School Configuration ──► classes, sections, subjects, fee heads/amounts, grading, exam scheme
        │                  └─► reused by: allocation, invoices, report cards, promotion
        ▼
Student Admission ──► child's details entered ONCE (or imported)
        │                  └─► reused by: attendance, fees, exams, ID card, TC, reports, board data
        ▼
Parent Setup ──► parent contact entered ONCE, siblings auto-linked
        │                  └─► reused by: portal login, alerts, fee reminders, PTM, pickup authorisation
        ▼
Daily Operations ──► timetable, diary, substitutions, library, transport
        ▼
Attendance ──► marked daily
        │                  └─► feeds: parent alerts, monthly %, exam eligibility, promotion
        ▼
Fees ──► invoices from structure; receipts; dues
        │                  └─► feeds: finance reports, defaulters, TC clearance, carry-forward
        ▼
Examinations ──► scheme → date sheet → hall tickets
        ▼
Results ──► marks → grades → report cards
        │                  └─► feeds: promotion decision, remarks, history
        ▼
Communication ──► templates + events send automatically across all of the above
        ▼
Reports ──► generated from the same records; no re-entry
        ▼
Year End ──► certificates, regulatory exports, dues decisions, stock-takes
        ▼
Promotion ──► proposed from results + attendance; teachers review exceptions
        ▼
New Academic Year ──► copy-forward + promoted students + revised fees
        ▼
Academic Year Closure ──► readiness check → freeze → archive → next year active
        └──────────────────────────────────────────────────────────────► repeat
```

**Single points of entry:** School identity (once, ever). Student and family (once per child, then reused for 10+ years). Class/section/fee/subject structure (once, then copied forward). Attendance and marks (once per event). Everything else is derived.

---

## 10. Final Readiness Checklist

### 10.1 Must be predefined by Operion (nothing for the school to do)
- [ ] Countries, states/UTs, districts, PIN lookup
- [ ] Blood groups, gender, religion, social category, CWSN types, languages, boards
- [ ] Status lists (student, admission, enrolment, staff, leave, payment, exam, year)
- [ ] Class ladder, subject catalogue, board-suggested bundles
- [ ] Grading scales (percentage, CBSE 9-point, 5-point, descriptive, CGPA)
- [ ] Attendance statuses and rules (75% default)
- [ ] Exam types and board-based exam schemes
- [ ] Fee heads, frequencies, payment modes, concession types, late-fee models
- [ ] Document types, exit reasons, incident categories, visitor purposes
- [ ] Message template library (DLT-ready wording), report/certificate templates
- [ ] National + state + festival holiday seeds, refreshed yearly
- [ ] Departments, designations, leave types, payroll components

### 10.2 Must be configurable by the school
- [ ] School identity, logo, signatory, affiliation/UDISE codes
- [ ] Board, state, class range, academic-year pattern, term pattern
- [ ] Sections and capacities; streams
- [ ] Subjects taught; teacher assignments
- [ ] Fee amounts, due dates, late-fee and concession values
- [ ] Period timings and special periods
- [ ] Grading choice, exam dates, ranking/grace policy
- [ ] Promotion criteria adjustments, TC/dues policy
- [ ] House names, activities, custom fields
- [ ] Communication provider credentials; payment gateway
- [ ] Any rename or addition to B-category items

### 10.3 Must be importable
- [ ] Staff, students, parents/guardians (linked)
- [ ] Fee structure, opening dues, concessions
- [ ] Transport routes and assignments
- [ ] Library catalogue, inventory opening stock
- [ ] Previous results, attendance history, documents (as needed)
- [ ] Staff leave balances

### 10.4 Must be automated
- [ ] Admission → student + parent + class + fees (Automation 2)
- [ ] Invoices, reminders, receipts, late fees (6–8)
- [ ] Absence alerts and attendance %
- [ ] Grades, totals, ranks, report cards
- [ ] Promotion proposal and next-year enrolment (17–18)
- [ ] Transfer clearance and status effects (15–16)
- [ ] Year closure readiness and archive (20)
- [ ] Copy-forward for a new year (21)
- [ ] Sibling linking, roll numbers, admission/receipt numbers
- [ ] Expiry alerts (vehicles, documents, leave balances)

### 10.5 Must be validated
- [ ] Every import: preview counts, error file, duplicate handling
- [ ] Student age vs class eligibility
- [ ] Mandatory board/regulatory fields complete before export
- [ ] Section capacities not exceeded; no teacher/room clashes
- [ ] Fee totals per class match the school's fee circular
- [ ] Opening dues reconcile to the school's ledger total
- [ ] Marks within max; missing marks flagged before publish
- [ ] Parent mobile/email format and duplicates
- [ ] Messaging templates approved (DLT) before first send

### 10.6 Must be tested before the school goes live
- [ ] **Admission dry-run**: one test applicant from enquiry to admitted student with fee applied and parent portal login
- [ ] **A full day**: mark attendance, send absentee alert, collect a fee payment, print a receipt
- [ ] **Fee cycle**: generate invoices, remind, pay partially, late fee, concession
- [ ] **Exam cycle**: date sheet, hall ticket, marks entry, report card (print preview)
- [ ] **TC flow**: clearance, TC print, seat freed
- [ ] **Promotion rehearsal** on a copy of data, including detained and passed-out students
- [ ] **Year closure rehearsal** with the readiness check
- [ ] Message delivery on each enabled channel to test numbers
- [ ] Printouts on school letterhead: receipt, report card, ID card, TC
- [ ] Parent portal on a real phone; sibling switch
- [ ] Reports reconcile with the school's own manual figures for one day/class
- [ ] Backup/export of all data confirmed available to the school

---

## Appendix A — Where Operion stands today (from a code inventory on 2026-10-07)

Summarised from an automated search of the repository. **Items marked "not confirmed" were not found in that search and should be verified before relying on the gap statement.**

| Area | Already in Operion | Gap against this document |
|---|---|---|
| Starter data | 12 grades (LKG–Grade 10), 10 departments, 17 designations, 12 role templates, 6 fee categories, 8 grading bands (A+–F) seeded from a school template | Class ladder stops at Grade 10 (no Nursery/Class 11–12/streams); fee heads fewer than W10 list; CBSE 9-point and other scales absent; states/countries/blood-group lists not confirmed as seeded |
| Subjects | Shared platform-wide catalogue | Board-suggested class bundles not found |
| Academic year | Draft/Active/Closed/Archived; calendar and working-calendar entries; setup progress widget | No copy-forward of structures; no guided term-pattern choice found |
| Imports | Students, guardians, classes/sections, subjects, enrolments, routes, books, fees with per-row isolation and per-row status | Downloadable templates and a validate-before-import preview not confirmed; staff, opening balances, history imports not found |
| Automations | Applicant → student conversion; fee structure on placement; portal invite on guardian link; overdue-fee reminders; leave status; scheduled announcements; notification delivery (email/SMS/WhatsApp) | Absence alerts, promotion proposals, closure readiness checks not found |
| Promotion / closure | Single-student promote action; close-year action | **No bulk promotion, no proposed-outcome engine, no auto next-year preparation** — the biggest gap |
| Modules | Dashboard, Students & Admissions, Academics/Timetable, Attendance, Examinations, Library, Learning, Fees, Transport, Communication, Messaging, Inventory, Purchase, Sales, Reports, HR, Payroll, Compliance | Hostel, visitor/gate, alumni, PTM scheduling not confirmed |
| Gaps verified absent | Scholarship/concession entity and approval; bulk rollover; UDISE/APAAR fields; TC certificate generation (only an incoming TC-number field); auto fee-structure copy to new year | House system and RTE quota exist only as free-text/custom fields |

**Suggested build priority from this comparison:** (1) Bulk promotion + year rollover + copy-forward; (2) Class ladder and board/exam/grading starter packs; (3) Import templates with preview/error files and a staff import; (4) Concessions/scholarship workflow and TC generation; (5) Onboarding wizard that applies the Starter Pack and runs the go-live check; (6) Regulatory field support (PEN, APAAR, UDISE).
