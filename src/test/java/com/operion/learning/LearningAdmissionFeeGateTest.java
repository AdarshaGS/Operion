package com.operion.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.examination.Exam;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamSchedule;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.ExamType;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import com.operion.student.AdmissionFeeClearanceMode;
import com.operion.student.AdmissionFeeGate;
import com.operion.student.AdmissionFeePendingException;
import com.operion.student.AdmissionSettings;
import com.operion.student.AdmissionSettingsRepository;
import com.operion.student.FeeClearanceStatus;
import com.operion.student.StudentEnrollment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admission-fee gate on the learning module's teacher-side writes: homework review, answer-sheet upload,
 * marking and release, and reward grants. A PENDING student in BLOCK mode is rejected with
 * AdmissionFeePendingException; the child's own homework submission stays allowed, and the gate lets everything
 * through in any other mode, with the FEES module off, or for any other clearance status.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, HomeworkService.class, AnswerSheetService.class, LeaderboardService.class,
		QuizService.class, AdmissionFeeGate.class, ModuleAccessService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LearningAdmissionFeeGateTest extends LearningTestBase {

	@Autowired private HomeworkService homeworkService;
	@Autowired private AnswerSheetService sheetService;
	@Autowired private LeaderboardService leaderboardService;
	@Autowired private ExamRepository examRepository;
	@Autowired private ExamScheduleRepository scheduleRepository;
	@Autowired private AdmissionSettingsRepository admissionSettingsRepository;
	@Autowired private ModuleAccessService moduleAccessService;

	private ExamSchedule mathsSchedule;

	@BeforeEach
	void setUpExam() {
		Exam exam = examRepository.save(new Exam(year, "Term 1", ExamType.UNIT_TEST));
		mathsSchedule = scheduleRepository.save(new ExamSchedule(exam, schoolClass, maths, LocalDate.of(2025, 9, 1), 50.0, 20.0));
	}

	private void setMode(AdmissionFeeClearanceMode mode) {
		Long organisationId = TenantContext.getOrganisationId();
		AdmissionSettings settings = admissionSettingsRepository.findByOrganisationId(organisationId).orElseGet(AdmissionSettings::new);
		settings.setFeeClearanceMode(mode);
		admissionSettingsRepository.save(settings);
	}

	private void setStatus(StudentEnrollment enrollment, FeeClearanceStatus status) {
		StudentEnrollment stored = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
		stored.changeFeeClearanceStatus(status);
		raviEnrollment = enrollmentRepository.save(stored);
	}

	private HomeworkSubmission submittedHomework() {
		Homework hw = homeworkService.assign(ashaTeachesMathsInA, sectionA.getId(), maths.getId(), "Fractions", null,
				LocalDate.now().plusDays(1), null);
		return homeworkService.submitForChild(guardianOf(raviEnrollment), hw.getId(), "done", null);
	}

	@Test
	void reviewingHomeworkIsRejectedForAPendingStudentInBlockMode() {
		HomeworkSubmission submission = submittedHomework();
		setMode(AdmissionFeeClearanceMode.BLOCK);
		setStatus(raviEnrollment, FeeClearanceStatus.PENDING);

		assertThatThrownBy(() -> homeworkService.review(ashaTeachesMathsInA, submission.getId(), "Good"))
				.isInstanceOfSatisfying(AdmissionFeePendingException.class,
						e -> assertThat(e.getEnrollmentIds()).containsExactly(raviEnrollment.getId()));
	}

	@Test
	void theChildsOwnHomeworkSubmissionIsStillAllowedWhilePending() {
		Homework hw = homeworkService.assign(ashaTeachesMathsInA, sectionA.getId(), maths.getId(), "Fractions", null,
				LocalDate.now().plusDays(1), null);
		setMode(AdmissionFeeClearanceMode.BLOCK);
		setStatus(raviEnrollment, FeeClearanceStatus.PENDING);

		HomeworkSubmission submission = homeworkService.submitForChild(guardianOf(raviEnrollment), hw.getId(), "done", null);

		assertThat(submission.getId()).isNotNull();
	}

	@Test
	void uploadingAnAnswerSheetIsRejectedForAPendingStudentInBlockMode() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		setStatus(raviEnrollment, FeeClearanceStatus.PENDING);

		assertThatThrownBy(() -> sheetService.upload(ashaTeachesMathsInA, mathsSchedule.getId(), raviEnrollment.getId(), List.of("r1")))
				.isInstanceOf(AdmissionFeePendingException.class);
	}

	@Test
	void markingAndReleasingAnAnswerSheetAreRejectedForAPendingStudentInBlockMode() {
		AnswerSheet sheet = sheetService.upload(ashaTeachesMathsInA, mathsSchedule.getId(), raviEnrollment.getId(), List.of("r1"));
		setMode(AdmissionFeeClearanceMode.BLOCK);
		setStatus(raviEnrollment, FeeClearanceStatus.PENDING);

		assertThatThrownBy(() -> sheetService.mark(ashaTeachesMathsInA, sheet.getId(), List.of(), 10.0, null, Instant.now()))
				.isInstanceOf(AdmissionFeePendingException.class);
		assertThatThrownBy(() -> sheetService.release(ashaTeachesMathsInA, sheet.getId(), Instant.now()))
				.isInstanceOf(AdmissionFeePendingException.class);
	}

	@Test
	void grantingARewardIsRejectedForAPendingStudentInBlockMode() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		setStatus(raviEnrollment, FeeClearanceStatus.PENDING);

		assertThatThrownBy(() -> leaderboardService.grant(ashaTeachesMathsInA, raviEnrollment.getId(), "Star", 5, "Helpful", Instant.now()))
				.isInstanceOf(AdmissionFeePendingException.class);
	}

	@Test
	void everythingIsAllowedInTrackAndOffModes() {
		setStatus(raviEnrollment, FeeClearanceStatus.PENDING);
		for (AdmissionFeeClearanceMode mode : List.of(AdmissionFeeClearanceMode.TRACK, AdmissionFeeClearanceMode.OFF)) {
			setMode(mode);
			assertThat(leaderboardService.grant(ashaTeachesMathsInA, raviEnrollment.getId(), "Star " + mode, 5, "Helpful", Instant.now())).isNotNull();
		}
	}

	@Test
	void everythingIsAllowedWhenTheFeesModuleIsOff() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		setStatus(raviEnrollment, FeeClearanceStatus.PENDING);
		moduleAccessService.setEnabled(TenantContext.getOrganisationId(), AppModule.FEES, false, "test");

		assertThat(leaderboardService.grant(ashaTeachesMathsInA, raviEnrollment.getId(), "Star", 5, "Helpful", Instant.now())).isNotNull();
	}

	@Test
	void everyOtherClearanceStatusIsAllowedInBlockMode() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		for (FeeClearanceStatus status : List.of(FeeClearanceStatus.NOT_REQUIRED, FeeClearanceStatus.CLEARED, FeeClearanceStatus.EXEMPTED)) {
			setStatus(raviEnrollment, status);
			assertThat(leaderboardService.grant(ashaTeachesMathsInA, raviEnrollment.getId(), "Star " + status, 5, "Helpful", Instant.now()))
					.isNotNull();
		}
	}
}
