package com.operion.performance;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.academic.TeacherAssignmentType;
import com.operion.attendance.ClassAttendanceRegister;
import com.operion.attendance.ClassAttendanceRegisterRepository;
import com.operion.attendance.ClassAttendanceRegisterStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether class attendance registers were closed on the day they are for. A register records no submitter,
 * so it is counted against the section's current class teacher.
 */
@Component
public class RegisterKpiProvider implements KpiMetricProvider {

	private final ClassAttendanceRegisterRepository registerRepository;
	private final TeacherAssignmentRepository assignmentRepository;

	public RegisterKpiProvider(ClassAttendanceRegisterRepository registerRepository, TeacherAssignmentRepository assignmentRepository) {
		this.registerRepository = registerRepository;
		this.assignmentRepository = assignmentRepository;
	}

	@Override
	public Set<KpiMetric> supports() {
		return Set.of(KpiMetric.REGISTER_SAME_DAY);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, MetricValue> compute(KpiMetric metric, KpiPeriod period, Integer days) {
		Map<Long, Long> classTeacherBySection = new HashMap<>();
		for (TeacherAssignment a : assignmentRepository.findByStatus(TeacherAssignmentStatus.ACTIVE)) {
			if (a.getAssignmentType() == TeacherAssignmentType.HOMEROOM) {
				classTeacherBySection.put(a.getSection().getId(), a.getTeacherPerson().getId());
			}
		}
		LocalDate today = LocalDate.now();
		Map<Long, int[]> tally = new HashMap<>();
		for (ClassAttendanceRegister register : registerRepository.findByAttendanceDateBetween(period.from(), period.to())) {
			Long teacher = classTeacherBySection.get(register.getSection().getId());
			if (teacher == null) {
				continue;
			}
			boolean submitted = register.getRegisterStatus() != ClassAttendanceRegisterStatus.DRAFT;
			if (submitted && register.getSubmittedAt() == null) {
				continue; // submitted before the timestamp was recorded: unknown, not late
			}
			if (!submitted && !register.getAttendanceDate().isBefore(today)) {
				continue; // today's draft can still be submitted on time
			}
			int[] counts = tally.computeIfAbsent(teacher, k -> new int[2]);
			counts[1]++;
			if (submitted && !register.getSubmittedAt().atZone(ZoneId.systemDefault()).toLocalDate().isAfter(register.getAttendanceDate())) {
				counts[0]++;
			}
		}
		Map<Long, MetricValue> values = new HashMap<>();
		tally.forEach((personId, counts) -> values.put(personId, MetricValue.ratio(counts[0], counts[1])));
		return values;
	}
}
