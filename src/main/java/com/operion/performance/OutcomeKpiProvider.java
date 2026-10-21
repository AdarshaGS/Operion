package com.operion.performance;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentType;
import com.operion.examination.ExamSchedule;
import com.operion.examination.MarksEntry;
import com.operion.examination.MarksEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pass rate and average marks for the students of the sections and subjects a teacher taught on the exam date.
 * Absent students are left out. The result is shared by everyone teaching that section and subject, and depends
 * as much on the class as on the teacher, which is why these metrics are scored against the teacher's own
 * previous period rather than a target.
 */
@Component
public class OutcomeKpiProvider implements KpiMetricProvider {

	private final MarksEntryRepository marksRepository;
	private final TeacherAssignmentRepository assignmentRepository;

	public OutcomeKpiProvider(MarksEntryRepository marksRepository, TeacherAssignmentRepository assignmentRepository) {
		this.marksRepository = marksRepository;
		this.assignmentRepository = assignmentRepository;
	}

	@Override
	public Set<KpiMetric> supports() {
		return Set.of(KpiMetric.OUTCOME_PASS_RATE, KpiMetric.OUTCOME_AVERAGE_MARKS);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, MetricValue> compute(KpiMetric metric, KpiPeriod period, Integer days) {
		List<TeacherAssignment> assignments = assignmentRepository.findAll().stream()
				.filter(a -> a.getSubject() != null && a.getAssignmentType() != TeacherAssignmentType.HOMEROOM).toList();
		// per teacher: [0] = passes, [1] = students counted; sum of percentages kept separately
		Map<Long, int[]> counts = new HashMap<>();
		Map<Long, Double> percentSum = new HashMap<>();
		for (MarksEntry entry : marksRepository.findByExamScheduleExamDateBetween(period.from(), period.to())) {
			if (entry.isAbsent()) {
				continue;
			}
			ExamSchedule schedule = entry.getExamSchedule();
			Long sectionId = entry.getStudentEnrollment().getSection().getId();
			for (Long teacherId : teachersOf(assignments, sectionId, schedule.getSubject().getId(), schedule.getExamDate())) {
				int[] c = counts.computeIfAbsent(teacherId, k -> new int[2]);
				c[1]++;
				if (entry.getMarksObtained() >= schedule.getPassMarks()) {
					c[0]++;
				}
				percentSum.merge(teacherId, 100.0 * entry.getMarksObtained() / schedule.getMaxMarks(), Double::sum);
			}
		}
		Map<Long, MetricValue> values = new HashMap<>();
		counts.forEach((teacherId, c) -> values.put(teacherId, metric == KpiMetric.OUTCOME_PASS_RATE
				? MetricValue.ratio(c[0], c[1])
				: new MetricValue(percentSum.get(teacherId) / c[1], c[1])));
		return values;
	}

	private static Set<Long> teachersOf(List<TeacherAssignment> assignments, Long sectionId, Long subjectId, LocalDate on) {
		Set<Long> teachers = new java.util.LinkedHashSet<>();
		for (TeacherAssignment a : assignments) {
			boolean covers = !a.getStartDate().isAfter(on) && (a.getEndDate() == null || !a.getEndDate().isBefore(on));
			if (covers && a.getSection().getId().equals(sectionId) && a.getSubject().getId().equals(subjectId)) {
				teachers.add(a.getTeacherPerson().getId());
			}
		}
		return teachers;
	}
}
