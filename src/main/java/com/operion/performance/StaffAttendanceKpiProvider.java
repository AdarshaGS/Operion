package com.operion.performance;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.StaffAttendance;
import com.operion.attendance.StaffAttendanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Punctuality and presence from the teacher's own check-in records. */
@Component
public class StaffAttendanceKpiProvider implements KpiMetricProvider {

	private final StaffAttendanceRepository attendanceRepository;

	public StaffAttendanceKpiProvider(StaffAttendanceRepository attendanceRepository) {
		this.attendanceRepository = attendanceRepository;
	}

	@Override
	public Set<KpiMetric> supports() {
		return Set.of(KpiMetric.STAFF_PUNCTUALITY, KpiMetric.STAFF_PRESENCE);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, MetricValue> compute(KpiMetric metric, KpiPeriod period, Integer days) {
		// part/whole per person: [0] = part, [1] = whole
		Map<Long, int[]> tally = new HashMap<>();
		for (StaffAttendance a : attendanceRepository.findByAttendanceDateBetween(period.from(), period.to())) {
			AttendanceStatus status = a.getAttendanceStatus();
			boolean attended = attended(status);
			int[] counts = tally.computeIfAbsent(a.getPerson().getId(), k -> new int[2]);
			if (metric == KpiMetric.STAFF_PUNCTUALITY) {
				if (attended) {
					counts[1]++;
					if (status != AttendanceStatus.LATE) {
						counts[0]++;
					}
				}
			} else if (attended || isAbsent(status)) {
				// approved leave is neither presence nor absence
				counts[1]++;
				if (attended) {
					counts[0]++;
				}
			}
		}
		Map<Long, MetricValue> values = new HashMap<>();
		tally.forEach((personId, counts) -> {
			if (counts[1] > 0) {
				values.put(personId, MetricValue.ratio(counts[0], counts[1]));
			}
		});
		return values;
	}

	/** Counts towards presence. Approved leave is neither presence nor absence. */
	static boolean attended(AttendanceStatus status) {
		return status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE || status == AttendanceStatus.HALF_DAY;
	}

	static boolean isAbsent(AttendanceStatus status) {
		return status == AttendanceStatus.ABSENT;
	}
}
