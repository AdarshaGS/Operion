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

/** Lesson plan lifecycle, review permission, draft privacy and calendar feed (#229). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, LessonPlanService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LessonPlanServiceTest extends LearningTestBase {

	private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

	@Autowired private LessonPlanService planService;
	@Autowired private LessonPlanRepository planRepository;

	private LessonPlan draft() {
		return planService.create(ashaTeachesMathsInA, sectionA.getId(), maths.getId(), DAY, "Fractions", "obj", "act", null);
	}

	@Test
	void approvedPlanIsLockedAndAppearsOnTheCalendarForTeacherAndGuardian() {
		LessonPlan plan = draft();
		planService.submit(ashaTeachesMathsInA, plan.getId());
		assertThat(planService.calendar(admin, DAY, DAY)).isEmpty();

		planService.approve(reviewer, plan.getId(), "Good");

		assertThat(planService.calendar(ashaTeachesMathsInA, DAY, DAY)).hasSize(1);
		assertThat(planService.calendarForChild(raviEnrollment, DAY, DAY)).hasSize(1);
		assertThat(planService.calendar(balaTeachesScienceInB, DAY, DAY)).isEmpty();
		assertThatThrownBy(() -> planService.edit(ashaTeachesMathsInA, plan.getId(), DAY, "New", null, null, null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void onlyReviewersReviewAndRejectionNeedsANoteThenResubmits() {
		LessonPlan plan = draft();
		planService.submit(ashaTeachesMathsInA, plan.getId());

		assertThatThrownBy(() -> planService.approve(ashaTeachesMathsInA, plan.getId(), null))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> planService.reject(reviewer, plan.getId(), " ")).isInstanceOf(InvalidRequestException.class);

		planService.reject(reviewer, plan.getId(), "Add objectives");
		assertThat(planRepository.findById(plan.getId()).orElseThrow().getStatus()).isEqualTo(LessonPlanStatus.REJECTED);
		planService.edit(ashaTeachesMathsInA, plan.getId(), DAY, "Fractions v2", "objectives", "act", null);
		planService.submit(ashaTeachesMathsInA, plan.getId());
		LessonPlan reloaded = planRepository.findById(plan.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(LessonPlanStatus.SUBMITTED);
		assertThat(reloaded.getReviewNote()).isNull();
	}

	@Test
	void draftsArePrivateToTheirAuthorAndOthersCannotChangeThem() {
		LessonPlan plan = draft();

		assertThat(planService.list(ashaTeachesMathsInA, null, null)).hasSize(1);
		assertThat(planService.list(reviewer, null, null)).isEmpty();
		assertThat(planService.list(admin, null, null)).extracting(LessonPlan::getId).doesNotContain(plan.getId());
		assertThatThrownBy(() -> planService.submit(balaTeachesScienceInB, plan.getId()))
				.isInstanceOf(AuthorizationDeniedException.class);

		planService.submit(ashaTeachesMathsInA, plan.getId());
		assertThat(planService.list(reviewer, null, LessonPlanStatus.SUBMITTED)).hasSize(1);
	}

	@Test
	void teacherCannotPlanForASectionTheyDoNotTeach() {
		assertThatThrownBy(() -> planService.create(ashaTeachesMathsInA, sectionB.getId(), maths.getId(), DAY, "X", null, null, null))
				.isInstanceOf(AuthorizationDeniedException.class);
	}
}
