package com.operion.dashboard.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.StudentAttendanceRepository;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.RequirePermission;
import com.operion.authorization.RoleRepository;
import com.operion.communication.AnnouncementRepository;
import com.operion.communication.AnnouncementStatus;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamStatus;
import com.operion.finance.InvoiceRepository;
import com.operion.finance.InvoiceStatus;
import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.ItemStatus;
import com.operion.library.BookRepository;
import com.operion.library.BookStatus;
import com.operion.library.BorrowRecordRepository;
import com.operion.library.BorrowStatus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.DesignationRepository;
import com.operion.student.StudentRepository;
import com.operion.student.StudentStatus;
import com.operion.transport.RouteRepository;
import com.operion.transport.RouteStatus;
import com.operion.transport.StudentTransportAssignmentRepository;
import com.operion.transport.TransportAssignmentStatus;
import com.operion.transport.VehicleRepository;
import com.operion.transport.VehicleStatus;
import org.springframework.web.bind.annotation.GetMapping;
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

	private static final int DEFAULT_ROLE_COUNT = 5;

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

	public DashboardController(StudentRepository studentRepository, CampusRepository campusRepository,
			StudentAttendanceRepository studentAttendanceRepository, InvoiceRepository invoiceRepository,
			StaffProfileRepository staffProfileRepository, DepartmentRepository departmentRepository,
			DesignationRepository designationRepository, ExamRepository examRepository, BookRepository bookRepository,
			BorrowRecordRepository borrowRecordRepository, VehicleRepository vehicleRepository, RouteRepository routeRepository,
			StudentTransportAssignmentRepository studentTransportAssignmentRepository, ItemRepository itemRepository,
			ItemCategoryRepository itemCategoryRepository, AnnouncementRepository announcementRepository,
			RoleRepository roleRepository, OrganisationMembershipRepository membershipRepository) {
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
	}

	@GetMapping("/summary")
	public DashboardSummaryResponse summary() {
		long activeStudents = studentRepository.countByStatus(StudentStatus.ACTIVE);
		long departments = departmentRepository.count();
		long designations = designationRepository.count();
		long activeMembers = membershipRepository.countByStatus(MembershipStatus.ACTIVE);
		long totalRoles = roleRepository.count();

		return new DashboardSummaryResponse(
				new EnrollmentSummary(activeStudents, campusRepository.count()),
				attendanceToday(),
				fees(),
				new StaffSummary(staffProfileRepository.countByStatus(StaffProfileStatus.ACTIVE), departments, designations),
				new ExaminationSummary(examRepository.countByStatus(ExamStatus.ACTIVE)),
				library(),
				transport(),
				new InventorySummary(itemRepository.countByStatus(ItemStatus.ACTIVE), itemCategoryRepository.count()),
				new CommunicationSummary(announcementRepository.countByStatusAndPublishedAtAfter(
						AnnouncementStatus.PUBLISHED, startOfThisMonth())),
				new SetupChecklist(departments > 0 && designations > 0, totalRoles > DEFAULT_ROLE_COUNT, activeMembers > 1,
						activeStudents > 0));
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
		BigDecimal totalCollected = invoiceRepository.sumAmountPaid();
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

	private static Instant startOfThisMonth() {
		return LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
	}
}
