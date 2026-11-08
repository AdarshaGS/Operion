package com.operion.dashboard.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SectionRepository;
import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.StudentAttendanceRepository;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.RequirePermission;
import com.operion.authorization.RoleRepository;
import com.operion.communication.AnnouncementRepository;
import com.operion.communication.AnnouncementStatus;
import com.operion.dashboard.DashboardPreferenceRepository;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamStatus;
import com.operion.examination.MarksEntryRegisterRepository;
import com.operion.examination.MarksEntryRegisterStatus;
import com.operion.finance.FeeStructureRepository;
import com.operion.finance.InvoiceRepository;
import com.operion.finance.InvoiceStatus;
import com.operion.finance.PaymentRepository;
import com.operion.finance.PaymentStatus;
import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
import com.operion.inventory.InventoryService;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.ItemStatus;
import com.operion.library.BookRepository;
import com.operion.library.BookStatus;
import com.operion.library.BorrowRecordRepository;
import com.operion.library.BorrowStatus;
import com.operion.common.TenantContext;
import org.springframework.dao.DataAccessException;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.AcademicYearStatus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.DesignationRepository;
import com.operion.organisation.OrganisationConfiguration;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.purchase.PurchaseOrderRepository;
import com.operion.purchase.PurchaseOrderStatus;
import com.operion.sales.SaleRepository;
import com.operion.student.ApplicantRepository;
import com.operion.student.ApplicantStatus;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import com.operion.student.StudentStatus;
import com.operion.transport.RouteRepository;
import com.operion.transport.RouteStatus;
import com.operion.transport.StudentTransportAssignmentRepository;
import com.operion.transport.TransportAssignmentStatus;
import com.operion.transport.VehicleRepository;
import com.operion.transport.VehicleStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only cross-module rollup for the post-login Dashboard (#30) - a handful of
 * aggregate queries against existing repositories, no new entities, no write paths. No
 * dedicated service, same "controller talks straight to repositories" shape as
 * CampusController - this is pure aggregation, not business logic. Lives outside the
 * four core packages (see ai-context/platform-boundaries.md) since it necessarily
 * depends on nearly every vertical module.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequirePermission("ORGANISATION_MANAGE")
public class DashboardController {

	// Org provisioning now seeds only the Owner role (DefaultRoles - GitHub #92 removed
	// the old Teacher/Accountant/Front Desk/Guardian defaults), so "Roles" reads done as
	// soon as one role beyond Owner exists.
	private static final int DEFAULT_ROLE_COUNT = 1;
	private static final int TREND_DAYS = 30;
	private static final int FEE_CHART_MONTHS = 6;

	private final StudentRepository studentRepository;
	private final CampusRepository campusRepository;
	private final StudentAttendanceRepository studentAttendanceRepository;
	private final InvoiceRepository invoiceRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final DepartmentRepository departmentRepository;
	private final DesignationRepository designationRepository;
	private final ExamRepository examRepository;
	private final BookRepository bookRepository;
	private final BorrowRecordRepository borrowRecordRepository;
	private final VehicleRepository vehicleRepository;
	private final RouteRepository routeRepository;
	private final StudentTransportAssignmentRepository studentTransportAssignmentRepository;
	private final ItemRepository itemRepository;
	private final ItemCategoryRepository itemCategoryRepository;
	private final AnnouncementRepository announcementRepository;
	private final RoleRepository roleRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final SaleRepository saleRepository;
	private final PurchaseOrderRepository purchaseOrderRepository;
	private final InventoryService inventoryService;
	private final SchoolClassRepository schoolClassRepository;
	private final AcademicYearRepository academicYearRepository;
	private final FeeStructureRepository feeStructureRepository;
	private final OrganisationConfigurationRepository organisationConfigurationRepository;
	private final DashboardPreferenceRepository dashboardPreferenceRepository;
	private final PaymentRepository paymentRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final SectionRepository sectionRepository;
	private final ApplicantRepository applicantRepository;
	private final MarksEntryRegisterRepository marksEntryRegisterRepository;

	public DashboardController(StudentRepository studentRepository, CampusRepository campusRepository,
			StudentAttendanceRepository studentAttendanceRepository, InvoiceRepository invoiceRepository,
			StaffProfileRepository staffProfileRepository, DepartmentRepository departmentRepository,
			DesignationRepository designationRepository, ExamRepository examRepository, BookRepository bookRepository,
			BorrowRecordRepository borrowRecordRepository, VehicleRepository vehicleRepository, RouteRepository routeRepository,
			StudentTransportAssignmentRepository studentTransportAssignmentRepository, ItemRepository itemRepository,
			ItemCategoryRepository itemCategoryRepository, AnnouncementRepository announcementRepository,
			RoleRepository roleRepository, OrganisationMembershipRepository membershipRepository, SaleRepository saleRepository,
			PurchaseOrderRepository purchaseOrderRepository, InventoryService inventoryService,
			SchoolClassRepository schoolClassRepository, AcademicYearRepository academicYearRepository,
			FeeStructureRepository feeStructureRepository, OrganisationConfigurationRepository organisationConfigurationRepository,
			DashboardPreferenceRepository dashboardPreferenceRepository, PaymentRepository paymentRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, SectionRepository sectionRepository,
			ApplicantRepository applicantRepository, MarksEntryRegisterRepository marksEntryRegisterRepository) {
		this.studentRepository = studentRepository;
		this.campusRepository = campusRepository;
		this.studentAttendanceRepository = studentAttendanceRepository;
		this.invoiceRepository = invoiceRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.departmentRepository = departmentRepository;
		this.designationRepository = designationRepository;
		this.examRepository = examRepository;
		this.bookRepository = bookRepository;
		this.borrowRecordRepository = borrowRecordRepository;
		this.vehicleRepository = vehicleRepository;
		this.routeRepository = routeRepository;
		this.studentTransportAssignmentRepository = studentTransportAssignmentRepository;
		this.itemRepository = itemRepository;
		this.itemCategoryRepository = itemCategoryRepository;
		this.announcementRepository = announcementRepository;
		this.roleRepository = roleRepository;
		this.membershipRepository = membershipRepository;
		this.saleRepository = saleRepository;
		this.purchaseOrderRepository = purchaseOrderRepository;
		this.inventoryService = inventoryService;
		this.schoolClassRepository = schoolClassRepository;
		this.academicYearRepository = academicYearRepository;
		this.feeStructureRepository = feeStructureRepository;
		this.organisationConfigurationRepository = organisationConfigurationRepository;
		this.dashboardPreferenceRepository = dashboardPreferenceRepository;
		this.paymentRepository = paymentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.sectionRepository = sectionRepository;
		this.applicantRepository = applicantRepository;
		this.marksEntryRegisterRepository = marksEntryRegisterRepository;
	}

	@GetMapping("/summary")
	public DashboardSummaryResponse summary() {
		long activeStudents = studentRepository.countByStatus(StudentStatus.ACTIVE);
		long departments = departmentRepository.count();
		long designations = designationRepository.count();
		Access access = access();

		// Each module section is returned only to a caller who may view that module, the same rule
		// the page applies - ORGANISATION_MANAGE opens the dashboard, not every module's figures.
		return new DashboardSummaryResponse(
				new EnrollmentSummary(activeStudents, campusRepository.count()),
				access.any("ATTENDANCE_VIEW", "STAFF_ATTENDANCE_VIEW") ? attendanceToday() : null,
				access.any("FEE_VIEW") ? fees() : null,
				access.any("HR_VIEW")
						? new StaffSummary(staffProfileRepository.countByStatus(StaffProfileStatus.ACTIVE), departments, designations)
						: null,
				access.any("EXAM_VIEW") ? new ExaminationSummary(examRepository.countByStatus(ExamStatus.ACTIVE)) : null,
				access.any("LIBRARY_VIEW") ? library() : null,
				access.any("TRANSPORT_VIEW") ? transport() : null,
				access.any("INVENTORY_VIEW")
						? new InventorySummary(itemRepository.countByStatus(ItemStatus.ACTIVE), itemCategoryRepository.count(),
								lowStockItemCount())
						: null,
				access.any("COMMUNICATION_VIEW")
						? new CommunicationSummary(announcementRepository.countByStatusAndPublishedAtAfter(
								AnnouncementStatus.PUBLISHED, startOfThisMonth()))
						: null,
				access.any("SALES_VIEW") ? sales() : null,
				access.any("PURCHASE_VIEW") ? purchase() : null,
				setupChecklist(activeStudents),
				preferences());
	}

	@GetMapping("/charts")
	public DashboardChartsResponse charts() {
		Access access = access();
		return new DashboardChartsResponse(access.any("ATTENDANCE_VIEW") ? attendanceTrend() : List.of(),
				access.any("FEE_VIEW") ? feeCollections() : List.of(), access.any("STUDENT_VIEW") ? studentsByClass() : List.of());
	}

	/** What the caller may see, resolved the same way PermissionInterceptor does: the Owner and
	 * ALL_FUNCTIONS see everything, anyone else needs one of the module's view permissions. */
	private Access access() {
		Long actorId = TenantContext.getActorId();
		if (membershipRepository.existsByUserIdAndStatusAndOwner(actorId, MembershipStatus.ACTIVE, true)) {
			return new Access(true, Set.of());
		}
		Set<String> codes = membershipRepository.findActivePermissionCodesForUser(actorId);
		return new Access(codes.contains("ALL_FUNCTIONS"), codes);
	}

	private record Access(boolean everything, Set<String> codes) {
		boolean any(String... anyOf) {
			return everything || java.util.Arrays.stream(anyOf).anyMatch(codes::contains);
		}
	}

	@PostMapping("/setup-progress/dismiss")
	public void dismissSetupProgress() {
		ensurePreferenceRow();
		dashboardPreferenceRepository.markSetupProgressDismissed(TenantContext.getActorId());
	}

	@PostMapping("/quick-actions/dismiss")
	public void dismissQuickActions() {
		ensurePreferenceRow();
		dashboardPreferenceRepository.markQuickActionsDismissed(TenantContext.getActorId());
	}

	private DashboardPreferences preferences() {
		return dashboardPreferenceRepository.findByUserId(TenantContext.getActorId())
				.map(preference -> new DashboardPreferences(preference.isSetupProgressDismissed(), preference.isQuickActionsDismissed()))
				.orElse(new DashboardPreferences(false, false));
	}

	private void ensurePreferenceRow() {
		try {
			dashboardPreferenceRepository.createIfMissing(TenantContext.getActorId());
		} catch (DataAccessException concurrentInsert) {
			// Another first dismissal by the same user created the row - it is there either way.
		}
	}

	/** Cheap existence/count probes only - this runs on every dashboard load. The first six flags
	 * keep their original meaning for the screens that still read them; the rest back the
	 * school-lifecycle card (see SetupChecklist for the step -> flag mapping). */
	private SetupChecklist setupChecklist(long activeStudents) {
		OrganisationConfiguration configuration = organisationConfigurationRepository.findById(TenantContext.getOrganisationId())
				.orElse(null);
		boolean hasCurrentYear = academicYearRepository.existsByCurrentTrue();
		boolean profileConfigured = false;
		if (configuration != null) {
			profileConfigured = isInstitutionProfileConfigured(configuration.getPrimaryContactName(),
					configuration.getPrimaryContactEmail(), configuration.getTimezone());
		}
		boolean hasClosedYear = academicYearRepository.existsByStatus(AcademicYearStatus.CLOSED);
		boolean hasClasses = schoolClassRepository.count() > 0;
		boolean studentRecordsExist = studentRepository.count() > 0;
		boolean attendanceStarted = studentAttendanceRepository.existsByIdNotNull();
		boolean feesConfigured = feeStructureRepository.count() > 0;

		return new SetupChecklist(roleRepository.count() > DEFAULT_ROLE_COUNT,
				membershipRepository.countByStatus(MembershipStatus.ACTIVE) > 1, hasCurrentYear && hasClasses, activeStudents > 0,
				feesConfigured, attendanceStarted, profileConfigured, isAcademicYearSet(hasCurrentYear),
				isClassStructureCreated(hasClasses, sectionRepository.count() > 0),
				areSubjectsMappedInLatestYear(academicYearRepository, schoolClassRepository),
				isAdmissionsStepDone(applicantRepository.countByStatus(ApplicantStatus.CONVERTED) > 0, studentRecordsExist),
				studentRecordsExist,
				examRepository.countByStatus(ExamStatus.ACTIVE) > 0,
				marksEntryRegisterRepository.existsByRegisterStatus(MarksEntryRegisterStatus.APPROVED),
				bookRepository.countByStatus(BookStatus.ACTIVE) > 0, hasClosedYear);
	}

	/** Setup step "Institution setup": the school profile contact and the business-settings timezone are filled in - the academic year is its own step, managed under Settings. */
	static boolean isInstitutionProfileConfigured(String primaryContactName, String primaryContactEmail, String timezone) {
		return !isBlank(primaryContactName) && !isBlank(primaryContactEmail) && !isBlank(timezone);
	}

	/** Setup step "Academic year creation": an academic year is current (active). A closed year does not count, and
	 * neither does a draft that was never made current. */
	static boolean isAcademicYearSet(boolean hasCurrentAcademicYear) {
		return hasCurrentAcademicYear;
	}

	/** Setup step "Class & section creation": a class alone is not enough - students are enrolled into a section. */
	static boolean isClassStructureCreated(boolean hasClasses, boolean hasSections) {
		return hasClasses && hasSections;
	}

	/** Setup step "Subject configuration", evaluated against the latest academic year: the current year if there is
	 * one, else the most recently closed year (latest end date, ties by id). A DRAFT-only school, or one with no
	 * years, has no latest year, so the step is not done. Other years' classes are ignored so a past year's
	 * leftovers can't hold the step open. */
	static boolean areSubjectsMappedInLatestYear(AcademicYearRepository academicYearRepository,
			SchoolClassRepository schoolClassRepository) {
		return academicYearRepository.findByCurrentTrue()
				.or(() -> academicYearRepository.findFirstByStatusOrderByEndDateDescIdDesc(AcademicYearStatus.CLOSED))
				.map(year -> areSubjectsMappedToEveryClass(schoolClassRepository.countActiveInAcademicYear(year.getId()),
						schoolClassRepository.countActiveInAcademicYearWithoutActiveSubject(year.getId())))
				.orElse(false);
	}

	/** The counting rule: at least one ACTIVE class and every one of them has at least one ACTIVE subject mapping -
	 * one mapped class out of ten is not "configured". The classes are the year-scoped SchoolClass offerings (what
	 * sections and enrolments hang off). */
	static boolean areSubjectsMappedToEveryClass(long activeClasses, long activeClassesWithoutSubjects) {
		return activeClasses > 0 && activeClassesWithoutSubjects == 0;
	}

	/** Setup step "Student admissions": an applicant has been admitted, or - for schools that import or add existing
	 * students directly and never use the admissions pipeline - students already exist, so the step
	 * can't strand them. */
	static boolean isAdmissionsStepDone(boolean hasAdmittedApplicant, boolean hasStudentRecords) {
		return hasAdmittedApplicant || hasStudentRecords;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private List<DashboardChartsResponse.AttendanceTrendPoint> attendanceTrend() {
		LocalDate today = LocalDate.now();
		// date -> [marked, present]; days with no marks simply don't appear (weekends, holidays)
		Map<LocalDate, long[]> byDate = new TreeMap<>();
		for (Object[] row : studentAttendanceRepository.countByDateAndStatusBetween(today.minusDays(TREND_DAYS - 1), today)) {
			long[] counts = byDate.computeIfAbsent((LocalDate) row[0], date -> new long[2]);
			long count = (Long) row[2];
			counts[0] += count;
			if (row[1] == AttendanceStatus.PRESENT) {
				counts[1] += count;
			}
		}
		return byDate.entrySet().stream().map(entry -> {
			long marked = entry.getValue()[0];
			long present = entry.getValue()[1];
			return new DashboardChartsResponse.AttendanceTrendPoint(entry.getKey(), marked, present,
					marked == 0 ? 0 : (int) (present * 100 / marked));
		}).toList();
	}

	private List<DashboardChartsResponse.FeeCollectionPoint> feeCollections() {
		YearMonth thisMonth = YearMonth.now();
		YearMonth first = thisMonth.minusMonths(FEE_CHART_MONTHS - 1);
		LocalDate fromDate = first.atDay(1);
		LocalDate toDate = thisMonth.atEndOfMonth();
		Map<YearMonth, BigDecimal> collected = sumByMonth(
				paymentRepository.sumAmountByPaymentDateBetween(PaymentStatus.CLEARED, fromDate, toDate));
		Map<YearMonth, BigDecimal> invoiced = sumByMonth(invoiceRepository.sumTotalAmountByDueDateBetween(fromDate, toDate));
		List<DashboardChartsResponse.FeeCollectionPoint> points = new java.util.ArrayList<>();
		// every month in the window is emitted, zero-filled, so the x-axis has no gaps
		for (YearMonth month = first; !month.isAfter(thisMonth); month = month.plusMonths(1)) {
			points.add(new DashboardChartsResponse.FeeCollectionPoint(month.toString(),
					collected.getOrDefault(month, BigDecimal.ZERO), invoiced.getOrDefault(month, BigDecimal.ZERO)));
		}
		return points;
	}

	private static Map<YearMonth, BigDecimal> sumByMonth(List<Object[]> rowsOfDateAndAmount) {
		Map<YearMonth, BigDecimal> totals = new HashMap<>();
		for (Object[] row : rowsOfDateAndAmount) {
			totals.merge(YearMonth.from((LocalDate) row[0]), (BigDecimal) row[1], BigDecimal::add);
		}
		return totals;
	}

	private List<DashboardChartsResponse.ClassEnrollmentPoint> studentsByClass() {
		// LinkedHashMap keeps the grade-sequence order from the query; merging handles two classes sharing a display name
		Map<String, Long> byName = new LinkedHashMap<>();
		for (Object[] row : studentEnrollmentRepository.countCurrentEnrolledBySchoolClass()) {
			byName.merge((String) row[0], (Long) row[1], Long::sum);
		}
		return byName.entrySet().stream().map(entry -> new DashboardChartsResponse.ClassEnrollmentPoint(entry.getKey(), entry.getValue())).toList();
	}

	private AttendanceSummary attendanceToday() {
		LocalDate today = LocalDate.now();
		long present = studentAttendanceRepository.countByAttendanceDateAndAttendanceStatus(today, AttendanceStatus.PRESENT);
		long absent = studentAttendanceRepository.countByAttendanceDateAndAttendanceStatus(today, AttendanceStatus.ABSENT);
		long late = studentAttendanceRepository.countByAttendanceDateAndAttendanceStatus(today, AttendanceStatus.LATE);
		long halfDay = studentAttendanceRepository.countByAttendanceDateAndAttendanceStatus(today, AttendanceStatus.HALF_DAY);
		long marked = studentAttendanceRepository.countByAttendanceDate(today);
		int ratePercent = marked == 0 ? 0 : (int) (present * 100 / marked);
		return new AttendanceSummary(present, absent, late, halfDay, marked, ratePercent);
	}

	private FeeSummary fees() {
		BigDecimal totalInvoiced = invoiceRepository.sumTotalAmount();
		// Refunds larger than the payments they reverse would otherwise show a negative amount
		// collected and a negative rate on the tile.
		BigDecimal totalCollected = invoiceRepository.sumAmountPaid().max(BigDecimal.ZERO);
		int collectionRatePercent = totalInvoiced.signum() == 0 ? 0
				: totalCollected.multiply(BigDecimal.valueOf(100)).divide(totalInvoiced, 0, java.math.RoundingMode.HALF_UP).intValue();
		long overdueInvoices = invoiceRepository.countByStatusNotAndDueDateBefore(InvoiceStatus.PAID, LocalDate.now());
		return new FeeSummary(totalInvoiced, totalCollected, collectionRatePercent, totalInvoiced.subtract(totalCollected),
				overdueInvoices);
	}

	private LibrarySummary library() {
		long currentlyBorrowed = borrowRecordRepository.countByStatus(BorrowStatus.BORROWED);
		long overdueBorrows = borrowRecordRepository.countByStatusAndDueDateBefore(BorrowStatus.BORROWED, LocalDate.now());
		return new LibrarySummary(bookRepository.countByStatus(BookStatus.ACTIVE), currentlyBorrowed, overdueBorrows);
	}

	private TransportSummary transport() {
		return new TransportSummary(vehicleRepository.countByStatus(VehicleStatus.ACTIVE),
				routeRepository.countByStatus(RouteStatus.ACTIVE),
				studentTransportAssignmentRepository.countByStatus(TransportAssignmentStatus.ACTIVE));
	}

	private SalesSummary sales() {
		LocalDate today = LocalDate.now();
		return new SalesSummary(saleRepository.sumTotalAmountBySaleDate(today),
				saleRepository.sumTotalAmountBySaleDateGreaterThanEqual(today.withDayOfMonth(1)));
	}

	private PurchaseSummary purchase() {
		return new PurchaseSummary(
				purchaseOrderRepository.countByStatusNotIn(List.of(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.CANCELLED)));
	}

	/** Reuses InventoryService.getLowStockItems() (the real balance/reorder-level logic) per active
	 * campus rather than re-deriving the same arithmetic here - campus counts are small enough that
	 * looping is cheaper than maintaining a second copy of that calculation. */
	private long lowStockItemCount() {
		// An item below its reorder level at two campuses is still one low-stock item.
		return campusRepository.findAll().stream()
				.flatMap(campus -> inventoryService.getLowStockItems(campus).stream())
				.map(lowStock -> lowStock.item().getId())
				.distinct()
				.count();
	}

	private static Instant startOfThisMonth() {
		return LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
	}
}
