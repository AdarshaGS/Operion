package com.operion.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.operion.academic.Section;
import com.operion.academic.Subject;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.academic.TeacherAssignmentType;
import com.operion.attendance.ClassAttendanceRegister;
import com.operion.attendance.ClassAttendanceRegisterRepository;
import com.operion.attendance.ClassAttendanceRegisterStatus;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.examination.ExamSchedule;
import com.operion.examination.MarksEntry;
import com.operion.examination.MarksEntryRepository;
import com.operion.identity.Person;
import com.operion.learning.AnswerSheetRepository;
import com.operion.learning.Homework;
import com.operion.learning.HomeworkRepository;
import com.operion.learning.LearningMaterialRepository;
import com.operion.learning.QuizRepository;
import com.operion.student.StudentEnrollment;
import org.junit.jupiter.api.Test;

/** The record-reading metric providers, with their repositories mocked. */
class KpiProvidersTest {

	private static Person person(long id) {
		Person person = mock(Person.class);
		when(person.getId()).thenReturn(id);
		return person;
	}

	private static Section section(long id) {
		Section section = mock(Section.class);
		when(section.getId()).thenReturn(id);
		return section;
	}

	private static Subject subject(long id) {
		Subject subject = mock(Subject.class);
		when(subject.getId()).thenReturn(id);
		return subject;
	}

	private static TeacherAssignment assignment(Person teacher, Section section, Subject subject, TeacherAssignmentType type) {
		TeacherAssignment a = mock(TeacherAssignment.class);
		when(a.getTeacherPerson()).thenReturn(teacher);
		when(a.getSection()).thenReturn(section);
		when(a.getSubject()).thenReturn(subject);
		when(a.getAssignmentType()).thenReturn(type);
		when(a.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
		when(a.getEndDate()).thenReturn(null);
		return a;
	}

	@Test
	void engagementCountsAreScaledToThirtyDays() {
		HomeworkRepository homeworkRepository = mock(HomeworkRepository.class);
		Homework homework = mock(Homework.class);
		Person author = person(7);
		when(homework.getAssignedBy()).thenReturn(author);
		when(homeworkRepository.findByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(List.of(homework, homework, homework));
		EngagementKpiProvider provider = new EngagementKpiProvider(homeworkRepository, mock(QuizRepository.class),
				mock(LearningMaterialRepository.class), mock(AnswerSheetRepository.class));

		MetricValue thirty = provider.compute(KpiMetric.HOMEWORK_ISSUED, new KpiPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), null).get(7L);
		MetricValue fifteen = provider.compute(KpiMetric.HOMEWORK_ISSUED, new KpiPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)), null).get(7L);

		assertThat(thirty.value()).isEqualTo(3.0);
		assertThat(thirty.sample()).isEqualTo(3);
		assertThat(fifteen.value()).isEqualTo(6.0);
	}

	@Test
	void registersAreCountedOnTheClassTeacherAndTodaysDraftIsNotYetLate() {
		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);
		Section section = section(5);
		ClassAttendanceRegister onTime = new ClassAttendanceRegister(null, section, today);
		onTime.submit();
		ClassAttendanceRegister late = new ClassAttendanceRegister(null, section, yesterday);
		late.submit();
		ClassAttendanceRegister neverClosed = new ClassAttendanceRegister(null, section, yesterday.minusDays(1));
		ClassAttendanceRegister todaysDraft = new ClassAttendanceRegister(null, section, today);
		ClassAttendanceRegister legacy = mock(ClassAttendanceRegister.class);
		when(legacy.getSection()).thenReturn(section);
		when(legacy.getRegisterStatus()).thenReturn(ClassAttendanceRegisterStatus.SUBMITTED);
		when(legacy.getSubmittedAt()).thenReturn(null);
		when(legacy.getAttendanceDate()).thenReturn(yesterday);
		ClassAttendanceRegisterRepository registerRepository = mock(ClassAttendanceRegisterRepository.class);
		when(registerRepository.findByAttendanceDateBetween(any(), any())).thenReturn(List.of(onTime, late, neverClosed, todaysDraft, legacy));
		TeacherAssignmentRepository assignmentRepository = mock(TeacherAssignmentRepository.class);
		TeacherAssignment classTeacher = assignment(person(7), section, null, TeacherAssignmentType.HOMEROOM);
		when(assignmentRepository.findByStatus(TeacherAssignmentStatus.ACTIVE)).thenReturn(List.of(classTeacher));

		MetricValue value = new RegisterKpiProvider(registerRepository, assignmentRepository)
				.compute(KpiMetric.REGISTER_SAME_DAY, new KpiPeriod(today.minusDays(5), today), null).get(7L);

		// on time, late and never closed count; today's open draft and the untimestamped legacy row do not
		assertThat(value.sample()).isEqualTo(3);
		assertThat(value.value()).isEqualTo(100.0 / 3);
	}

	@Test
	void marksEnteredWithinTheGraceDaysAreOnTime() {
		LocalDate examDate = LocalDate.of(2026, 9, 10);
		Person entrant = person(7);
		MarksEntry soon = marks(11L, examDate, examDate.plusDays(3));
		MarksEntry late = marks(11L, examDate, examDate.plusDays(9));
		MarksEntry unknownUser = marks(99L, examDate, examDate.plusDays(1));
		MarksEntryRepository marksRepository = mock(MarksEntryRepository.class);
		when(marksRepository.findByExamScheduleExamDateBetween(any(), any())).thenReturn(List.of(soon, late, unknownUser));
		OrganisationMembershipRepository membershipRepository = mock(OrganisationMembershipRepository.class);
		OrganisationMembership membership = mock(OrganisationMembership.class);
		when(membership.getStatus()).thenReturn(MembershipStatus.ACTIVE);
		when(membership.getPerson()).thenReturn(entrant);
		when(membershipRepository.findByUserId(11L)).thenReturn(List.of(membership));
		when(membershipRepository.findByUserId(99L)).thenReturn(List.of());

		MarksEntryKpiProvider provider = new MarksEntryKpiProvider(marksRepository, membershipRepository);
		var values = provider.compute(KpiMetric.MARKS_ENTRY_ON_TIME, new KpiPeriod(examDate.minusDays(5), examDate.plusDays(20)), 7);

		assertThat(values).containsOnlyKeys(7L);
		assertThat(values.get(7L).value()).isEqualTo(50.0);
		assertThat(values.get(7L).sample()).isEqualTo(2);
		// with no grace at all, neither entry made after the exam day is on time
		assertThat(provider.compute(KpiMetric.MARKS_ENTRY_ON_TIME, new KpiPeriod(examDate.minusDays(5), examDate.plusDays(20)), 0).get(7L).value())
				.isEqualTo(0.0);
	}

	private static MarksEntry marks(long userId, LocalDate examDate, LocalDate enteredOn) {
		MarksEntry entry = mock(MarksEntry.class);
		ExamSchedule schedule = mock(ExamSchedule.class);
		when(schedule.getExamDate()).thenReturn(examDate);
		when(entry.getExamSchedule()).thenReturn(schedule);
		when(entry.getCreatedBy()).thenReturn(userId);
		when(entry.getCreatedAt()).thenReturn(enteredOn.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
		return entry;
	}

	@Test
	void outcomesAreSharedByTheTeachersOfThatSectionAndSubjectOnTheExamDate() {
		Section sectionA = section(1);
		Subject maths = subject(10);
		Subject art = subject(20);
		Person anil = person(7);
		Person bela = person(8);
		TeacherAssignmentRepository assignmentRepository = mock(TeacherAssignmentRepository.class);
		List<TeacherAssignment> assignments = List.of(
				assignment(anil, sectionA, maths, TeacherAssignmentType.SUBJECT),
				assignment(bela, sectionA, maths, TeacherAssignmentType.CO_TEACHER),
				assignment(bela, sectionA, art, TeacherAssignmentType.SUBJECT),
				assignment(anil, sectionA, null, TeacherAssignmentType.HOMEROOM));
		when(assignmentRepository.findAll()).thenReturn(assignments);
		LocalDate examDate = LocalDate.of(2026, 9, 10);
		MarksEntryRepository marksRepository = mock(MarksEntryRepository.class);
		List<MarksEntry> entries = List.of(
				outcome(sectionA, maths, examDate, 80, false), outcome(sectionA, maths, examDate, 30, false),
				outcome(sectionA, maths, examDate, 0, true), outcome(sectionA, art, examDate, 90, false));
		when(marksRepository.findByExamScheduleExamDateBetween(any(), any())).thenReturn(entries);
		OutcomeKpiProvider provider = new OutcomeKpiProvider(marksRepository, assignmentRepository);
		KpiPeriod period = new KpiPeriod(examDate.minusDays(5), examDate.plusDays(5));

		var passRate = provider.compute(KpiMetric.OUTCOME_PASS_RATE, period, null);
		var average = provider.compute(KpiMetric.OUTCOME_AVERAGE_MARKS, period, null);

		// maths: 80 passes, 30 fails (pass mark 40), the absent student is ignored - both maths teachers share it
		assertThat(passRate.get(7L).value()).isEqualTo(50.0);
		assertThat(passRate.get(7L).sample()).isEqualTo(2);
		assertThat(average.get(7L).value()).isEqualTo(55.0);
		// Bela also teaches art (one student, 90)
		assertThat(passRate.get(8L).sample()).isEqualTo(3);
		assertThat(average.get(8L).value()).isEqualTo((80 + 30 + 90) / 3.0);
	}

	private static MarksEntry outcome(Section section, Subject subject, LocalDate examDate, double obtained, boolean absent) {
		MarksEntry entry = mock(MarksEntry.class);
		ExamSchedule schedule = mock(ExamSchedule.class);
		when(schedule.getSubject()).thenReturn(subject);
		when(schedule.getExamDate()).thenReturn(examDate);
		when(schedule.getPassMarks()).thenReturn(40.0);
		when(schedule.getMaxMarks()).thenReturn(100.0);
		StudentEnrollment enrollment = mock(StudentEnrollment.class);
		when(enrollment.getSection()).thenReturn(section);
		when(entry.getExamSchedule()).thenReturn(schedule);
		when(entry.getStudentEnrollment()).thenReturn(enrollment);
		when(entry.getMarksObtained()).thenReturn(obtained);
		when(entry.isAbsent()).thenReturn(absent);
		return entry;
	}

	@Test
	void reviewerRatingIsTheAverageAsAPercentageOfTheTopRating() {
		TeacherFeedbackRepository feedbackRepository = mock(TeacherFeedbackRepository.class);
		TeacherFeedback four = mock(TeacherFeedback.class);
		TeacherFeedback five = mock(TeacherFeedback.class);
		Person teacher = person(7);
		when(four.getTeacher()).thenReturn(teacher);
		when(four.getRating()).thenReturn(4);
		when(five.getTeacher()).thenReturn(teacher);
		when(five.getRating()).thenReturn(5);
		when(feedbackRepository.findBySourceInAndCreatedAtBetween(any(), any(), any())).thenReturn(List.of(four, five));

		MetricValue value = new FeedbackKpiProvider(feedbackRepository)
				.compute(KpiMetric.FEEDBACK_RATING, new KpiPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), null).get(7L);

		assertThat(value.value()).isEqualTo(90.0);
		assertThat(value.sample()).isEqualTo(2);
	}
}
