package com.operion.examination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.examination.ExaminationService.BandInput;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves GradingScale/GradingScaleBand creation persists correctly - the actual
 * percentage-to-grade resolution is exercised end-to-end via ReportCardTest, since
 * resolveGrade() is a private implementation detail of publishReportCard().
 *
 * ExaminationService is constructed by hand rather than @Import'd - see
 * StudentAttendanceLifecycleTest for why (no ObjectMapper bean in this DataJpaTest slice).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GradingScaleTest {

	private ExaminationService examinationService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private ExamScheduleRepository examScheduleRepository;

	@Autowired
	private GradingScaleRepository gradingScaleRepository;

	@Autowired
	private GradingScaleBandRepository gradingScaleBandRepository;

	@Autowired
	private MarksEntryRepository marksEntryRepository;

	@Autowired
	private MarksEntryRegisterRepository marksEntryRegisterRepository;

	@Autowired
	private ReportCardRepository reportCardRepository;

	@Autowired
	private ExaminationSettingsRepository examinationSettingsRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@BeforeEach
	void setUpExaminationService() {
		examinationService = new ExaminationService(examRepository, examScheduleRepository, gradingScaleRepository,
				gradingScaleBandRepository, marksEntryRepository, marksEntryRegisterRepository, reportCardRepository,
				examinationSettingsRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void createsAScaleWithBandsOrderedByDescendingMinPercentage() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "grading-scale-school"));
		TenantContext.set(organisation.getId(), null);

		GradingScale scale = examinationService.createGradingScale("CBSE Standard", true, List.of(
				new BandInput("A+", 90.0, "Excellent"),
				new BandInput("A", 80.0, "Very good"),
				new BandInput("B", 60.0, "Good"),
				new BandInput("F", 0.0, "Fail")));

		List<GradingScaleBand> bands = gradingScaleBandRepository.findByGradingScaleIdOrderByMinPercentageDesc(scale.getId());
		assertThat(bands).extracting(GradingScaleBand::getGrade).containsExactly("A+", "A", "B", "F");
		assertThat(scale.isDefaultScale()).isTrue();
	}
}
