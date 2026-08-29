// Permission-driven role fixtures - Operion has no hardcoded role names beyond the
// per-org "Owner" (GitHub #92), so "different roles" is modeled here as bundles of
// permission codes, each becoming its own Playwright project with its own storageState.
// See ai-context findings in the plan: catalog is closed/Flyway-seeded, RolePermission
// is the only tenant-configurable surface.

export type FixtureRoleName =
	| "owner"
	| "allFunctionsAdmin"
	| "readOnlyStaff"
	| "feesCollector"
	| "teacher"
	| "noPermissions"
	| "guardian"
	| "platformAdmin";

export interface PermissionRoleFixture {
	name: FixtureRoleName;
	/** Role name created via POST /api/v1/roles. Unset for owner/guardian/platformAdmin,
	 * which don't go through role creation (Owner bypasses via isOwner; Guardian is the
	 * managed role PortalInviteService.claim() auto-finds-or-creates; platformAdmin is a
	 * separate auth plane with no Role at all). */
	roleName?: string;
	permissionCodes?: string[];
}

const VIEW_PERMISSIONS = [
	"ATTENDANCE_VIEW",
	"CLASS_VIEW",
	"COMMUNICATION_VIEW",
	"EXAM_VIEW",
	"FEE_VIEW",
	"GRADE_LEVEL_VIEW",
	"GUARDIAN_VIEW",
	"HR_VIEW",
	"INVENTORY_VIEW",
	"LIBRARY_VIEW",
	"MEMBERSHIP_VIEW",
	"PURCHASE_VIEW",
	"REPORT_VIEW",
	"SALES_VIEW",
	"STAFF_ATTENDANCE_VIEW",
	"STUDENT_VIEW",
	"SUBJECT_VIEW",
	"TEACHER_ASSIGNMENT_VIEW",
	"TRANSPORT_VIEW",
];

export const ROLE_FIXTURES: PermissionRoleFixture[] = [
	{ name: "owner" },
	{ name: "allFunctionsAdmin", roleName: "E2E All Functions Admin", permissionCodes: ["ALL_FUNCTIONS"] },
	{ name: "readOnlyStaff", roleName: "E2E Read Only Staff", permissionCodes: VIEW_PERMISSIONS },
	{
		name: "feesCollector",
		roleName: "E2E Fees Collector",
		permissionCodes: ["STUDENT_VIEW", "FEE_VIEW", "FEE_COLLECT", "FEE_INVOICE_MANAGE", "FEE_ASSIGNMENT_MANAGE"],
	},
	{
		name: "teacher",
		roleName: "E2E Teacher",
		// A realistic classroom-teacher bundle: view their students/class/subject context,
		// mark attendance, enter marks - but nothing org-wide (roles, org config, pricing,
		// other modules). See tests/flows/teacher-flow.spec.ts's note on TeacherAssignment
		// being informational-only - this bundle is what the backend actually enforces.
		permissionCodes: [
			"STUDENT_VIEW",
			"CLASS_VIEW",
			"SUBJECT_VIEW",
			"TEACHER_ASSIGNMENT_VIEW",
			"ATTENDANCE_VIEW",
			"ATTENDANCE_MARK",
			"EXAM_VIEW",
			"MARKS_ENTER",
			"COMMUNICATION_VIEW",
		],
	},
	{ name: "noPermissions", roleName: "E2E No Permissions", permissionCodes: [] },
	{ name: "guardian" },
	{ name: "platformAdmin" },
];

export const PLATFORM_ADMIN_CREDENTIALS = {
	email: "admin@operion.platform",
	// Dev-only bootstrap credential seeded by V25__billing_schema.sql - rotate before
	// any real deployment, per that migration's own comment.
	password: "ChangeMe123!",
};
