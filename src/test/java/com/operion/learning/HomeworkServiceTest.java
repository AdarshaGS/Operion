package com.operion.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.common.InvalidRequestException;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Homework assignment scope, submission/late/review rules and tracking (#226). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, HomeworkService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HomeworkServiceTest extends LearningTestBase {

	@Autowired private HomeworkService homeworkService;

	@Test
	void teacherCanAssignOnlyForTheirOwnSectionAndSubject() {
		Homework ok = homeworkService.assign(ashaTeachesMathsInA, sectionA.getId(), maths.getId(), "Fractions", "p.12",
				LocalDate.now().plusDays(2), null);
		assertThat(ok.getStatus()).isEqualTo(HomeworkStatus.ACTIVE);

		assertThatThrownBy(() -> homeworkService.assign(ashaTeachesMathsInA, sectionB.getId(), maths.getId(), "X", null,
				LocalDate.now(), null)).isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> homeworkService.assign(ashaTeachesMathsInA, sectionA.getId(), science.getId(), "X", null,
				LocalDate.now(), null)).isInstanceOf(AuthorizationDeniedException.class);
		// A manager may assign anywhere.
		assertThat(homeworkService.assign(admin, sectionB.getId(), science.getId(), "Plants", null, LocalDate.now(), null))
				.isNotNull();
	}

	@Test
	void listIsScopedToTheTeachersSectionsButAdminSeesAll() {
		homeworkService.assign(admin, sectionA.getId(), maths.getId(), "A-hw", null, LocalDate.now(), null);
		homeworkService.assign(admin, sectionB.getId(), science.getId(), "B-hw", null, LocalDate.now(), null);

		assertThat(homeworkService.list(ashaTeachesMathsInA, null)).extracting(v -> v.homework().getTitle())
				.containsExactly("A-hw");
		assertThat(homeworkService.list(admin, null)).hasSize(2);
		assertThatThrownBy(() -> homeworkService.list(ashaTeachesMathsInA, sectionB.getId()))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void guardianSubmissionIsTrackedAndReviewLocksIt() {
		Homework hw = homeworkService.assign(ashaTeachesMathsInA, sectionA.getId(), maths.getId(), "Fractions", null,
				LocalDate.now().plusDays(1), null);
		var guardian = guardianOf(raviEnrollment);

		HomeworkSubmission submission = homeworkService.submitForChild(guardian, hw.getId(), "done", null);
		assertThat(submission.isLate()).isFalse();
		// Resubmitting before review replaces in place - still one row per (homework, student).
		homeworkService.submitForChild(guardian, hw.getId(), "done v2", null);

		var view = homeworkService.list(ashaTeachesMathsInA, sectionA.getId()).get(0);
		assertThat(view.enrolled()).isEqualTo(2);
		assertThat(view.submitted()).isEqualTo(1);

		var tracking = homeworkService.tracking(ashaTeachesMathsInA, hw.getId());
		assertThat(tracking).extracting(r -> r.submission() != null).containsExactly(true, false);

		homeworkService.review(ashaTeachesMathsInA, submission.getId(), "Good");
		assertThatThrownBy(() -> homeworkService.submitForChild(guardian, hw.getId(), "again", null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void lateSubmissionIsFlaggedAndOtherSectionsHomeworkIsNotFound() {
		Homework overdue = homeworkService.assign(ashaTeachesMathsInA, sectionA.getId(), maths.getId(), "Old", null,
				LocalDate.now().minusDays(3), null);
		assertThat(homeworkService.submitForChild(guardianOf(raviEnrollment), overdue.getId(), "late", null).isLate()).isTrue();

		Homework other = homeworkService.assign(admin, sectionB.getId(), science.getId(), "B", null, LocalDate.now(), null);
		assertThatThrownBy(() -> homeworkService.submitForChild(guardianOf(raviEnrollment), other.getId(), "x", null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> homeworkService.submitForChild(guardianOf(raviEnrollment), overdue.getId(), " ", null))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void cancelledHomeworkIsHiddenFromGuardiansAndNotSubmittable() {
		Homework hw = homeworkService.assign(ashaTeachesMathsInA, sectionA.getId(), maths.getId(), "Gone", null,
				LocalDate.now(), null);
		homeworkService.cancel(ashaTeachesMathsInA, hw.getId());

		assertThat(homeworkService.forChild(raviEnrollment)).isEmpty();
		assertThatThrownBy(() -> homeworkService.submitForChild(guardianOf(raviEnrollment), hw.getId(), "x", null))
				.isInstanceOf(IllegalStateException.class);
	}
}
