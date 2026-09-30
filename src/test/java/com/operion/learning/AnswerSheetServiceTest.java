package com.operion.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.examination.Exam;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamSchedule;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.ExamType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Upload, on-screen marking, release and guardian visibility of scanned answer sheets (#230). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, AnswerSheetService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AnswerSheetServiceTest extends LearningTestBase {

	@Autowired private AnswerSheetService sheetService;
	@Autowired private ExamRepository examRepository;
	@Autowired private ExamScheduleRepository scheduleRepository;

	private ExamSchedule mathsSchedule;

	@BeforeEach
	void setUpExam() {
		Exam exam = examRepository.save(new Exam(year, "Term 1", ExamType.UNIT_TEST));
		mathsSchedule = scheduleRepository.save(new ExamSchedule(exam, schoolClass, maths, LocalDate.of(2025, 9, 1), 50.0, 20.0));
	}

	private AnswerSheet uploaded() {
		return sheetService.upload(ashaTeachesMathsInA, mathsSchedule.getId(), raviEnrollment.getId(), List.of("ref-1", "ref-2"));
	}

	private static AnswerSheetService.AnnotationInput marks(int page, double m) {
		return new AnswerSheetService.AnnotationInput(page, AnnotationKind.MARKS, 0.5, 0.5, null, m);
	}

	@Test
	void teacherUploadsPagesAndReuploadReplacesThem() {
		AnswerSheet sheet = uploaded();
		sheetService.upload(ashaTeachesMathsInA, mathsSchedule.getId(), raviEnrollment.getId(), List.of("ref-3"));
		assertThat(sheetService.detail(ashaTeachesMathsInA, sheet.getId()).pages()).extracting(AnswerSheetPage::getAssetRef).containsExactly("ref-3");
	}

	@Test
	void teacherOfAnotherSectionCannotUploadOrMark() {
		assertThatThrownBy(() -> sheetService.upload(balaTeachesScienceInB, mathsSchedule.getId(), raviEnrollment.getId(), List.of("r")))
				.isInstanceOf(AuthorizationDeniedException.class);
		AnswerSheet sheet = uploaded();
		assertThatThrownBy(() -> sheetService.mark(balaTeachesScienceInB, sheet.getId(), List.of(), 10.0, null, Instant.now()))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> sheetService.detail(balaTeachesScienceInB, sheet.getId())).isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void uploadRequiresAPageAndAStudentInScope() {
		assertThatThrownBy(() -> sheetService.upload(ashaTeachesMathsInA, mathsSchedule.getId(), raviEnrollment.getId(), List.of()))
				.hasMessageContaining("at least one");
		Exam exam = examRepository.save(new Exam(year, "Other", ExamType.OTHER));
		ExamSchedule sectionBOnly = scheduleRepository
				.save(new ExamSchedule(exam, schoolClass, sectionB, maths, LocalDate.of(2025, 9, 2), 50.0, 20.0));
		assertThatThrownBy(() -> sheetService.upload(admin, sectionBOnly.getId(), raviEnrollment.getId(), List.of("r")))
				.hasMessageContaining("not in the class/section");
	}

	@Test
	void markingSumsMarksAnnotationsAndValidatesAgainstMax() {
		AnswerSheet sheet = uploaded();
		var detail = sheetService.mark(ashaTeachesMathsInA, sheet.getId(), List.of(marks(1, 4), marks(2, 6),
				new AnswerSheetService.AnnotationInput(1, AnnotationKind.TICK, 0.1, 0.2, null, null)), null, "Good", Instant.now());
		assertThat(detail.sheet().getTotalMarks()).isEqualTo(10.0);
		assertThat(detail.sheet().getStatus()).isEqualTo(AnswerSheetStatus.MARKED);
		assertThat(detail.annotations()).hasSize(3);

		assertThatThrownBy(() -> sheetService.mark(ashaTeachesMathsInA, sheet.getId(), List.of(marks(1, 60)), null, null, Instant.now()))
				.hasMessageContaining("between 0 and 50");
		assertThatThrownBy(() -> sheetService.mark(ashaTeachesMathsInA, sheet.getId(),
				List.of(new AnswerSheetService.AnnotationInput(9, AnnotationKind.TICK, 0.1, 0.1, null, null)), null, null, Instant.now()))
				.hasMessageContaining("unknown page");
		assertThatThrownBy(() -> sheetService.mark(ashaTeachesMathsInA, sheet.getId(),
				List.of(new AnswerSheetService.AnnotationInput(1, AnnotationKind.TICK, 1.5, 0.1, null, null)), null, null, Instant.now()))
				.hasMessageContaining("within the page");
	}

	@Test
	void remarkingReplacesAnnotationsAndExplicitTotalWins() {
		AnswerSheet sheet = uploaded();
		sheetService.mark(ashaTeachesMathsInA, sheet.getId(), List.of(marks(1, 4)), null, null, Instant.now());
		var detail = sheetService.mark(ashaTeachesMathsInA, sheet.getId(), List.of(marks(1, 2)), 12.5, null, Instant.now());
		assertThat(detail.annotations()).hasSize(1);
		assertThat(detail.sheet().getTotalMarks()).isEqualTo(12.5);
	}

	@Test
	void releaseNeedsMarkingAndLocksTheSheet() {
		AnswerSheet sheet = uploaded();
		assertThatThrownBy(() -> sheetService.release(ashaTeachesMathsInA, sheet.getId(), Instant.now())).hasMessageContaining("marked");
		sheetService.mark(ashaTeachesMathsInA, sheet.getId(), List.of(marks(1, 5)), null, null, Instant.now());
		sheetService.release(ashaTeachesMathsInA, sheet.getId(), Instant.now());
		assertThatThrownBy(() -> sheetService.mark(ashaTeachesMathsInA, sheet.getId(), List.of(), 1.0, null, Instant.now()))
				.hasMessageContaining("released");
		assertThatThrownBy(() -> sheetService.upload(ashaTeachesMathsInA, mathsSchedule.getId(), raviEnrollment.getId(), List.of("x")))
				.hasMessageContaining("already marked");
	}

	@Test
	void guardianSeesOnlyReleasedSheets() {
		AnswerSheet sheet = uploaded();
		sheetService.mark(ashaTeachesMathsInA, sheet.getId(), List.of(marks(1, 5)), null, null, Instant.now());
		assertThat(sheetService.releasedForStudent(ravi.getId())).isEmpty();
		sheetService.release(ashaTeachesMathsInA, sheet.getId(), Instant.now());
		assertThat(sheetService.releasedForStudent(ravi.getId())).hasSize(1);
		assertThat(sheetService.releasedForStudent(sita.getId())).isEmpty();
	}

	@Test
	void rosterListsStudentsWithSheetStatusAndRespectsScope() {
		uploaded();
		var rows = sheetService.roster(ashaTeachesMathsInA, mathsSchedule.getId());
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).sheet()).isNotNull();
		assertThat(rows.get(0).pages()).isEqualTo(2);
		assertThat(rows.get(1).sheet()).isNull();
		assertThatThrownBy(() -> sheetService.roster(balaTeachesScienceInB, mathsSchedule.getId())).isInstanceOf(AuthorizationDeniedException.class);
		assertThat(sheetService.schedules(ashaTeachesMathsInA)).hasSize(1);
		assertThat(sheetService.schedules(balaTeachesScienceInB)).isEmpty();
	}
}
