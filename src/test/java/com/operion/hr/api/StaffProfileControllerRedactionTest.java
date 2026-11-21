package com.operion.hr.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.operion.authorization.PermissionChecker;
import com.operion.common.TenantContext;
import com.operion.hr.EmploymentType;
import com.operion.hr.HrService;
import com.operion.hr.StaffAccessService;
import com.operion.hr.StaffBankDetail;
import com.operion.hr.StaffBankDetailRepository;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.organisation.Designation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** What a caller gets back depends on what they hold: {@code access} needs MEMBERSHIP_VIEW or MEMBERSHIP_MANAGE, and the full
 * bank account number / tax identifier needs HR_STAFF_MANAGE. The checker mock answers per requested code, like the real one. */
class StaffProfileControllerRedactionTest {

	private static final long ACTOR = 42L;

	private final StaffProfileRepository staffProfileRepository = mock(StaffProfileRepository.class);
	private final StaffBankDetailRepository staffBankDetailRepository = mock(StaffBankDetailRepository.class);
	private final StaffAccessService staffAccessService = mock(StaffAccessService.class);
	private final PermissionChecker permissionChecker = mock(PermissionChecker.class);
	private final HrService hrService = mock(HrService.class);
	private final StaffProfileController controller = new StaffProfileController(hrService, staffProfileRepository, null, null, null,
			staffBankDetailRepository, null, null, null, null, null, staffAccessService, permissionChecker);

	private StaffProfile staff;
	private StaffBankDetail bank;

	@BeforeEach
	void setUp() {
		TenantContext.set(1L, ACTOR);
		Person person = new Person("Ravi", "Menon");
		ReflectionTestUtils.setField(person, "id", 7L);
		Designation designation = new Designation("Teacher");
		staff = new StaffProfile(person, null, "EMP-1", designation, null, LocalDate.of(2020, 6, 1), EmploymentType.PERMANENT);
		ReflectionTestUtils.setField(staff, "id", 3L);
		bank = new StaffBankDetail(staff, "Ravi Menon", "123456781234", "Test Bank", "TEST0001", "ABCDE1234F");

		when(staffProfileRepository.findById(3L)).thenReturn(Optional.of(staff));
		when(staffProfileRepository.findByStatusIn(any())).thenReturn(List.of(staff));
		when(staffBankDetailRepository.findByStaffProfileId(3L)).thenReturn(Optional.of(bank));
		StaffAccessSummary summary = new StaffAccessSummary(true, 9L, "Teacher role", "ACTIVE");
		when(staffAccessService.summarise(7L)).thenReturn(summary);
		when(staffAccessService.summariseAll(any())).thenReturn(Map.of(7L, summary));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	/** The caller holds exactly these codes. */
	private void holds(String... codes) {
		List<String> held = List.of(codes);
		when(permissionChecker.holdsAny(eq(ACTOR), any())).thenAnswer(invocation -> {
			Collection<String> asked = invocation.getArgument(1);
			return asked.stream().anyMatch(held::contains);
		});
	}

	@Test
	void aCallerWithoutMembershipPermissionsGetsNoAccessBlockOnTheDetail() {
		holds("HR_VIEW");

		assertThat(controller.get(3L).access()).isNull();
		verify(staffAccessService, never()).summarise(any());
	}

	@Test
	void aCallerWithoutMembershipPermissionsGetsNoAccessBlockOnAnyListRow() {
		holds("HR_VIEW", "HR_STAFF_MANAGE", "HR_PAYROLL_VIEW");

		List<StaffProfileResponse> rows = controller.list(null);

		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).access()).isNull();
		assertThat(rows.get(0).employeeCode()).isEqualTo("EMP-1");
		verify(staffAccessService, never()).summariseAll(any());
	}

	@Test
	void membershipViewOrManageEachGetTheAccessBlock() {
		for (String code : List.of("MEMBERSHIP_VIEW", "MEMBERSHIP_MANAGE")) {
			holds("HR_VIEW", code);

			StaffAccessSummary detail = controller.get(3L).access();
			assertThat(detail).isNotNull();
			assertThat(detail.hasAccess()).isTrue();
			assertThat(detail.status()).isEqualTo("ACTIVE");
			assertThat(detail.roleName()).isEqualTo("Teacher role");
			assertThat(detail.membershipId()).isEqualTo(9L);

			assertThat(controller.list(null).get(0).access()).isEqualTo(detail);
		}
	}

	@Test
	void aStaffManagerSeesTheFullAccountNumberAndTaxIdentifier() {
		holds("HR_PAYROLL_VIEW", "HR_STAFF_MANAGE");

		StaffBankDetailResponse response = controller.getBankDetails(3L);

		assertThat(response.bankAccountNumber()).isEqualTo("123456781234");
		assertThat(response.taxIdentifier()).isEqualTo("ABCDE1234F");
	}

	@Test
	void aPayrollViewerWhoIsNotAStaffManagerSeesOnlyTheLastFour() {
		holds("HR_PAYROLL_VIEW");

		StaffBankDetailResponse response = controller.getBankDetails(3L);

		assertThat(response.bankAccountNumber()).isEqualTo("********1234").doesNotContain("56781234").endsWith("1234");
		assertThat(response.taxIdentifier()).isEqualTo("******234F");
		assertThat(response.bankBranchCode()).isEqualTo("TEST0001");
		assertThat(response.bankAccountHolderName()).isEqualTo("Ravi Menon");
	}

	@Test
	void theSaveResponseFollowsTheSameRule() {
		when(hrService.upsertBankDetails(any(), any(), any(), any(), any(), any())).thenReturn(bank);
		UpsertStaffBankDetailsRequest request = new UpsertStaffBankDetailsRequest("Ravi Menon", "********1234", "Test Bank", "TEST0001", "******234F");

		holds("HR_PAYROLL_VIEW");
		assertThat(controller.upsertBankDetails(3L, request).bankAccountNumber()).isEqualTo("********1234");

		holds("HR_PAYROLL_VIEW", "HR_STAFF_MANAGE");
		assertThat(controller.upsertBankDetails(3L, request).bankAccountNumber()).isEqualTo("123456781234");
		verify(hrService, times(2)).upsertBankDetails(any(), eq("Ravi Menon"), eq("********1234"), eq("Test Bank"),
				eq("TEST0001"), eq("******234F"));
	}

	@Test
	void shortValuesAreHiddenEntirelyAndEmptyOnesPassThrough() {
		assertThat(StaffBankDetailResponse.mask("1234")).isEqualTo("****");
		assertThat(StaffBankDetailResponse.mask("12")).isEqualTo("**");
		assertThat(StaffBankDetailResponse.mask("12345")).isEqualTo("*2345");
		assertThat(StaffBankDetailResponse.mask(null)).isNull();
		assertThat(StaffBankDetailResponse.mask("")).isEmpty();
	}
}
