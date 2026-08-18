import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { ClaimInvitePage } from "./auth/ClaimInvitePage";
import { ClaimStaffInvitePage } from "./auth/ClaimStaffInvitePage";
import { ForgotPasswordPage } from "./auth/ForgotPasswordPage";
import { LoginPage } from "./auth/LoginPage";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { ResetPasswordPage } from "./auth/ResetPasswordPage";
import { AppLayout } from "./layout/AppLayout";
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
import { MarketingPage } from "./marketing/MarketingPage";
import { SettingsPage } from "./modules/settings/SettingsPage";
import { UserDetailPage } from "./modules/settings/UserDetailPage";
import { RouteDetailPage } from "./modules/transport/RouteDetailPage";
import { TransportPage } from "./modules/transport/TransportPage";
import { StudentCreatePage } from "./modules/students/StudentCreatePage";
import { StudentDetailPage } from "./modules/students/StudentDetailPage";
import { StudentListPage } from "./modules/students/StudentListPage";
import { DashboardPage } from "./platform/DashboardPage";
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
					<Route path="/forgot-password" element={<ForgotPasswordPage />} />
					<Route path="/reset-password" element={<ResetPasswordPage />} />
					<Route element={<ProtectedRoute />}>
						<Route element={<AppLayout />}>
							<Route index element={<Navigate to="/students" replace />} />
							<Route path="/students" element={<StudentListPage />} />
							<Route path="/students/new" element={<StudentCreatePage />} />
							<Route path="/students/:studentId" element={<StudentDetailPage />} />
							<Route path="/academics" element={<AcademicsPage />} />
							<Route path="/academics/classes/:classId" element={<SchoolClassSectionsPage />} />
							<Route path="/academics/classes/:classId/sections/:sectionId" element={<SectionDetailPage />} />
							<Route path="/attendance" element={<AttendancePage />} />
							<Route path="/fees" element={<FeesPage />} />
							<Route path="/examinations" element={<ExaminationsPage />} />
							<Route path="/examinations/exams/:examId" element={<ExamDetailPage />} />
							<Route path="/examinations/exams/:examId/schedules/:scheduleId" element={<MarksEntryPage />} />
							<Route path="/communication" element={<CommunicationPage />} />
							<Route path="/transport" element={<TransportPage />} />
							<Route path="/transport/routes/:routeId" element={<RouteDetailPage />} />
							<Route path="/library" element={<LibraryPage />} />
							<Route path="/library/books/:bookId" element={<BookDetailPage />} />
							<Route path="/inventory" element={<InventoryPage />} />
							<Route path="/inventory/items/:itemId" element={<ItemDetailPage />} />
							<Route path="/hr" element={<HrPage />} />
							<Route path="/hr/staff/new" element={<StaffCreatePage />} />
							<Route path="/hr/staff/:staffProfileId" element={<StaffDetailPage />} />
							<Route path="/settings" element={<SettingsPage />} />
							<Route path="/settings/users/:userId" element={<UserDetailPage />} />
						</Route>
					</Route>
					<Route path="/platform/login" element={<PlatformLoginPage />} />
					<Route element={<PlatformProtectedRoute />}>
						<Route element={<PlatformLayout />}>
							<Route path="/platform" element={<Navigate to="/platform/dashboard" replace />} />
							<Route path="/platform/dashboard" element={<DashboardPage />} />
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
