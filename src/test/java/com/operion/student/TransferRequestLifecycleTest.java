package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves TransferRequestService: fromCampus/toCampus must differ, approve/reject are
 * one-way transitions off PENDING, and decidedBy is recorded - same shape as
 * LeaveBalanceApprovalTest for LeaveRequest.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, TransferRequestService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransferRequestLifecycleTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private TransferRequestService transferRequestService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Student student, Campus mainCampus, Campus branchCampus) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus mainCampus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Campus branchCampus = campusRepository.save(new Campus("Branch Campus", "BRANCH"));
		Person person = personRepository.save(new Person("Anaya", "Rao"));
		Student student = studentRepository.save(
				new Student(person, "STU-100", "ADM-100", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null));

		return new Fixture(student, mainCampus, branchCampus);
	}

	@Test
	void raisingWithTheSameFromAndToCampusIsRejected() {
		Fixture fixture = setUpFixture("transfer-same-campus");
		assertThatThrownBy(() -> transferRequestService.raise(fixture.student(), fixture.mainCampus(), fixture.mainCampus(), null, 1L))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void approvingAPendingRequestSucceeds() {
		Fixture fixture = setUpFixture("transfer-approve");
		TransferRequest request =
				transferRequestService.raise(fixture.student(), fixture.mainCampus(), fixture.branchCampus(), "Family relocation", 7L);

		transferRequestService.approve(request, 99L);

		assertThat(request.getStatus()).isEqualTo(TransferRequestStatus.APPROVED);
		assertThat(request.getRequestedBy()).isEqualTo(7L);
		assertThat(request.getDecidedBy()).isEqualTo(99L);
		assertThat(request.getDecidedAt()).isNotNull();
	}

	@Test
	void rejectingAPendingRequestSucceeds() {
		Fixture fixture = setUpFixture("transfer-reject");
		TransferRequest request = transferRequestService.raise(fixture.student(), fixture.mainCampus(), fixture.branchCampus(), null, 7L);

		transferRequestService.reject(request, 42L);

		assertThat(request.getStatus()).isEqualTo(TransferRequestStatus.REJECTED);
		assertThat(request.getDecidedBy()).isEqualTo(42L);
	}

	@Test
	void decidingAnAlreadyDecidedRequestIsRejected() {
		Fixture fixture = setUpFixture("transfer-decide-twice");
		TransferRequest request = transferRequestService.raise(fixture.student(), fixture.mainCampus(), fixture.branchCampus(), null, 7L);
		transferRequestService.approve(request, 99L);

		assertThatThrownBy(() -> transferRequestService.reject(request, 99L)).isInstanceOf(IllegalStateException.class);
	}
}
