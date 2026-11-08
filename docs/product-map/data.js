// Operion product map content. One source for the page (index.html) and the screenshot capture script.
// A sub-section is reached by `path`, then optionally clicking the tab named `tab`,
// or scrolling the panel headed `section` into view. Its screenshot lives at screenshots/<module>/<key>.png.
window.OPERION_MAP = {
	nav: [
		{ group: null, items: ["dashboard"] },
		{ group: "Academics", items: ["students", "admissions", "academics", "attendance", "examinations", "library", "learning"] },
		{ group: "Operations", items: ["fees", "transport", "communication", "messaging", "inventory", "purchase", "sales", "reports"] },
		{ group: "Administration", items: ["hr", "payroll", "performance", "members", "settings"] },
	],

	modules: {
		dashboard: {
			name: "Dashboard", route: "/dashboard", perm: ["ORGANISATION_MANAGE"],
			what: "The landing page for school admins. A read-only summary of the whole school, plus the setup checklist and shortcuts to common jobs.",
			needs: ["Nothing; fills in as other modules get data"],
			subs: [
				{ key: "overview", label: "Greeting and summary stats", path: "/dashboard", desc: "Greeting banner, the next setup prompt, and live counts: active students, active staff, today's attendance and fees due." },
				{ key: "setup", label: "Next setup step", path: "/dashboard", section: "Next setup step", desc: "Checklist of the four setup tasks (structure, academic setup, members, students) with a button to the next one. Can be dismissed for good." },
				{ key: "attendance", label: "Attendance overview", path: "/dashboard", section: "Attendance overview", desc: "Today's attendance rate and present / absent / late / half-day counts across the school." },
				{ key: "quick-actions", label: "Quick actions", path: "/dashboard", section: "Quick actions", desc: "One-click shortcuts: take attendance, add student, collect fee, generate report, create notice, add staff." },
				{ key: "fees", label: "Fee collections", path: "/dashboard", section: "Fee collections", desc: "Collected vs outstanding fees at a glance, linking to the fee dashboard." },
				{ key: "by-class", label: "Students by class", path: "/dashboard", section: "Students by class", desc: "How many students are enrolled in each class this academic year." },
			],
		},

		students: {
			name: "Students", route: "/students", perm: ["STUDENT_VIEW"],
			what: "The student register. Every student has a profile holding their guardians, enrollment history, documents, attendance, certificates and library loans.",
			needs: ["Current academic year", "Classes and sections"],
			subs: [
				{ key: "list", label: "Student list", path: "/students", desc: "Search and filter every student by class, section, status and admission date. Buttons for certificates, year-end promotion and admitting a student." },
				{ key: "applicants", label: "Applicants panel", path: "/students", section: "Applicants", desc: "Enquiries recorded from the student page, before they become admitted students." },
				{ key: "new", label: "Admit student", path: "/students/new", desc: "One form: person details, primary guardian and admission details. Creates the student and their guardian together." },
				{ key: "profile", label: "Student profile", path: "/students/5", desc: "Person and admission details for one student, with Edit." },
				{ key: "guardians", label: "Guardians", path: "/students/5", section: "Guardians", desc: "Parents and guardians linked to this student: relationship, primary contact, emergency contact, pickup and communication permissions." },
				{ key: "documents", label: "Documents", path: "/students/5", section: "Documents", desc: "Uploaded files such as birth certificate or previous TC." },
				{ key: "transfers", label: "Campus transfers", path: "/students/5", section: "Campus transfers", desc: "Request moving the student to another campus; history is kept." },
				{ key: "certificates", label: "Certificates on profile", path: "/students/5", section: "Certificates", desc: "Request a certificate (bonafide, transfer, etc.) for this student and see past ones." },
				{ key: "promotion", label: "Year-end promotion", path: "/students/promotion", desc: "Move a whole section's students into next year's classes in one step. Past enrollments stay on record." },
				{ key: "certificate-register", label: "Certificate register", path: "/students/certificates", desc: "Every certificate issued by the school, with approval status." },
			],
		},

		admissions: {
			name: "Admissions", route: "/admissions", perm: ["STUDENT_VIEW"],
			what: "The applicant pipeline. Applications come from the public form at /apply/:orgSlug or are added by staff, move through review, and an admitted applicant becomes a student.",
			needs: ["Current academic year", "Classes"],
			subs: [
				{ key: "pipeline", label: "Applicant pipeline", path: "/admissions", desc: "Applicants filtered by stage: Pending, Under review, Approved, Rejected. 'Public form link' gives the URL parents can apply through; 'Add applicant' records one by hand." },
			],
		},

		academics: {
			name: "Academics", route: "/academics", perm: ["CLASS_VIEW", "GRADE_LEVEL_VIEW", "SUBJECT_VIEW", "TEACHER_ASSIGNMENT_VIEW"],
			what: "The school's structure: which grades exist, what is taught in each, who teaches it, and when.",
			needs: ["Academic year (Settings)", "Staff in HR, for teacher assignments"],
			subs: [
				{ key: "grade-levels", label: "Grade levels", path: "/academics", tab: "Grade levels", desc: "The ladder of grades (LKG, UKG, Grade 1 … Grade 10) with their order and stage (pre-primary, primary, secondary). Org-wide, not per year." },
				{ key: "sections", label: "Sections", path: "/academics", tab: "Sections", desc: "Divisions of a class offering (A, B, …) with capacity and room. Students are enrolled into a section." },
				{ key: "subjects", label: "Subjects", path: "/academics", tab: "Subjects", desc: "The subject catalog (English, Mathematics, …) with short codes." },
				{ key: "class-offerings", label: "Class offerings", path: "/academics", tab: "Class offerings", desc: "A grade running in a given academic year on a campus, e.g. 'Grade 5, 2026-27, Main Campus'. Subjects and sections hang off this." },
				{ key: "teacher-assignments", label: "Teacher assignments", path: "/academics", tab: "Teacher assignments", desc: "Which teacher teaches which subject to which section. The timetable and the teacher portal both depend on this." },
				{ key: "periods", label: "Periods", path: "/academics", tab: "Periods", desc: "The school day: numbered periods with start/end times, and which ones are breaks." },
				{ key: "rooms", label: "Rooms", path: "/academics", tab: "Rooms", desc: "Classrooms and labs that timetable slots can be placed in." },
				{ key: "timetable", label: "Timetable", path: "/academics", tab: "Timetable", desc: "The weekly grid per section. A teacher can't be booked in two places in the same period." },
				{ key: "substitutions", label: "Substitutions", path: "/academics", tab: "Substitutions", desc: "Cover for an absent teacher on a given day." },
				{ key: "class-detail", label: "Class detail page", path: "/academics/classes/31", desc: "One class offering: its subjects (with weekly periods) and its sections." },
				{ key: "section-detail", label: "Section detail page", path: "/academics/classes/31/sections/51", desc: "One section: its teacher assignments and its timetable, with draft generation, publish, CSV export and print." },
			],
		},

		attendance: {
			name: "Attendance", route: "/attendance", perm: ["ATTENDANCE_VIEW", "STAFF_ATTENDANCE_VIEW"],
			what: "Daily student attendance per class register, staff attendance, and monthly percentages. Holidays come from the working calendar.",
			needs: ["Students enrolled in sections", "Working calendar"],
			subs: [
				{ key: "overview", label: "Overview", path: "/attendance", tab: "Overview", desc: "Today's totals (enrolled, present, absent, classes not yet recorded) and the list of classes with a 'Mark now' button each." },
				{ key: "class-register", label: "Class register", path: "/attendance", tab: "Class Register", desc: "Month-view register for a section: one row per student, one column per day." },
				{ key: "staff", label: "Staff attendance", path: "/attendance", tab: "Staff Attendance", desc: "Daily attendance for staff members." },
				{ key: "mark", label: "Mark attendance", path: "/attendance/mark", desc: "Pick a section and date, then mark each student present, absent, late or half day." },
			],
		},

		examinations: {
			name: "Examinations", route: "/examinations", perm: ["EXAM_VIEW"],
			what: "Exams, schedules per class and subject, marks entry with an approval step, ranks, and report cards that are published and can be corrected.",
			needs: ["Classes, subjects and enrolled students"],
			subs: [
				{ key: "overview", label: "Overview", path: "/examinations", tab: "Overview", desc: "All exams this year with status, plus upcoming schedules, marks-entry progress and published results." },
				{ key: "grading-scales", label: "Grading scales", path: "/examinations", tab: "Grading Scales", desc: "Mark ranges mapped to grades (A1, A2, …) used on report cards." },
				{ key: "settings", label: "Exam settings", path: "/examinations", tab: "Settings", desc: "Pass/fail rule and whether ranks are shown." },
			],
		},

		library: {
			name: "Library", route: "/library", perm: ["LIBRARY_VIEW"],
			what: "Book catalog with physical copies per campus, borrowing and returns, overdue tracking, and loan limits by borrower type.",
			needs: ["Campuses", "Students or staff as borrowers"],
			subs: [
				{ key: "books", label: "Books", path: "/library", tab: "Books", desc: "The catalog. Each book can have several copies, each with a shelf location. Chips show available / issued / overdue / lost / damaged counts." },
				{ key: "borrow", label: "Borrow / return", path: "/library", tab: "Borrow / return", desc: "Issue a copy to a student or staff member, and record its return (optionally as damaged)." },
				{ key: "overdue", label: "Overdue", path: "/library", tab: "Overdue", desc: "Loans past their due date, with reminders." },
				{ key: "settings", label: "Library settings", path: "/library", tab: "Settings", desc: "Max loans and borrowing days per borrower type, and whether overdue loans block new ones." },
			],
		},

		learning: {
			name: "Learning", route: "/learning", perm: ["LEARNING_VIEW", "LESSON_PLAN_REVIEW"],
			what: "Light LMS shared by admins and teachers. Teachers also reach it from the portal at /portal/teaching; guardians act for their child.",
			needs: ["Teacher assignments", "Sections with students"],
			subs: [
				{ key: "homework", label: "Homework", path: "/learning", tab: "Homework", desc: "Assign homework to a section and subject; guardians submit for their child; teachers review with feedback." },
				{ key: "quizzes", label: "Quizzes", path: "/learning", tab: "Quizzes", desc: "Multiple-choice quizzes, auto-scored. Answers are hidden until the quiz closes." },
				{ key: "materials", label: "Materials", path: "/learning", tab: "Materials", desc: "Files, links and notes shared with a class." },
				{ key: "lesson-plans", label: "Lesson plans", path: "/learning", tab: "Lesson plans", desc: "Draft, submit for review, approve or reject. Approved plans lock." },
				{ key: "answer-sheets", label: "Answer sheets", path: "/learning", tab: "Answer sheets", desc: "Upload scanned answer sheets, mark them with ticks, crosses and comments, then release to the guardian." },
				{ key: "leaderboards", label: "Leaderboards", path: "/learning", tab: "Leaderboards", desc: "Per-section boards for quiz scores, marks and reward points, plus a teacher activity board." },
			],
		},

		fees: {
			name: "Fees", route: "/fees", perm: ["FEE_VIEW"],
			what: "Fee setup, collection with receipts, refunds, waivers and adjustments, payment links, and the overdue view with reminders.",
			needs: ["Classes", "Enrolled students", "Guardians (for reminders)"],
			subs: [
				{ key: "dashboard", label: "Fees & collections", path: "/fees", desc: "Totals (fees, collected, outstanding, overdue) and the student fee ledger. 'Collect Fee' starts a payment." },
				{ key: "charts", label: "Collection charts", path: "/fees", section: "Fee Collection Trend", desc: "Collection trend over 6 months, collection by class, and breakdown by fee type." },
				{ key: "setup", label: "Fee structure", path: "/fees/setup", desc: "Fee categories (tuition, transport, …) and fee structures per class, which are then assigned to students." },
				{ key: "demand", label: "Overdue & reminders", path: "/fees/demand", desc: "Generate invoices that have fallen due, list overdue invoices, and send reminders to guardians." },
			],
		},

		transport: {
			name: "Transport", route: "/transport", perm: ["TRANSPORT_VIEW"],
			what: "Vehicles with documents and licence expiry, routes with stops and timings, crew, and which students ride which route.",
			needs: ["Staff (drivers, attendants)", "Students"],
			subs: [
				{ key: "vehicles", label: "Vehicles", path: "/transport", section: "Vehicles", desc: "Buses and vans with registration, capacity, documents and status." },
				{ key: "routes", label: "Routes", path: "/transport", section: "Routes", desc: "Routes with ordered stops, pickup and drop times, vehicle, driver and attendant." },
				{ key: "students", label: "Student transport", path: "/transport", section: "Student transport", desc: "Assign a student to a route and stop, for pickup, drop or both." },
			],
		},

		communication: {
			name: "Communication", route: "/communication", perm: ["COMMUNICATION_VIEW"],
			what: "Announcements to the whole school, a campus, a class, a section or staff, delivered in-app and by email/SMS, plus an events calendar.",
			needs: ["Guardians with contact details"],
			subs: [
				{ key: "notifications", label: "My notifications", path: "/communication", desc: "Your own in-app notifications, with the in-app toggle." },
				{ key: "compose", label: "Compose announcement", path: "/communication", section: "Compose announcement", desc: "Title, body and audience. Preview who will receive it, save as draft, or schedule it." },
				{ key: "drafts", label: "Drafts and published feed", path: "/communication", section: "Drafts", desc: "Unsent drafts, and everything already published." },
				{ key: "events", label: "Events calendar", path: "/communication", section: "Events calendar", desc: "School events shown to staff and guardians." },
			],
		},

		messaging: {
			name: "Messaging", route: "/messaging", perm: ["COMMUNICATION_VIEW"],
			what: "Direct conversations between people in the school, and class group chats.",
			needs: ["Members with logins"],
			subs: [
				{ key: "conversations", label: "Conversations", path: "/messaging", desc: "Conversation list on the left, the open thread on the right. 'New message' or 'Class group' starts one." },
			],
		},

		inventory: {
			name: "Inventory", route: "/inventory", perm: ["INVENTORY_VIEW"],
			what: "Stock items with a full ledger (entries, issues, adjustments, campus transfers), low-stock alerts, physical assets, suppliers and customers.",
			needs: ["Campuses"],
			subs: [
				{ key: "categories", label: "Item categories", path: "/inventory", section: "Item categories", desc: "Groups for stock items (stationery, lab, sports, …)." },
				{ key: "items", label: "Items", path: "/inventory", section: "Items", desc: "Every stock item with unit, category and reorder level. Pick a campus and tick 'Low stock only' to see what needs reordering." },
				{ key: "assets", label: "Assets", path: "/inventory", section: "Assets", desc: "Individually tracked things (laptops, projectors) with who has them and where." },
				{ key: "suppliers", label: "Suppliers", path: "/inventory", section: "Suppliers", desc: "Vendors you buy from. Used by Purchase." },
				{ key: "customers", label: "Customers", path: "/inventory", section: "Customers", desc: "Who you sell to: a student, a guardian, or a walk-in. Used by Sales." },
			],
		},

		purchase: {
			name: "Purchase", route: "/purchase", perm: ["PURCHASE_VIEW"],
			what: "Purchase orders that go through approval, are received into stock, and can be partly returned to the supplier.",
			needs: ["Suppliers", "Inventory items"],
			subs: [
				{ key: "orders", label: "Purchase orders", path: "/purchase", desc: "All POs with status: Draft → Submitted → Approved → (Partially) Received, or Cancelled before receiving starts." },
			],
		},

		sales: {
			name: "Sales", route: "/sales", perm: ["SALES_VIEW"],
			what: "Selling items such as uniforms and books to students, guardians or walk-in customers, with payments.",
			needs: ["Inventory items in stock", "Customers"],
			subs: [
				{ key: "list", label: "Sales", path: "/sales", desc: "Every sale with customer, amount and payment status. 'New sale' starts one." },
			],
		},

		reports: {
			name: "Reports", route: "/reports", perm: ["REPORT_CREATE", "REPORT_MANAGE"],
			what: "Saved reports, private to you or shared with others. 'Seed standard reports' adds a ready-made set.",
			needs: ["Data in the modules you report on"],
			subs: [
				{ key: "mine", label: "My reports", path: "/reports", tab: "My reports", desc: "Reports you created." },
				{ key: "shared", label: "Shared reports", path: "/reports", tab: "Shared reports", desc: "Reports other people shared with you." },
			],
		},

		hr: {
			name: "HR", route: "/hr", perm: ["HR_VIEW"],
			what: "Staff profiles, documents, bank details, leave, holidays, job applications and exits. Login access for staff is granted from here.",
			needs: ["Departments and designations (Settings)"],
			subs: [
				{ key: "leave-types", label: "Leave types", path: "/hr", desc: "Casual, sick, earned and other leave types with default days per year." },
				{ key: "staff", label: "Staff list", path: "/hr", section: "Staff", desc: "Every staff member with employee code, designation, employment type and status." },
				{ key: "leave-holidays", label: "Leave requests and holidays", path: "/hr", section: "Pending leave requests", desc: "Leave requests waiting for approval, upcoming holidays in the next 60 days, and pending job applications." },
				{ key: "new", label: "Add staff member", path: "/hr/staff/new", desc: "Create a staff member; optionally give them a login at the same time." },
				{ key: "profile", label: "Staff profile", path: "/hr/staff/1", desc: "One staff member: status, transfer, exit, and 'Grant login access'." },
				{ key: "bank-docs", label: "Bank, tax and documents", path: "/hr/staff/1", section: "Bank & tax details", desc: "Bank and tax details (used by payroll) and uploaded documents." },
				{ key: "leave", label: "Leave balance and requests", path: "/hr/staff/1", section: "Leave balance", desc: "Allocate leave days, see what is left, and raise a request on their behalf." },
				{ key: "timetable", label: "Teacher timetable", path: "/hr/staff/1", section: "Timetable", desc: "This person's weekly teaching timetable and times they are unavailable." },
				{ key: "salary", label: "Salary structure", path: "/hr/staff/1", section: "Salary structure", desc: "Basic pay and salary components, versioned by effective date. Payroll uses the version in force each month." },
			],
		},

		payroll: {
			name: "Payroll", route: "/payroll", perm: ["PAYROLL_VIEW"],
			what: "Monthly payroll runs that produce payslips from each staff member's salary structure.",
			needs: ["Staff in HR", "A salary structure per staff member"],
			subs: [
				{ key: "runs", label: "Payroll runs", path: "/payroll", tab: "Payroll runs", desc: "Pick month and year, generate a draft run, add adjustments, then finalize (locks it). Mistakes are fixed with a correction run." },
				{ key: "components", label: "Salary components", path: "/payroll", tab: "Salary components", desc: "The school's own earnings and deductions, flat or a percentage of basic." },
			],
		},

		performance: {
			name: "Performance", route: "/performance", perm: ["KPI_VIEW"],
			what: "Teacher KPIs computed from existing data, appraisal cycles, and insights.",
			needs: ["Teacher assignments, attendance and exam data"],
			subs: [
				{ key: "overview", label: "Overview", path: "/performance", tab: "Overview", desc: "Where appraisals stand this cycle." },
				{ key: "scorecard", label: "Scorecard", path: "/performance", tab: "Scorecard", desc: "Each teacher's KPI scores side by side." },
				{ key: "cycles", label: "Appraisal cycles", path: "/performance", tab: "Appraisal cycles", desc: "Named review periods: who is pending review, ratings and goals." },
				{ key: "insights", label: "Insights", path: "/performance", tab: "Insights", desc: "Trends across staff." },
				{ key: "metrics", label: "Metrics", path: "/performance", tab: "Metrics", desc: "Which KPIs are measured and how they are weighted." },
			],
		},

		members: {
			name: "Members", route: "/members", perm: ["MEMBERSHIP_VIEW"],
			what: "Everyone who can log in to this school and their roles. Status shows Invited, Active or Inactive.",
			needs: ["Roles (Settings)"],
			subs: [
				{ key: "list", label: "Member list", path: "/members", desc: "Totals (members, active, pending) and the list, filterable by role, campus and status. 'Add member' sends an invite." },
				{ key: "detail", label: "Member detail", path: "/members/16", desc: "One person's account, recent activity, and roles. Grant a role, edit, or disable." },
			],
		},

		settings: {
			name: "Settings", route: "/settings", perm: [],
			what: "School-wide configuration as a grid of cards; each card opens /settings/<section>. The setup wizard walks a new school through the essentials.",
			needs: [],
			subs: [
				{ key: "grid", label: "Settings home", path: "/settings", desc: "All settings grouped as Organisation, Access & Security, Configuration, Compliance and System." },
				{ key: "business", label: "Business settings", path: "/settings/business", desc: "Timezone, currency, date format, and working days." },
				{ key: "profile", label: "Organisation profile", path: "/settings/profile", desc: "Name, logo, primary contact, address, and tax identifiers." },
				{ key: "campuses", label: "Campuses", path: "/settings/campuses", desc: "Add or manage branch and location campuses." },
				{ key: "departments", label: "Departments", path: "/settings/departments", desc: "The school's departments." },
				{ key: "designations", label: "Designations", path: "/settings/designations", desc: "Staff job titles." },
				{ key: "school-template", label: "School setup template", path: "/settings/school-template", desc: "Pre-fill grades, departments, designations, roles, fee and inventory categories, and a default grading scale." },
				{ key: "users", label: "Members", path: "/settings/users", desc: "Member accounts and role assignments." },
				{ key: "roles", label: "Roles", path: "/settings/roles", desc: "Define roles and tick their permissions, grouped by module. 'Portal' roles land in the portal instead of the admin app." },
				{ key: "audit-logs", label: "Audit logs", path: "/settings/audit-logs", desc: "Who changed roles, fee records, marks, student data and settings, and when." },
				{ key: "profile-change-requests", label: "Profile change requests", path: "/settings/profile-change-requests", desc: "Approve phone, email or photo changes that staff and guardians request." },
				{ key: "academic-years", label: "Academic years", path: "/settings/academic-years", desc: "Create years, mark one current, close old ones, roll over to the next." },
				{ key: "working-calendar", label: "Working calendar", path: "/settings/working-calendar", desc: "Holidays, special working days and school timings. Attendance and leave use this." },
				{ key: "branding", label: "Branding & documents", path: "/settings/branding", desc: "Logo, stamp, signature, footer text and numbering formats for printed documents." },
				{ key: "letter-formats", label: "Letter formats", path: "/settings/letter-formats", desc: "Header/footer template for question papers and report cards." },
				{ key: "certificate-types", label: "Certificate types", path: "/settings/certificate-types", desc: "Transfer, bonafide and other certificates: wording, numbering and approval rules." },
				{ key: "id-card-studio", label: "ID Card Studio", path: "/settings/id-card-studio", desc: "Design ID cards bound to live student data." },
				{ key: "notifications", label: "Notifications", path: "/settings/notifications", desc: "Message templates for email and SMS." },
				{ key: "custom-fields", label: "Custom fields", path: "/settings/custom-fields", desc: "Add your own fields to records like Student without a code change." },
				{ key: "privacy", label: "Privacy & consent", path: "/settings/privacy", desc: "Consent records, and requests to access, correct, export or delete personal data." },
				{ key: "regulatory-exports", label: "Regulatory exports", path: "/settings/regulatory-exports", desc: "Map student or staff records onto an external reporting layout and download as CSV." },
				{ key: "transfer-connectors", label: "Transfer hand-offs", path: "/settings/transfer-connectors", desc: "Prepare and track a transferred student's record for an external system." },
				{ key: "imports-exports", label: "Imports & exports", path: "/settings/imports-exports", desc: "Bulk import from Excel/CSV, or export existing records." },
				{ key: "integrations", label: "Integrations & API", path: "/settings/integrations", desc: "Your own accounts for email and SMS delivery and other services." },
				{ key: "setup-structure", label: "Setup wizard: structure", path: "/setup/structure", desc: "Step-by-step: organisation profile, primary contact, address, business settings." },
				{ key: "setup-review", label: "Setup wizard: review", path: "/setup/review", desc: "Everything the school has configured, with a 'Manage' link for each area." },
				{ key: "setup-go-live", label: "Setup wizard: go-live check", path: "/setup/go-live", desc: "Per-module readiness check with a 'Fix' button for anything missing." },
			],
		},
	},

	settingsGroups: [
		{ group: "Organisation", items: ["Business settings", "Organisation profile", "Campuses", "Departments", "Designations", "School setup template"] },
		{ group: "Access & Security", items: ["Members", "Roles", "Audit logs", "Profile change requests"] },
		{ group: "Configuration", items: ["Academic years", "Working calendar", "Branding & documents", "Letter formats", "Certificate types", "ID Card Studio", "Notifications", "Custom fields"] },
		{ group: "Compliance", items: ["Privacy & consent", "Regulatory exports", "Transfer hand-offs"] },
		{ group: "System", items: ["Imports & exports", "Integrations & API"] },
	],

	flows: [
		{ title: "A new school goes live", note: "Done once, by the school Owner. The dashboard checklist tracks progress.",
			steps: [["Platform admin creates the school", "/platform/organisations"], ["Owner logs in", "/login"], ["Organisation profile + business settings", "/setup/structure"], ["Academic year", "Settings › Academic years"], ["Grades, classes, sections, subjects", "/academics"], ["Review", "/setup/review"], ["Go-live check", "/setup/go-live"]] },
		{ title: "Access: roles, staff and logins", note: "Login access is optional for staff. One person can be staff and guardian at once.",
			steps: [["Create roles, tick permissions", "Settings › Roles"], ["Add staff member", "/hr/staff/new"], ["Grant login access (invite email)", "Staff profile / Members"], ["Staff claims invite", "/claim-staff-invite"], ["Staff logs in", "/dashboard or /portal"]] },
		{ title: "Student lifecycle", note: "History is kept at every step. Enrollment is per academic year, so past classes stay on record.",
			steps: [["Applies online or added by staff", "/apply/:orgSlug · /admissions"], ["Admitted", "/admissions"], ["Enrolled in class + section", "/students/:id"], ["Guardians linked", "Student › Guardians"], ["Daily attendance", "/attendance/mark"], ["Fees invoiced and paid", "/fees"], ["Exams and report card", "/examinations"], ["Promoted next year", "/students/promotion"], ["Exit / TC certificate", "/students/certificates"]] },
		{ title: "Fees: from setup to receipt", note: "Online payment-link payments are recorded only when the payment provider confirms them, never when the parent's browser says it worked.",
			steps: [["Fee structure per class", "/fees/setup"], ["Assign to students", "/fees/setup"], ["Invoices raised", "Student › Fees"], ["Collect payment", "/fees"], ["Receipt (PDF)", "Payment history"], ["Refund / waiver / adjustment", "Student › Fees"], ["Overdue list + reminders", "/fees/demand"]] },
		{ title: "Exams: from schedule to published report card", note: "A correction after publishing needs MARKS_CORRECT_AFTER_PUBLISH and creates a new version instead of editing the old one.",
			steps: [["Create exam", "/examinations"], ["Schedule per class + subject", "/examinations/exams/:id"], ["Teacher enters marks (draft)", "…/schedules/:id"], ["Submit", "MARKS_SUBMIT"], ["Approve", "MARKS_APPROVE"], ["Publish report cards", "Exam detail"], ["Guardian sees it", "/portal/guardian"]] },
		{ title: "Payroll: month end", note: "A mistake after finalizing is fixed with a correction run that supersedes the old one.",
			steps: [["Salary components", "/payroll"], ["Salary structure per staff", "Staff profile"], ["Generate run (draft)", "/payroll"], ["Add adjustment lines", "/payroll/runs/:id"], ["Finalize (locks)", "/payroll/runs/:id"], ["Staff sees payslip", "/portal/payslips"]] },
		{ title: "Buying and stocking",
			steps: [["Supplier", "/inventory › Suppliers"], ["Purchase order (draft)", "/purchase"], ["Submit → approve", "/purchase/orders/:id"], ["Receive into stock", "/purchase/orders/:id"], ["Issue / transfer / sell", "/inventory · /sales"], ["Low-stock alert", "/inventory · Dashboard"]] },
		{ title: "Teaching week",
			steps: [["Teacher assignment", "/academics › Teacher assignments"], ["Periods + timetable", "/academics › Timetable"], ["Lesson plan → review", "/learning"], ["Homework, quizzes, materials", "/learning · /portal/teaching"], ["Guardian submits for child", "/portal/guardian/children/:id/learning"], ["Teacher reviews", "/learning"]] },
	],

	portals: [
		{ name: "Guardian dashboard", route: "/portal/guardian", what: "For each linked child: today's attendance and monthly %, fee invoices and outstanding amount, published report cards, school and campus announcements, certificates.",
			screens: ["/portal/guardian", "…/children/:studentId/timetable", "…/children/:studentId/learning", "/portal/profile"], needs: ["Guardian linked to a student", "Guardian role marked as portal"] },
		{ name: "Teacher dashboard", route: "/portal/teacher", what: "Own assignments, leave balance and requests, own timetable, teaching tools and payslips.",
			screens: ["/portal/teacher", "/portal/teacher/timetable", "/portal/teaching", "/portal/payslips", "/portal/profile"], needs: ["Staff profile", "Teacher assignments", "Teacher role marked as portal"] },
		{ name: "Portal chooser", route: "/portal", what: "Someone who is both a guardian and a teacher picks which dashboard to open.", screens: ["/portal"], needs: [] },
	],

	platform: [
		{ key: "platform-platform-dashboard", name: "Platform dashboard", route: "/platform/dashboard", what: "Totals across every school: organisations, trials, outstanding invoices, MRR, growth and billed revenue." },
		{ key: "platform-organisations", name: "Organisations", route: "/platform/organisations", what: "Every school. The detail page holds module switches, subscription and status." },
		{ key: "platform-crm", name: "CRM leads", route: "/platform/crm/leads", what: "Prospective schools, converted into an organisation when they sign." },
		{ key: "platform-billing", name: "Billing", route: "/platform/plans", what: "Plans, subscriptions per school, invoices and payments." },
		{ key: "platform-operations", name: "System health", route: "/platform/system-health", what: "API, database, background jobs, backups and secrets status. Also: activity log, usage, support tickets and platform settings." },
	],
};
