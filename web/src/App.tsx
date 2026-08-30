import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { ClaimInvitePage } from "./auth/ClaimInvitePage";
import { ClaimStaffInvitePage } from "./auth/ClaimStaffInvitePage";
import { JobApplicationPage } from "./careers/JobApplicationPage";
import { EmailVerifyPage } from "./auth/EmailVerifyPage";
import { ForgotPasswordPage } from "./auth/ForgotPasswordPage";
import { IndexRedirect } from "./auth/IndexRedirect";
import { LoginPage } from "./auth/LoginPage";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { ResetPasswordPage } from "./auth/ResetPasswordPage";
import { AppLayout } from "./layout/AppLayout";
import { AcademicSetupPage } from "./modules/academics/AcademicSetupPage";
import { AcademicsPage } from "./modules/academics/AcademicsPage";
import { SchoolClassSectionsPage } from "./modules/academics/SchoolClassSectionsPage";
import { SectionDetailPage } from "./modules/academics/SectionDetailPage";
import { AttendancePage } from "./modules/attendance/AttendancePage";
import { CommunicationPage } from "./modules/communication/CommunicationPage";
import { ExamDetailPage } from "./modules/examinations/ExamDetailPage";
import { ExaminationsPage } from "./modules/examinations/ExaminationsPage";
import { MarksEntryPage } from "./modules/examinations/MarksEntryPage";
import { FeesPage } from "./modules/fees/FeesPage";
import { HrPage } from "./modules/hr/HrPage";
import { StaffCreatePage } from "./modules/hr/StaffCreatePage";
import { StaffDetailPage } from "./modules/hr/StaffDetailPage";
import { ItemDetailPage } from "./modules/inventory/ItemDetailPage";
import { InventoryPage } from "./modules/inventory/InventoryPage";
import { BookDetailPage } from "./modules/library/BookDetailPage";
import { LibraryPage } from "./modules/library/LibraryPage";
import { MessagingPage } from "./modules/messaging/MessagingPage";
import { PurchaseOrderDetailPage } from "./modules/purchase/PurchaseOrderDetailPage";
import { PurchasePage } from "./modules/purchase/PurchasePage";
import { ReportDetailPage } from "./modules/reporting/ReportDetailPage";
import { ReportsPage } from "./modules/reporting/ReportsPage";
import { CustomerHistoryPage } from "./modules/sales/CustomerHistoryPage";
import { SaleDetailPage } from "./modules/sales/SaleDetailPage";
import { SalesPage } from "./modules/sales/SalesPage";
import { MarketingPage } from "./marketing/MarketingPage";
import { DashboardPage } from "./modules/dashboard/DashboardPage";
import { MembersPage } from "./modules/members/MembersPage";
import { StructureSetupPage } from "./modules/setup/StructureSetupPage";
import { ProfilePage } from "./modules/profile/ProfilePage";
import { SettingsPage } from "./modules/settings/SettingsPage";
import { SettingsSectionPage } from "./modules/settings/SettingsSectionPage";
import { UserDetailPage } from "./modules/settings/UserDetailPage";
import { RouteDetailPage } from "./modules/transport/RouteDetailPage";
import { TransportPage } from "./modules/transport/TransportPage";
import { StudentCreatePage } from "./modules/students/StudentCreatePage";
import { StudentDetailPage } from "./modules/students/StudentDetailPage";
import { StudentListPage } from "./modules/students/StudentListPage";
import { DashboardPage as PlatformDashboardPage } from "./platform/DashboardPage";
import { OrganisationDetailPage } from "./platform/OrganisationDetailPage";
import { OrganisationsPage } from "./platform/OrganisationsPage";
import { PlansPage } from "./platform/PlansPage";
import { PlatformAuthProvider } from "./platform/auth/PlatformAuthContext";
import { PlatformLoginPage } from "./platform/auth/PlatformLoginPage";
import { PlatformProtectedRoute } from "./platform/auth/PlatformProtectedRoute";
import { PlatformLayout } from "./platform/layout/PlatformLayout";

function App() {
	return (
		<AuthProvider>
			<PlatformAuthProvider>
				<Routes>
					<Route path="/welcome" element={<MarketingPage />} />
					<Route path="/login" element={<LoginPage />} />
					<Route path="/claim-invite" element={<ClaimInvitePage />} />
					<Route path="/claim-staff-invite" element={<ClaimStaffInvitePage />} />
					<Route path="/careers" element={<JobApplicationPage />} />
					<Route path="/forgot-password" element={<ForgotPasswordPage />} />
					<Route path="/reset-password" element={<ResetPasswordPage />} />
					<Route path="/verify-email" element={<EmailVerifyPage />} />
					<Route element={<ProtectedRoute />}>
						<Route element={<AppLayout />}>
							<Route index element={<IndexRedirect />} />
							<Route path="/dashboard" element={<DashboardPage />} />
							<Route path="/students" element={<StudentListPage />} />
							<Route path="/students/new" element={<StudentCreatePage />} />
							<Route path="/students/:studentId" element={<StudentDetailPage />} />
							<Route path="/academics" element={<AcademicsPage />} />
							<Route path="/academics/setup" element={<AcademicSetupPage />} />
							<Route path="/academics/classes/:classId" element={<SchoolClassSectionsPage />} />
							<Route path="/academics/classes/:classId/sections/:sectionId" element={<SectionDetailPage />} />
							<Route path="/attendance" element={<AttendancePage />} />
							<Route path="/attendance/mark" element={<AttendancePage />} />
							<Route path="/fees" element={<FeesPage />} />
							<Route path="/fees/setup" element={<FeesPage />} />
							<Route path="/fees/collect" element={<FeesPage />} />
							<Route path="/examinations" element={<ExaminationsPage />} />
							<Route path="/examinations/exams/:examId" element={<ExamDetailPage />} />
							<Route path="/examinations/exams/:examId/schedules/:scheduleId" element={<MarksEntryPage />} />
							<Route path="/communication" element={<CommunicationPage />} />
							<Route path="/messaging" element={<MessagingPage />} />
							<Route path="/transport" element={<TransportPage />} />
							<Route path="/transport/routes/:routeId" element={<RouteDetailPage />} />
							<Route path="/library" element={<LibraryPage />} />
							<Route path="/library/books/:bookId" element={<BookDetailPage />} />
							<Route path="/inventory" element={<InventoryPage />} />
							<Route path="/inventory/items/:itemId" element={<ItemDetailPage />} />
							<Route path="/purchase" element={<PurchasePage />} />
							<Route path="/purchase/orders/:orderId" element={<PurchaseOrderDetailPage />} />
							<Route path="/reports" element={<ReportsPage />} />
							<Route path="/reports/:reportId" element={<ReportDetailPage />} />
							<Route path="/sales" element={<SalesPage />} />
							<Route path="/sales/:saleId" element={<SaleDetailPage />} />
							<Route path="/sales/customers/:customerId" element={<CustomerHistoryPage />} />
							<Route path="/hr" element={<HrPage />} />
							<Route path="/hr/staff/new" element={<StaffCreatePage />} />
							<Route path="/hr/staff/:staffProfileId" element={<StaffDetailPage />} />
							<Route path="/members" element={<MembersPage />} />
							<Route path="/members/invite" element={<MembersPage autoOpenInvite />} />
							<Route path="/setup/structure" element={<StructureSetupPage />} />
							<Route path="/profile" element={<ProfilePage />} />
							<Route path="/settings" element={<SettingsPage />} />
							<Route path="/settings/users/:userId" element={<UserDetailPage />} />
							<Route path="/settings/:section" element={<SettingsSectionPage />} />
						</Route>
					</Route>
					<Route path="/platform/login" element={<PlatformLoginPage />} />
					<Route element={<PlatformProtectedRoute />}>
						<Route element={<PlatformLayout />}>
							<Route path="/platform" element={<Navigate to="/platform/dashboard" replace />} />
							<Route path="/platform/dashboard" element={<PlatformDashboardPage />} />
							<Route path="/platform/organisations" element={<OrganisationsPage />} />
							<Route path="/platform/organisations/:organisationId" element={<OrganisationDetailPage />} />
							<Route path="/platform/plans" element={<PlansPage />} />
						</Route>
					</Route>
					<Route path="*" element={<Navigate to="/" replace />} />
				</Routes>
			</PlatformAuthProvider>
		</AuthProvider>
	);
}

export default App;
